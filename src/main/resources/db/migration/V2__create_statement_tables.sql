CREATE TABLE bank_transactions
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       VARCHAR(34)    NOT NULL,
    booking_date     DATE           NOT NULL,
    description      VARCHAR(255)   NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    category         VARCHAR(20)    NOT NULL,
    -- SHA-256 of the movement, so importing the same statement twice is a no-op
    fingerprint      VARCHAR(64)    NOT NULL,
    source_file      VARCHAR(255)   NOT NULL,
    job_execution_id BIGINT         NOT NULL,
    imported_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_bank_transactions_fingerprint UNIQUE (fingerprint),
    CONSTRAINT ck_bank_transactions_amount CHECK (amount <> 0)
);

CREATE INDEX idx_bank_transactions_account_date ON bank_transactions (account_id, booking_date);
CREATE INDEX idx_bank_transactions_job ON bank_transactions (job_execution_id);

-- Lines skipped during an import, with the reason, so nothing is lost silently
CREATE TABLE import_errors
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_execution_id BIGINT       NOT NULL,
    source_file      VARCHAR(255) NOT NULL,
    line_number      INT,
    line             TEXT,
    reason           VARCHAR(500) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_import_errors_job ON import_errors (job_execution_id);
