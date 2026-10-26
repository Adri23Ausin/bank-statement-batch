package com.adrianausin.statements.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * A bank's CSV export format. Each implementation recognises its own header and normalises
 * its lines into {@link StatementLine}s.
 * <p>
 * Sealed: supporting a new bank means adding a permitted implementation here, and the
 * compiler points at every place that has to handle it.
 */
public sealed interface StatementFormat permits StatementFormat.IberBank, StatementFormat.NorthBank {

    String name();

    boolean supports(String headerLine);

    StatementLine parse(String line);

    /**
     * Spanish bank export: {@code Fecha;Concepto;Importe;Divisa;Saldo}, dates as
     * {@code dd/MM/yyyy} and amounts with a decimal comma and dot thousands separator
     * ({@code -1.234,56}).
     */
    record IberBank() implements StatementFormat {

        private static final String HEADER = "Fecha;Concepto;Importe;Divisa;Saldo";
        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

        @Override
        public String name() {
            return "IBERBANK";
        }

        @Override
        public boolean supports(String headerLine) {
            return headerLine.strip().equalsIgnoreCase(HEADER);
        }

        @Override
        public StatementLine parse(String line) {
            List<String> fields = Fields.require(CsvFields.split(line, ';'), 5);
            LocalDate date = Fields.date(fields.get(0), DATE);
            String description = Fields.description(fields.get(1));
            BigDecimal amount = Fields.decimal(fields.get(2).replace(".", "").replace(',', '.'));
            if (amount.signum() == 0) {
                throw new InvalidStatementLineException("Amount must not be zero");
            }
            return new StatementLine(date, description, amount, Fields.currency(fields.get(3)));
        }
    }

    /**
     * UK-style export: {@code Date,Description,Debit,Credit,Currency}, ISO dates and separate
     * unsigned debit and credit columns, exactly one of which is filled in.
     */
    record NorthBank() implements StatementFormat {

        private static final String HEADER = "Date,Description,Debit,Credit,Currency";

        @Override
        public String name() {
            return "NORTHBANK";
        }

        @Override
        public boolean supports(String headerLine) {
            return headerLine.strip().equalsIgnoreCase(HEADER);
        }

        @Override
        public StatementLine parse(String line) {
            List<String> fields = Fields.require(CsvFields.split(line, ','), 5);
            LocalDate date = Fields.date(fields.get(0), DateTimeFormatter.ISO_LOCAL_DATE);
            String description = Fields.description(fields.get(1));
            String debit = fields.get(2);
            String credit = fields.get(3);
            if (debit.isEmpty() == credit.isEmpty()) {
                throw new InvalidStatementLineException("Exactly one of debit and credit must be present");
            }
            BigDecimal amount = debit.isEmpty() ? Fields.decimal(credit) : Fields.decimal(debit).negate();
            if (amount.signum() == 0) {
                throw new InvalidStatementLineException("Amount must not be zero");
            }
            return new StatementLine(date, description, amount, Fields.currency(fields.get(4)));
        }
    }

    /**
     * Field conversions shared by the formats.
     */
    final class Fields {

        private Fields() {
        }

        static List<String> require(List<String> fields, int count) {
            if (fields.size() != count) {
                throw new InvalidStatementLineException("Expected %d fields, got %d".formatted(count, fields.size()));
            }
            return fields;
        }

        static LocalDate date(String value, DateTimeFormatter format) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException e) {
                throw new InvalidStatementLineException("Invalid date '%s'".formatted(value), e);
            }
        }

        static BigDecimal decimal(String value) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                throw new InvalidStatementLineException("Invalid amount '%s'".formatted(value), e);
            }
        }

        static String description(String value) {
            if (value.isBlank()) {
                throw new InvalidStatementLineException("Description is empty");
            }
            return value.replaceAll("\\s+", " ");
        }

        static String currency(String value) {
            String code = value.toUpperCase(Locale.ROOT);
            try {
                return Currency.getInstance(code).getCurrencyCode();
            } catch (IllegalArgumentException e) {
                throw new InvalidStatementLineException("Unknown currency '%s'".formatted(value), e);
            }
        }
    }
}
