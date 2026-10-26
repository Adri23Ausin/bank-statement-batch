package com.adrianausin.statements.importing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * Turns a set of statement files into an import: stores them in their own inbox
 * sub-directory and launches {@code importStatementsJob} on it.
 * <p>
 * The import id is both the directory name and a job parameter, so the job execution of an
 * import can always be found again from its id.
 */
@Service
public class ImportLauncher implements DisposableBean {

    public static final String IMPORT_ID = "importId";

    private static final Logger log = LoggerFactory.getLogger(ImportLauncher.class);
    private static final DateTimeFormatter ID_PREFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JobOperator jobOperator;
    private final Job importStatementsJob;
    private final InboxProperties inbox;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ImportLauncher(JobOperator jobOperator, Job importStatementsJob, InboxProperties inbox, Clock clock) {
        this.jobOperator = jobOperator;
        this.importStatementsJob = importStatementsJob;
        this.inbox = inbox;
        this.clock = clock;
    }

    /**
     * Stores the files and starts the import in the background.
     *
     * @param files file name to content; names must follow the {@code <IBAN>_<anything>.csv} convention
     * @return the import id
     * @throws IllegalArgumentException if a file has an invalid name or an unknown format
     */
    public String submit(Map<String, InputStream> files) {
        String importId = newImportId();
        Path directory = importDirectory(importId);
        try {
            Files.createDirectories(directory);
            for (Map.Entry<String, InputStream> file : files.entrySet()) {
                Path target = directory.resolve(safeFileName(file.getKey()));
                Files.copy(file.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
                // Fail fast on unknown formats, before anything is launched
                StatementFile.open(target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store uploaded statements", e);
        }
        executor.submit(() -> run(importId));
        return importId;
    }

    /**
     * Moves the CSV files dropped in the inbox root into a new import and runs it.
     *
     * @return the import id, or {@code null} if there was nothing to import
     */
    public String importDroppedFiles() throws IOException {
        List<Path> dropped;
        try (var entries = Files.list(inbox.directory())) {
            dropped = entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .toList();
        }
        if (dropped.isEmpty()) {
            return null;
        }
        String importId = newImportId();
        Path directory = Files.createDirectories(importDirectory(importId));
        for (Path file : dropped) {
            Files.move(file, directory.resolve(file.getFileName()), StandardCopyOption.ATOMIC_MOVE);
        }
        run(importId);
        return importId;
    }

    public JobParameters parameters(String importId) {
        return new JobParametersBuilder()
                .addString(IMPORT_ID, importId)
                .addString(ImportJobConfig.INPUT_DIRECTORY, importDirectory(importId).toAbsolutePath().toString())
                .toJobParameters();
    }

    public Path importDirectory(String importId) {
        return inbox.directory().resolve(importId);
    }

    private void run(String importId) {
        try {
            JobExecution execution = jobOperator.start(importStatementsJob, parameters(importId));
            log.info("Import {} finished with status {}", importId, execution.getStatus());
        } catch (Exception e) {
            log.error("Import {} could not be launched", importId, e);
        }
    }

    private String newImportId() {
        return ID_PREFIX.format(clock.instant().atZone(clock.getZone())) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String safeFileName(String name) {
        // Never trust client paths: keep only the file name
        String fileName = Path.of(name).getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Only .csv files are accepted, got " + fileName);
        }
        return fileName;
    }

    @Override
    public void destroy() {
        executor.close();
    }
}
