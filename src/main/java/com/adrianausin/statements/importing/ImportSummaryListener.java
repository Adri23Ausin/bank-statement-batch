package com.adrianausin.statements.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Logs one line per step when an import finishes.
 */
@Component
public class ImportSummaryListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ImportSummaryListener.class);

    @Override
    public void afterJob(JobExecution jobExecution) {
        for (StepExecution step : jobExecution.getStepExecutions()) {
            log.info("Job {} step {}: {} - read={} written={} duplicates={} skipped={}",
                    jobExecution.getId(), step.getStepName(), step.getStatus(), step.getReadCount(),
                    step.getWriteCount(), step.getExecutionContext().getLong(BankTransactionWriter.DUPLICATES_KEY, 0L),
                    step.getSkipCount());
        }
    }
}
