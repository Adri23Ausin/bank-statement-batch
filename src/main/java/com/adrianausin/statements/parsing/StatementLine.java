package com.adrianausin.statements.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One movement of a bank statement, already normalised: signed amount (negative = money out),
 * ISO date and ISO 4217 currency, whatever the original bank format looked like.
 */
public record StatementLine(LocalDate bookingDate, String description, BigDecimal amount, String currency) {
}
