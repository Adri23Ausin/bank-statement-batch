package com.adrianausin.statements.importing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;

import com.adrianausin.statements.categorization.TransactionCategorizer;
import com.adrianausin.statements.parsing.InvalidStatementLineException;
import com.adrianausin.statements.parsing.StatementLine;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStream;

/**
 * Validates, categorises and fingerprints each line.
 * <p>
 * The fingerprint identifies a movement across imports, so re-importing a statement (or two
 * overlapping statements) doesn't duplicate transactions. Two identical movements on the same
 * day (two coffees at the same price) are told apart by their occurrence number within the file.
 * <p>
 * The occurrence number is derived from line numbers rather than a counter, so it stays the same
 * when a chunk is rolled back and processed again after a skip. The line numbers seen so far are
 * saved in the step's {@link ExecutionContext}, so a restarted import numbers the remaining lines
 * exactly as the first attempt would have.
 */
public class BankTransactionProcessor implements ItemProcessor<ParsedLine, BankTransaction>, ItemStream {

    private static final String OCCURRENCES_KEY = "bankTransactionProcessor.occurrences";

    private final StatementFile file;
    private final TransactionCategorizer categorizer;
    private final Clock clock;
    private HashMap<String, TreeSet<Integer>> linesByIdentity = new HashMap<>();

    public BankTransactionProcessor(StatementFile file, TransactionCategorizer categorizer, Clock clock) {
        this.file = file;
        this.categorizer = categorizer;
        this.clock = clock;
    }

    @Override
    public BankTransaction process(ParsedLine item) {
        StatementLine line = item.line();
        if (line.bookingDate().isAfter(LocalDate.now(clock))) {
            throw new InvalidStatementLineException("Booking date %s is in the future".formatted(line.bookingDate()));
        }
        String identity = String.join("|", file.accountId(), line.bookingDate().toString(),
                line.amount().stripTrailingZeros().toPlainString(), line.currency(), line.description());
        TreeSet<Integer> lines = linesByIdentity.computeIfAbsent(identity, key -> new TreeSet<>());
        lines.add(item.lineNumber());
        int occurrence = lines.headSet(item.lineNumber(), true).size();

        return new BankTransaction(file.accountId(), line.bookingDate(), line.description(), amount(line),
                line.currency(), categorizer.categorize(line.description(), line.amount()),
                sha256(identity + "|" + occurrence), file.name());
    }

    private static BigDecimal amount(StatementLine line) {
        try {
            return line.amount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidStatementLineException("Amounts with more than 2 decimals are not supported", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void open(ExecutionContext executionContext) {
        if (executionContext.containsKey(OCCURRENCES_KEY)) {
            linesByIdentity = new HashMap<>((Map<String, TreeSet<Integer>>) executionContext.get(OCCURRENCES_KEY));
        }
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.put(OCCURRENCES_KEY, new HashMap<>(linesByIdentity));
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
