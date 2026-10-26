package com.adrianausin.statements.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expected-payments")
@Tag(name = "Expected payments", description = "Payments to reconcile against imported statements")
class ExpectedPaymentController {

    private final JdbcClient jdbc;

    ExpectedPaymentController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    @Operation(summary = "Register a payment that should appear in a statement (negative amount = money out)")
    ResponseEntity<ExpectedPaymentView> create(@Valid @RequestBody NewExpectedPayment request) {
        ExpectedPaymentView created = jdbc.sql("""
                        INSERT INTO expected_payments (account_id, reference, description, amount, due_date)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id, account_id, reference, description, amount, due_date, status,
                                  matched_transaction_id
                        """)
                .params(request.accountId(), request.reference(), request.description(), request.amount(),
                        request.dueDate())
                .query(ExpectedPaymentView.class)
                .single();
        return ResponseEntity.created(URI.create("/api/v1/expected-payments?accountId=" + created.accountId()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "Expected payments of an account and their reconciliation status")
    List<ExpectedPaymentView> list(@RequestParam String accountId, @RequestParam(required = false) @Nullable String status) {
        return jdbc.sql("""
                        SELECT id, account_id, reference, description, amount, due_date, status, matched_transaction_id
                        FROM expected_payments
                        WHERE account_id = :account AND (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                        ORDER BY due_date, id
                        """)
                .param("account", accountId)
                .param("status", status)
                .query(ExpectedPaymentView.class)
                .list();
    }

    record NewExpectedPayment(
            @NotBlank @Pattern(regexp = "[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}") String accountId,
            @NotBlank @Size(max = 100) String reference,
            @Size(max = 255) @Nullable String description,
            @NotNull @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull LocalDate dueDate) {
    }

    record ExpectedPaymentView(long id, String accountId, String reference, @Nullable String description,
                               BigDecimal amount, LocalDate dueDate, String status,
                               @Nullable Long matchedTransactionId) {
    }
}
