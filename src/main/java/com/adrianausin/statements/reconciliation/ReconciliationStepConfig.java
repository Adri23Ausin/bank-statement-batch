package com.adrianausin.statements.reconciliation;

import com.adrianausin.statements.reconciliation.PaymentReconciler.ReconciliationResult;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class ReconciliationStepConfig {

    @Bean
    Step reconcilePayments(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                           PaymentReconciler reconciler) {
        return new StepBuilder("reconcilePayments", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    ReconciliationResult result = reconciler.reconcile();
                    ExecutionContext context = contribution.getStepExecution().getExecutionContext();
                    context.putInt("matched", result.matched());
                    context.putInt("needReview", result.needReview());
                    context.putInt("overdue", result.overdue());
                    contribution.incrementWriteCount(result.matched());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
