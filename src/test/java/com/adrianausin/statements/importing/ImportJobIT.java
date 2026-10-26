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

    private static final String SPANISH = "ES9121000418450200051332_2026-09.csv";
    private static final String BRITISH = "GB33BUKB20201555555555_2026-09.csv";

    @Autowired
    JobOperator jobOperator;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    Job importStatementsJob;

    @Autowired
    JdbcClient jdbc;

    @TempDir
    Path inbox;

    JobOperatorTestUtils jobs;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE bank_transactions, import_errors").update();
        jobs = new JobOperatorTestUtils(jobOperator, jobRepository);
        jobs.setJob(importStatementsJob);
    }

    @Test
    void importsASpanishStatementSkippingInvalidLines() throws Exception {
        copySample(SPANISH);

        JobExecution execution = importInbox();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution worker = workerStep(execution, SPANISH);
        assertThat(worker.getWriteCount()).isEqualTo(25);
        assertThat(worker.getSkipCount()).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM bank_transactions WHERE account_id = 'ES9121000418450200051332'"))
                .isEqualTo(25);
        assertThat(jdbc.sql("SELECT reason FROM import_errors ORDER BY line_number").query(String.class).list())
                .containsExactly("Invalid amount ''", "Invalid date '31/09/2026'");
    }

    @Test
    void importsEveryFileOfTheDirectoryInItsOwnPartition() throws Exception {
        copySample(SPANISH);
        copySample(BRITISH);

        JobExecution execution = importInbox();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactlyInAnyOrder("importStatements", "importStatementFile:file0", "importStatementFile:file1");
        assertThat(count("SELECT count(DISTINCT account_id) FROM bank_transactions")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM bank_transactions")).isEqualTo(25 + 22);
    }

    @Test
    void keepsIdenticalMovementsOnTheSameDay() throws Exception {
        copySample(SPANISH);

        importInbox();

        assertThat(count("SELECT count(*) FROM bank_transactions WHERE description = 'CAFETERIA EL RINCON'"))
                .isEqualTo(2);
    }

    @Test
    void categorisesWhileImporting() throws Exception {
        copySample(BRITISH);

        importInbox();

        assertThat(jdbc.sql("SELECT category FROM bank_transactions WHERE description = 'SALARY ACME LTD'")
                .query(String.class).single()).isEqualTo("INCOME");
        assertThat(jdbc.sql("SELECT amount FROM bank_transactions WHERE description = 'TESCO STORES 2041' "
                + "ORDER BY booking_date LIMIT 1").query(String.class).single()).isEqualTo("-42.10");
    }

    @Test
    void reimportingTheSameStatementAddsNothing() throws Exception {
        copySample(BRITISH);
        importInbox();

        JobExecution second = importInbox();

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(workerStep(second, BRITISH).getExecutionContext().getLong(BankTransactionWriter.DUPLICATES_KEY))
                .isEqualTo(22);
        assertThat(count("SELECT count(*) FROM bank_transactions")).isEqualTo(22);
    }

    @Test
    void anUnknownFormatFailsOnlyThatFile() throws Exception {
        copySample(BRITISH);
        Files.writeString(inbox.resolve("ES9121000418450200051332_unknown.csv"), "id,amount\n1,10.00\n");

        JobExecution execution = importInbox();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(count("SELECT count(*) FROM bank_transactions")).isEqualTo(22);
    }

    private JobExecution importInbox() throws Exception {
        JobParameters parameters = jobs.getUniqueJobParametersBuilder()
                .addString(ImportJobConfig.INPUT_DIRECTORY, inbox.toString())
                .toJobParameters();
        return jobs.startJob(parameters);
    }

    private static StepExecution workerStep(JobExecution execution, String fileName) {
        return execution.getStepExecutions().stream()
                .filter(step -> step.getExecutionContext().getString(StatementFilesPartitioner.INPUT_FILE, "")
                        .endsWith(fileName))
                .findFirst()
                .orElseThrow();
    }

    private void copySample(String name) throws IOException {
        Files.copy(Path.of("samples", name), inbox.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
