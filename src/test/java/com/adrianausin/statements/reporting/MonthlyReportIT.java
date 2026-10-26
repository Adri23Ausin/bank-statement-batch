package com.adrianausin.statements.reporting;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

import com.adrianausin.statements.TestcontainersConfiguration;
import com.adrianausin.statements.categorization.Category;
import com.adrianausin.statements.importing.ImportJobConfig;
import com.adrianausin.statements.reporting.MonthlyReport.CategoryTotal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MonthlyReportIT {

    private static final String ACCOUNT = "ES9121000418450200051332";

    @Autowired
    JobOperator jobOperator;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    Job importStatementsJob;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MonthlyReportService reports;

    @Autowired
    ReportProperties reportProperties;

    @Autowired
    JsonMapper jsonMapper;

    @TempDir
    Path inbox;

    @BeforeEach
    void importSpanishStatement() throws Exception {
        jdbc.sql("TRUNCATE expected_payments, bank_transactions, import_errors").update();
        FileSystemUtils.deleteRecursively(reportProperties.directory());
        Files.copy(Path.of("samples", ACCOUNT + "_2026-09.csv"), inbox.resolve(ACCOUNT + "_2026-09.csv"));

        JobOperatorTestUtils jobs = new JobOperatorTestUtils(jobOperator, jobRepository);
        jobs.setJob(importStatementsJob);
        assertThat(jobs.startJob(jobs.getUniqueJobParametersBuilder()
                .addString(ImportJobConfig.INPUT_DIRECTORY, inbox.toString())
                .toJobParameters()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void summarisesIncomeAndSpendingByCategory() {
        MonthlyReport report = reports.report(ACCOUNT, YearMonth.of(2026, 9)).orElseThrow();

        assertThat(report.transactions()).isEqualTo(25);
        assertThat(report.currency()).isEqualTo("EUR");
        assertThat(report.income()).isEqualByComparingTo("2401.49");
        assertThat(report.net()).isEqualByComparingTo(report.income().add(report.expenses()));
        assertThat(report.categories())
                .filteredOn(total -> total.category() == Category.GROCERIES)
                .singleElement()
                .satisfies(groceries -> {
                    assertThat(groceries.transactions()).isEqualTo(4);
                    assertThat(groceries.total()).isEqualByComparingTo("-191.36");
                });
        assertThat(report.categories().stream().map(CategoryTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(report.net());
    }

    @Test
    void writesJsonAndCsvFilesForTheImportedMonths() throws Exception {
        Path json = reportProperties.directory().resolve(ACCOUNT).resolve("2026-09.json");
        Path csv = reportProperties.directory().resolve(ACCOUNT).resolve("2026-09.csv");

        assertThat(json).exists();
        JsonNode report = jsonMapper.readTree(Files.readString(json));
        assertThat(report.get("month").asString()).isEqualTo("2026-09");
        assertThat(report.get("income").decimalValue()).isEqualByComparingTo("2401.49");

        assertThat(Files.readAllLines(csv))
                .first().isEqualTo("category,transactions,total,currency");
        assertThat(Files.readAllLines(csv)).contains("GROCERIES,4,-191.36,EUR");
    }

    @Test
    void hasNoReportForMonthsWithoutData() {
        assertThat(reports.report(ACCOUNT, YearMonth.of(2026, 8))).isEmpty();
    }
}
