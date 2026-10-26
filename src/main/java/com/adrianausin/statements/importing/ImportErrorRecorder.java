package com.adrianausin.statements.importing;

import com.adrianausin.statements.parsing.InvalidStatementLineException;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stores every skipped line with the reason, so a user can fix and re-import them.
 */
public class ImportErrorRecorder implements SkipListener<ParsedLine, BankTransaction>, StepExecutionListener {

    private final JdbcClient jdbc;
    private final String sourceFile;
    private long jobExecutionId;

    public ImportErrorRecorder(JdbcClient jdbc, String sourceFile) {
        this.jdbc = jdbc;
        this.sourceFile = sourceFile;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.jobExecutionId = stepExecution.getJobExecutionId();
    }

    @Override
    public void onSkipInRead(Throwable error) {
        if (error instanceof FlatFileParseException parseError) {
            record(parseError.getLineNumber(), parseError.getInput(), rootMessage(parseError));
        } else {
            record(null, null, rootMessage(error));
        }
    }

    @Override
    public void onSkipInProcess(ParsedLine item, Throwable error) {
        record(item.lineNumber(), item.raw(), rootMessage(error));
    }

    private void record(Integer lineNumber, String line, String reason) {
        jdbc.sql("""
                        INSERT INTO import_errors (job_execution_id, source_file, line_number, line, reason)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .params(jobExecutionId, sourceFile, lineNumber, line, abbreviate(reason))
                .update();
    }

    /**
     * Prefers our own validation message ("Invalid date '31/09/2026'") over low-level causes
     * such as a {@link NumberFormatException}.
     */
    private static String rootMessage(Throwable error) {
        Throwable root = error;
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InvalidStatementLineException) {
                return current.getMessage();
            }
            root = current;
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private static String abbreviate(String reason) {
        return reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
    }
}
