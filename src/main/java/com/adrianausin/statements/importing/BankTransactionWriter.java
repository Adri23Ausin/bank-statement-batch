package com.adrianausin.statements.importing;

import java.sql.Date;
import java.util.Arrays;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Batch-inserts transactions, ignoring the ones already imported (same fingerprint). The
 * number of duplicates is kept in the step execution context for the import summary.
 */
public class BankTransactionWriter implements ItemWriter<BankTransaction>, StepExecutionListener {

    public static final String DUPLICATES_KEY = "duplicates";

    private static final String INSERT = """
            INSERT INTO bank_transactions (account_id, booking_date, description, amount, currency, category,
                                           fingerprint, source_file, job_execution_id)
            VALUES (:accountId, :bookingDate, :description, :amount, :currency, :category,
                    :fingerprint, :sourceFile, :jobExecutionId)
            ON CONFLICT (fingerprint) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private StepExecution stepExecution;

    public BankTransactionWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void write(Chunk<? extends BankTransaction> chunk) {
        long jobExecutionId = stepExecution.getJobExecutionId();
        SqlParameterSource[] batch = chunk.getItems().stream()
                .map(tx -> new MapSqlParameterSource()
                        .addValue("accountId", tx.accountId())
                        .addValue("bookingDate", Date.valueOf(tx.bookingDate()))
                        .addValue("description", tx.description())
                        .addValue("amount", tx.amount())
                        .addValue("currency", tx.currency())
                        .addValue("category", tx.category().name())
                        .addValue("fingerprint", tx.fingerprint())
                        .addValue("sourceFile", tx.sourceFile())
                        .addValue("jobExecutionId", jobExecutionId))
                .toArray(SqlParameterSource[]::new);

        int inserted = Arrays.stream(jdbc.batchUpdate(INSERT, batch)).sum();

        long duplicates = stepExecution.getExecutionContext().getLong(DUPLICATES_KEY, 0L) + chunk.size() - inserted;
        stepExecution.getExecutionContext().putLong(DUPLICATES_KEY, duplicates);
    }
}
