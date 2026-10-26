package com.adrianausin.statements.importing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.adrianausin.statements.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ImportJobIT {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    Job importStatementsJob;

    @Autowired
    JdbcClient jdbc;

    JobOperatorTestUtils jobs;

    @TempDir
    Path inbox;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE bank_transactions, import_errors").update();
        jobs = new JobOperatorTestUtils(jobOperator, jobRepository);
        jobs.setJob(importStatementsJob);
    }

    @Test
    void importsASpanishStatementSkippingInvalidLines() throws Exception {
        JobExecution execution = importFile(sample("ES9121000418450200051332_2026-09.csv"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution step = execution.getStepExecutions().iterator().next();
        assertThat(step.getWriteCount()).isEqualTo(25);
        assertThat(step.getSkipCount()).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM bank_transactions WHERE account_id = 'ES9121000418450200051332'"))
                .isEqualTo(25);
        assertThat(jdbc.sql("SELECT reason FROM import_errors ORDER BY line_number").query(String.class).list())
                .containsExactly("Invalid amount ''", "Invalid date '31/09/2026'");
    }

    @Test
    void keepsIdenticalMovementsOnTheSameDay() throws Exception {
        importFile(sample("ES9121000418450200051332_2026-09.csv"));

        assertThat(count("SELECT count(*) FROM bank_transactions WHERE description = 'CAFETERIA EL RINCON'"))
                .isEqualTo(2);
    }

    @Test
    void categorisesWhileImporting() throws Exception {
        importFile(sample("GB33BUKB20201555555555_2026-09.csv"));

        assertThat(jdbc.sql("SELECT category FROM bank_transactions WHERE description = 'SALARY ACME LTD'")
                .query(String.class).single()).isEqualTo("INCOME");
        assertThat(jdbc.sql("SELECT amount FROM bank_transactions WHERE description = 'TESCO STORES 2041' "
                + "ORDER BY booking_date LIMIT 1").query(String.class).single()).isEqualTo("-42.10");
    }

    @Test
    void reimportingTheSameStatementAddsNothing() throws Exception {
        Path file = sample("GB33BUKB20201555555555_2026-09.csv");
        importFile(file);

        JobExecution second = importFile(file);

        StepExecution step = second.getStepExecutions().iterator().next();
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step.getExecutionContext().getLong(BankTransactionWriter.DUPLICATES_KEY)).isEqualTo(22);
        assertThat(count("SELECT count(*) FROM bank_transactions")).isEqualTo(22);
    }

    @Test
    void failsOnUnknownFormats() throws Exception {
        Path file = inbox.resolve("ES9121000418450200051332_unknown.csv");
        Files.writeString(file, "id,amount\n1,10.00\n");

        assertThat(importFile(file).getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    private JobExecution importFile(Path file) throws Exception {
        JobParameters parameters = jobs.getUniqueJobParametersBuilder()
                .addString(ImportJobConfig.INPUT_FILE, file.toString())
                .toJobParameters();
        return jobs.startJob(parameters);
    }

    private Path sample(String name) throws IOException {
        return Files.copy(Path.of("samples", name), inbox.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
