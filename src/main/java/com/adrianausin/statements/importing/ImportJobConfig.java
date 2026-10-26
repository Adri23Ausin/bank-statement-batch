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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code importStatementsJob}: reads a bank statement, categorises every line and stores it,
 * skipping (and recording) the lines that can't be parsed.
 */
@Configuration(proxyBeanMethods = false)
public class ImportJobConfig {

    public static final String JOB_NAME = "importStatementsJob";
    public static final String INPUT_FILE = "inputFile";

    static final int CHUNK_SIZE = 500;
    static final int SKIP_LIMIT = 100;

    @Bean
    Job importStatementsJob(JobRepository jobRepository, Step importStatementFile, ImportSummaryListener summary) {
        return new JobBuilder(JOB_NAME, jobRepository)
                // No incrementer: in Spring Batch 6 it would replace the parameters given at launch.
                // Callers add a unique parameter (e.g. the launch time) to start a new instance.
                .validator(new DefaultJobParametersValidator(new String[] {INPUT_FILE}, new String[0]))
                .listener(summary)
                .start(importStatementFile)
                .build();
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
    FlatFileItemReader<ParsedLine> statementReader(@Value("#{jobParameters['inputFile']}") String inputFile) {
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
    BankTransactionProcessor bankTransactionProcessor(@Value("#{jobParameters['inputFile']}") String inputFile,
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
    ImportErrorRecorder importErrorRecorder(@Value("#{jobParameters['inputFile']}") String inputFile,
                                            JdbcClient jdbc) {
        return new ImportErrorRecorder(jdbc, Path.of(inputFile).getFileName().toString());
    }
}
