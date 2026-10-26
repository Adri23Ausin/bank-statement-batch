package com.adrianausin.statements.reconciliation;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.adrianausin.statements.TestcontainersConfiguration;
import com.adrianausin.statements.importing.ImportJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
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
class ReconciliationIT {

    private static final String ACCOUNT = "ES9121000418450200051332";

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
    void setUp() throws Exception {
        jdbc.sql("TRUNCATE expected_payments, bank_transactions, import_errors").update();
        jobs = new JobOperatorTestUtils(jobOperator, jobRepository);
        jobs.setJob(importStatementsJob);
        Files.copy(Path.of("samples", ACCOUNT + "_2026-09.csv"), inbox.resolve(ACCOUNT + "_2026-09.csv"));
    }

    @Test
    void matchesPaymentsByReference() throws Exception {
        expect("ALQ-2026-09", "-950.00", "2026-09-01");
        expect("IBD-778812", "-78.45", "2026-09-05");

        JobExecution execution = runImport();

        assertThat(statusOf("ALQ-2026-09")).isEqualTo("MATCHED");
        assertThat(matchedDescription("IBD-778812")).isEqualTo("RECIBO IBERDROLA CLIENTES REF IBD-778812");
        assertThat(reconcileStep(execution).getExecutionContext().getInt("matched")).isEqualTo(2);
    }

    @Test
    void matchesAUniqueAmountWithinTheDateWindowWithoutReference() throws Exception {
        expect("GYM-SEPTEMBER", "-29.99", "2026-09-17");

        runImport();

        assertThat(matchedDescription("GYM-SEPTEMBER")).isEqualTo("GIMNASIO BASIC FIT");
    }

    @Test
    void asksForReviewWhenSeveralTransactionsCouldMatch() throws Exception {
        expect("COFFEE", "-3.20", "2026-09-09");

        runImport();

        assertThat(statusOf("COFFEE")).isEqualTo("REVIEW");
    }

    @Test
    void flagsPaymentsThatShouldHaveAppearedAsOverdue() throws Exception {
        expect("WATER-BILL", "-31.50", "2026-09-10");

        runImport();

        assertThat(statusOf("WATER-BILL")).isEqualTo("OVERDUE");
    }

    @Test
    void keepsPaymentsPendingWhenStatementsDontCoverTheirDueDateYet() throws Exception {
        expect("OCTOBER-RENT", "-950.00", "2026-10-01");

        runImport();

        assertThat(statusOf("OCTOBER-RENT")).isEqualTo("PENDING");
    }

    @Test
    void aTransactionSettlesOnlyOnePayment() throws Exception {
        expect("NETFLIX-1", "-13.99", "2026-09-01");
        expect("NETFLIX-2", "-13.99", "2026-09-02");

        runImport();

        assertThat(statusOf("NETFLIX-1")).isEqualTo("MATCHED");
        assertThat(statusOf("NETFLIX-2")).isEqualTo("OVERDUE");
    }

    private JobExecution runImport() throws Exception {
        JobExecution execution = jobs.startJob(jobs.getUniqueJobParametersBuilder()
                .addString(ImportJobConfig.INPUT_DIRECTORY, inbox.toString())
                .toJobParameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        return execution;
    }

    private void expect(String reference, String amount, String dueDate) {
        jdbc.sql("INSERT INTO expected_payments (account_id, reference, amount, due_date) VALUES (?, ?, ?, ?)")
                .params(ACCOUNT, reference, new BigDecimal(amount), LocalDate.parse(dueDate))
                .update();
    }

    private String statusOf(String reference) {
        return jdbc.sql("SELECT status FROM expected_payments WHERE reference = ?")
                .param(reference).query(String.class).single();
    }

    private String matchedDescription(String reference) {
        return jdbc.sql("""
                        SELECT t.description FROM expected_payments p
                        JOIN bank_transactions t ON t.id = p.matched_transaction_id
                        WHERE p.reference = ?
                        """)
                .param(reference).query(String.class).single();
    }

    private static StepExecution reconcileStep(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(step -> step.getStepName().equals("reconcilePayments"))
                .findFirst()
                .orElseThrow();
    }
}
