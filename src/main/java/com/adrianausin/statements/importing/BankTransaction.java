package com.adrianausin.statements.importing;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.adrianausin.statements.categorization.Category;

public record BankTransaction(
        String accountId,
        LocalDate bookingDate,
        String description,
        BigDecimal amount,
        String currency,
        Category category,
        String fingerprint,
        String sourceFile) {
}
