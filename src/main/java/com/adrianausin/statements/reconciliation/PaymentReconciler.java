package com.adrianausin.statements.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matches expected payments with imported bank transactions.
 * <p>
 * A transaction is a candidate when it is on the same account, has exactly the expected amount
 * and was booked between {@value #DAYS_BEFORE} days before and {@value #DAYS_AFTER} days after
 * the due date. Then:
 * <ul>
 *     <li>a candidate whose description contains the payment reference is a match;</li>
 *     <li>otherwise a single candidate is a match;</li>
 *     <li>several candidates and no reference to tell them apart: the payment needs manual
 *     {@code REVIEW};</li>
 *     <li>no candidate and the statements already cover the whole window: the payment is
 *     {@code OVERDUE}.</li>
 * </ul>
 */
@Component
public class PaymentReconciler {

    static final int DAYS_BEFORE = 3;
    static final int DAYS_AFTER = 5;

    private final JdbcClient jdbc;

    public PaymentReconciler(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReconciliationResult reconcile() {
        List<ExpectedPayment> open = jdbc.sql("""
                        SELECT id, account_id, reference, amount, due_date FROM expected_payments
                        WHERE status IN ('PENDING', 'REVIEW', 'OVERDUE')
                        ORDER BY due_date, id
                        """)
                .query(ExpectedPayment.class)
                .list();

        int matched = 0;
        int review = 0;
        int overdue = 0;
        for (ExpectedPayment payment : open) {
            switch (decide(payment)) {
                case Outcome.Matched(long transactionId) -> {
                    markMatched(payment, transactionId);
                    matched++;
                }
                case Outcome.Ambiguous() -> {
                    setStatus(payment, "REVIEW");
                    review++;
                }
                case Outcome.Overdue() -> {
                    setStatus(payment, "OVERDUE");
                    overdue++;
                }
                case Outcome.Pending() -> setStatus(payment, "PENDING");
            }
        }
        return new ReconciliationResult(open.size(), matched, review, overdue);
    }

    private Outcome decide(ExpectedPayment payment) {
        List<Candidate> candidates = jdbc.sql("""
                        SELECT t.id, t.description FROM bank_transactions t
                        WHERE t.account_id = :account AND t.amount = :amount
                          AND t.booking_date BETWEEN :from AND :to
                          AND NOT EXISTS (SELECT 1 FROM expected_payments p WHERE p.matched_transaction_id = t.id)
                        ORDER BY t.booking_date, t.id
                        """)
                .param("account", payment.accountId())
                .param("amount", payment.amount())
                .param("from", payment.dueDate().minusDays(DAYS_BEFORE))
                .param("to", payment.dueDate().plusDays(DAYS_AFTER))
                .query(Candidate.class)
                .list();

        String reference = payment.reference().toUpperCase(Locale.ROOT);
        Optional<Candidate> byReference = candidates.stream()
                .filter(candidate -> candidate.description().toUpperCase(Locale.ROOT).contains(reference))
                .findFirst();
        if (byReference.isPresent()) {
            return new Outcome.Matched(byReference.get().id());
        }
        if (candidates.size() == 1) {
            return new Outcome.Matched(candidates.getFirst().id());
        }
        if (candidates.size() > 1) {
            return new Outcome.Ambiguous();
        }
        return statementsCover(payment) ? new Outcome.Overdue() : new Outcome.Pending();
    }

    /**
     * Whether the imported statements already go past the end of the matching window, i.e. the
     * payment would have shown up by now.
     */
    private boolean statementsCover(ExpectedPayment payment) {
        LocalDate lastBooking = jdbc.sql("SELECT max(booking_date) FROM bank_transactions WHERE account_id = ?")
                .param(payment.accountId())
                .query(LocalDate.class)
                .optional()
                .orElse(null);
        return lastBooking != null && lastBooking.isAfter(payment.dueDate().plusDays(DAYS_AFTER));
    }

    private void markMatched(ExpectedPayment payment, long transactionId) {
        jdbc.sql("""
                        UPDATE expected_payments
                        SET status = 'MATCHED', matched_transaction_id = ?, reconciled_at = now()
                        WHERE id = ?
                        """)
                .params(transactionId, payment.id())
                .update();
    }

    private void setStatus(ExpectedPayment payment, String status) {
        jdbc.sql("UPDATE expected_payments SET status = ?, reconciled_at = now() WHERE id = ?")
                .params(status, payment.id())
                .update();
    }

    record ExpectedPayment(long id, String accountId, String reference, BigDecimal amount, LocalDate dueDate) {
    }

    record Candidate(long id, String description) {
    }

    sealed interface Outcome {

        record Matched(long transactionId) implements Outcome {
        }

        record Ambiguous() implements Outcome {
        }

        record Overdue() implements Outcome {
        }

        record Pending() implements Outcome {
        }
    }

    public record ReconciliationResult(int examined, int matched, int needReview, int overdue) {
    }
}
