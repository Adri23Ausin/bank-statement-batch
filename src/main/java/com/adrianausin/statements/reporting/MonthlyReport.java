package com.adrianausin.statements.reporting;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.adrianausin.statements.categorization.Category;

/**
 * Income and spending of one account in one month, broken down by category.
 *
 * @param expenses negative total of the money that went out
 */
public record MonthlyReport(
        String accountId,
        YearMonth month,
        String currency,
        int transactions,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal net,
        List<CategoryTotal> categories) {

    public record CategoryTotal(Category category, int transactions, BigDecimal total) {
    }
}
