package com.adrianausin.statements.importing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reads the progress of an import from the Spring Batch job repository.
 */
@Service
public class ImportStatusService {

    private final JobRepository jobRepository;
    private final ImportLauncher launcher;
    private final JdbcClient jdbc;

    public ImportStatusService(JobRepository jobRepository, ImportLauncher launcher, JdbcClient jdbc) {
        this.jobRepository = jobRepository;
        this.launcher = launcher;
        this.jdbc = jdbc;
    }

    public Optional<ImportStatus> status(String importId) {
        if (!importId.matches("[0-9A-Za-z-]+") || !Files.isDirectory(launcher.importDirectory(importId))) {
            return Optional.empty();
        }
        JobInstance instance = jobRepository.getJobInstance(ImportJobConfig.JOB_NAME, launcher.parameters(importId));
        JobExecution execution = instance == null ? null : jobRepository.getLastJobExecution(instance);
        if (execution == null) {
            return Optional.of(new ImportStatus(importId, null, "QUEUED", null, null, List.of(), List.of()));
        }

        List<FileStatus> files = execution.getStepExecutions().stream()
                .filter(step -> step.getExecutionContext().containsKey(StatementFilesPartitioner.INPUT_FILE))
                .map(ImportStatusService::fileStatus)
                .sorted(Comparator.comparing(FileStatus::file))
                .toList();
        List<ImportError> errors = jdbc.sql("""
                        SELECT source_file, line_number, line, reason FROM import_errors
                        WHERE job_execution_id = ? ORDER BY source_file, line_number
                        """)
                .param(execution.getId())
                .query(ImportError.class)
                .list();

        return Optional.of(new ImportStatus(importId, execution.getId(), execution.getStatus().name(),
                execution.getStartTime(), execution.getEndTime(), files, errors));
    }

    private static FileStatus fileStatus(StepExecution step) {
        String path = step.getExecutionContext().getString(StatementFilesPartitioner.INPUT_FILE);
        return new FileStatus(Path.of(path).getFileName().toString(), step.getStatus().name(),
                step.getReadCount(), step.getWriteCount(),
                step.getExecutionContext().getLong(BankTransactionWriter.DUPLICATES_KEY, 0L), step.getSkipCount(),
                step.getExitStatus().getExitDescription());
    }

    public record ImportStatus(
            String importId,
            @Nullable Long jobExecutionId,
            String status,
            @Nullable LocalDateTime startTime,
            @Nullable LocalDateTime endTime,
            List<FileStatus> files,
            List<ImportError> errors) {
    }

    public record FileStatus(String file, String status, long read, long written, long duplicates, long skipped,
                             @Nullable String failure) {
    }

    public record ImportError(String sourceFile, @Nullable Integer lineNumber, @Nullable String line, String reason) {
    }
}
