package com.adrianausin.statements.reporting;

import com.adrianausin.statements.reporting.MonthlyReportService.AccountMonth;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class ReportStepConfig {

    /**
     * Regenerates the reports of every account and month that the current import touched.
     */
    @Bean
    Step writeMonthlyReports(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             MonthlyReportService reports, ReportFileWriter writer) {
        return new StepBuilder("writeMonthlyReports", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    long jobExecutionId = contribution.getStepExecution().getJobExecutionId();
                    for (AccountMonth accountMonth : reports.monthsImportedBy(jobExecutionId)) {
                        reports.report(accountMonth.accountId(), accountMonth.month()).ifPresent(report -> {
                            writer.write(report);
                            contribution.incrementWriteCount(1);
                        });
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
