-- Payments we expect to see in a statement (rent, bills, invoices...). Reconciliation links
-- each one to the bank transaction that settled it.
CREATE TABLE expected_payments
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id             VARCHAR(34)    NOT NULL,
    reference              VARCHAR(100)   NOT NULL,
    description            VARCHAR(255),
    -- Signed as it should appear in the statement: negative for money going out
    amount                 NUMERIC(15, 2) NOT NULL,
    due_date               DATE           NOT NULL,
    status                 VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    matched_transaction_id BIGINT REFERENCES bank_transactions (id),
    reconciled_at          TIMESTAMPTZ,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_expected_payments_reference UNIQUE (account_id, reference),
    -- A bank transaction can settle at most one expected payment
    CONSTRAINT uq_expected_payments_transaction UNIQUE (matched_transaction_id),
    CONSTRAINT ck_expected_payments_status CHECK (status IN ('PENDING', 'MATCHED', 'REVIEW', 'OVERDUE')),
    CONSTRAINT ck_expected_payments_amount CHECK (amount <> 0)
);

CREATE INDEX idx_expected_payments_pending ON expected_payments (account_id) WHERE status <> 'MATCHED';
