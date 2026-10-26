package com.adrianausin.statements.importing;

import java.nio.file.Path;
import java.time.Clock;

import com.adrianausin.statements.categorization.TransactionCategorizer;
import com.adrianausin.statements.parsing.InvalidStatementLineException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code importStatementsJob}:
 * <ol>
 *     <li>{@code importStatements}: imports every statement file of a directory. Each file is a
 *     partition processed in parallel by the {@code importStatementFile} worker step, which reads,
 *     categorises and stores its lines, skipping (and recording) the ones that can't be parsed;</li>
 *     <li>{@code reconcilePayments}: matches expected payments with the imported transactions;</li>
 *     <li>{@code writeMonthlyReports}: rewrites the reports of the months that received new data.</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class ImportJobConfig {

    public static final String JOB_NAME = "importStatementsJob";
    public static final String INPUT_DIRECTORY = "inputDirectory";

    static final int CHUNK_SIZE = 500;
    static final int SKIP_LIMIT = 100;
    static final int MAX_PARALLEL_FILES = 4;

    @Bean
    Job importStatementsJob(JobRepository jobRepository, Step importStatements, Step reconcilePayments,
                            Step writeMonthlyReports, ImportSummaryListener summary) {
        return new JobBuilder(JOB_NAME, jobRepository)
                // No incrementer: in Spring Batch 6 it would replace the parameters given at launch.
                // Callers add a unique parameter (e.g. the launch time) to start a new instance.
                .validator(new DefaultJobParametersValidator(new String[] {INPUT_DIRECTORY}, new String[0]))
                .listener(summary)
                .start(importStatements)
                .next(reconcilePayments)
                .next(writeMonthlyReports)
                .build();
    }

    @Bean
    Step importStatements(JobRepository jobRepository, Step importStatementFile,
                          StatementFilesPartitioner statementFilesPartitioner) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("import-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(MAX_PARALLEL_FILES);
        return new StepBuilder("importStatements", jobRepository)
                .partitioner("importStatementFile", statementFilesPartitioner)
                .step(importStatementFile)
                .taskExecutor(executor)
                .build();
    }

    @Bean
    @StepScope
    StatementFilesPartitioner statementFilesPartitioner(
            @Value("#{jobParameters['inputDirectory']}") String inputDirectory) {
        return new StatementFilesPartitioner(Path.of(inputDirectory));
    }

    @Bean
    Step importStatementFile(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             FlatFileItemReader<ParsedLine> statementReader,
                             BankTransactionProcessor bankTransactionProcessor,
                             BankTransactionWriter bankTransactionWriter,
                             ImportErrorRecorder importErrorRecorder) {
        return new StepBuilder("importStatementFile", jobRepository)
                .<ParsedLine, BankTransaction>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(statementReader)
                .processor(bankTransactionProcessor)
                .writer(bankTransactionWriter)
                .stream(bankTransactionProcessor)
                .listener(bankTransactionWriter)
                .listener(importErrorRecorder)
                .faultTolerant()
                .skip(FlatFileParseException.class, InvalidStatementLineException.class)
                // More bad lines than this means the file is wrong, not a few lines
                .skipLimit(SKIP_LIMIT)
                .skipListener(importErrorRecorder)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemReader<ParsedLine> statementReader(@Value("#{stepExecutionContext['inputFile']}") String inputFile) {
        StatementFile file = StatementFile.open(Path.of(inputFile));
        return new FlatFileItemReaderBuilder<ParsedLine>()
                .name("statementReader")
                .resource(new FileSystemResource(file.path()))
                .encoding("UTF-8")
                .linesToSkip(1)
                .lineMapper((line, lineNumber) -> new ParsedLine(lineNumber, line, file.format().parse(line)))
                .build();
    }

    @Bean
    @StepScope
    BankTransactionProcessor bankTransactionProcessor(@Value("#{stepExecutionContext['inputFile']}") String inputFile,
                                                      TransactionCategorizer categorizer, Clock clock) {
        return new BankTransactionProcessor(StatementFile.open(Path.of(inputFile)), categorizer, clock);
    }

    @Bean
    @StepScope
    BankTransactionWriter bankTransactionWriter(NamedParameterJdbcTemplate jdbc) {
        return new BankTransactionWriter(jdbc);
    }

    @Bean
    @StepScope
    ImportErrorRecorder importErrorRecorder(@Value("#{stepExecutionContext['inputFile']}") String inputFile,
                                            JdbcClient jdbc) {
        return new ImportErrorRecorder(jdbc, Path.of(inputFile).getFileName().toString());
    }
}
