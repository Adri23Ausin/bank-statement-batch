package com.adrianausin.statements.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.adrianausin.statements.categorization.Category;
import com.adrianausin.statements.reporting.MonthlyReport.CategoryTotal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Builds monthly reports straight from SQL aggregates, so the numbers always reflect what is
 * stored, however many imports contributed to the month.
 */
@Service
public class MonthlyReportService {

    private final JdbcClient jdbc;

    public MonthlyReportService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MonthlyReport> report(String accountId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<CategoryTotal> categories = jdbc.sql("""
                        SELECT category, count(*) AS transactions, sum(amount) AS total
                        FROM bank_transactions
                        WHERE account_id = ? AND booking_date BETWEEN ? AND ?
                        GROUP BY category
                        ORDER BY sum(amount)
                        """)
                .params(accountId, from, to)
                .query((rs, row) -> new CategoryTotal(Category.valueOf(rs.getString("category")),
                        rs.getInt("transactions"), rs.getBigDecimal("total")))
                .list();
        if (categories.isEmpty()) {
            return Optional.empty();
        }

        Totals totals = jdbc.sql("""
                        SELECT min(currency) AS currency,
                               count(*) AS transactions,
                               coalesce(sum(amount) FILTER (WHERE amount > 0), 0) AS income,
                               coalesce(sum(amount) FILTER (WHERE amount < 0), 0) AS expenses
                        FROM bank_transactions
                        WHERE account_id = ? AND booking_date BETWEEN ? AND ?
                        """)
                .params(accountId, from, to)
                .query(Totals.class)
                .single();

        return Optional.of(new MonthlyReport(accountId, month, totals.currency(), totals.transactions(),
                totals.income(), totals.expenses(), totals.income().add(totals.expenses()), categories));
    }

    /**
     * Account/month pairs that received transactions in the given job execution.
     */
    public List<AccountMonth> monthsImportedBy(long jobExecutionId) {
        return jdbc.sql("""
                        SELECT DISTINCT account_id, date_trunc('month', booking_date)::date AS first_day
                        FROM bank_transactions
                        WHERE job_execution_id = ?
                        ORDER BY account_id, first_day
                        """)
                .param(jobExecutionId)
                .query((rs, row) -> new AccountMonth(rs.getString("account_id"),
                        YearMonth.from(rs.getDate("first_day").toLocalDate())))
                .list();
    }

    public record AccountMonth(String accountId, YearMonth month) {
    }

    private record Totals(String currency, int transactions, BigDecimal income, BigDecimal expenses) {
    }
}
