package com.adrianausin.statements.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.adrianausin.statements.categorization.Category;
import com.adrianausin.statements.reporting.MonthlyReport;
import com.adrianausin.statements.reporting.MonthlyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
@Tag(name = "Accounts", description = "Imported transactions and monthly reports")
class AccountController {

    private final MonthlyReportService reports;
    private final JdbcClient jdbc;

    AccountController(MonthlyReportService reports, JdbcClient jdbc) {
        this.reports = reports;
        this.jdbc = jdbc;
    }

    @GetMapping("/reports/{month}")
    @Operation(summary = "Income and spending by category for a month (yyyy-MM)")
    ResponseEntity<MonthlyReport> report(@PathVariable String accountId, @PathVariable YearMonth month) {
        return ResponseEntity.of(reports.report(accountId, month));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Transactions of a month, optionally of one category")
    List<TransactionView> transactions(@PathVariable String accountId,
                                       @RequestParam YearMonth month,
                                       @RequestParam(required = false) @Nullable Category category) {
        return jdbc.sql("""
                        SELECT id, booking_date, description, amount, currency, category
                        FROM bank_transactions
                        WHERE account_id = :account AND booking_date BETWEEN :from AND :to
                          AND (CAST(:category AS VARCHAR) IS NULL OR category = :category)
                        ORDER BY booking_date, id
                        """)
                .param("account", accountId)
                .param("from", month.atDay(1))
                .param("to", month.atEndOfMonth())
                .param("category", category == null ? null : category.name())
                .query(TransactionView.class)
                .list();
    }

    record TransactionView(long id, LocalDate bookingDate, String description, BigDecimal amount, String currency,
                           Category category) {
    }
}
