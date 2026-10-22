-- Credit Card Billing & Statement Management System - initial schema
-- Written to run unchanged on MySQL 8.x and on H2 (MODE=MySQL).
-- All money columns are DECIMAL(19,2); percentage columns are DECIMAL(7,4). Currency: INR (assumption).
-- All timestamps are UTC wall-clock values stored as DATETIME(6).

CREATE TABLE customers (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(190) NOT NULL,
    phone         VARCHAR(20),
    password_hash VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE administrators (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_administrators_username UNIQUE (username)
);

CREATE TABLE bin_configurations (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bin_code                    VARCHAR(6)    NOT NULL,
    name                        VARCHAR(100)  NOT NULL,
    billing_cycle_day           INT           NOT NULL,
    payment_due_days            INT           NOT NULL,
    min_due_percentage          DECIMAL(7, 4) NOT NULL,
    min_due_floor               DECIMAL(19, 2) NOT NULL,
    finance_charge_monthly_rate DECIMAL(7, 4) NOT NULL,
    gst_rate                    DECIMAL(7, 4) NOT NULL,
    late_fee_percentage         DECIMAL(7, 4) NOT NULL,
    late_fee_floor              DECIMAL(19, 2) NOT NULL,
    late_fee_cap                DECIMAL(19, 2) NOT NULL,
    late_fee_threshold          DECIMAL(19, 2) NOT NULL,
    rule_version                INT           NOT NULL DEFAULT 1,
    created_at                  DATETIME(6)   NOT NULL,
    updated_at                  DATETIME(6)   NOT NULL,
    version                     INT           NOT NULL DEFAULT 0,
    CONSTRAINT uk_bin_configurations_bin UNIQUE (bin_code),
    CONSTRAINT ck_bin_cycle_day CHECK (billing_cycle_day BETWEEN 1 AND 28),
    CONSTRAINT ck_bin_due_days CHECK (payment_due_days BETWEEN 1 AND 27),
    CONSTRAINT ck_bin_min_due_pct CHECK (min_due_percentage > 0 AND min_due_percentage <= 100),
    CONSTRAINT ck_bin_min_due_floor CHECK (min_due_floor >= 0),
    CONSTRAINT ck_bin_finance_rate CHECK (finance_charge_monthly_rate >= 0 AND finance_charge_monthly_rate <= 25),
    CONSTRAINT ck_bin_gst_rate CHECK (gst_rate >= 0 AND gst_rate <= 50),
    CONSTRAINT ck_bin_late_pct CHECK (late_fee_percentage >= 0 AND late_fee_percentage <= 100),
    CONSTRAINT ck_bin_late_floor CHECK (late_fee_floor >= 0),
    CONSTRAINT ck_bin_late_cap CHECK (late_fee_cap >= late_fee_floor),
    CONSTRAINT ck_bin_late_threshold CHECK (late_fee_threshold >= 0)
);

CREATE TABLE credit_cards (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id              BIGINT         NOT NULL,
    bin_configuration_id     BIGINT         NOT NULL,
    masked_number            VARCHAR(19)    NOT NULL,
    last4                    VARCHAR(4)     NOT NULL,
    number_hash              VARCHAR(64)    NOT NULL,
    credit_limit             DECIMAL(19, 2) NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    opened_on                DATE           NOT NULL,
    expires_on               DATE           NOT NULL,
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    version                  INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_credit_cards_number_hash UNIQUE (number_hash),
    CONSTRAINT fk_cards_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_cards_bin FOREIGN KEY (bin_configuration_id) REFERENCES bin_configurations (id),
    CONSTRAINT ck_cards_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_cards_limit CHECK (credit_limit > 0)
);
CREATE INDEX idx_cards_customer ON credit_cards (customer_id);
CREATE INDEX idx_cards_bin ON credit_cards (bin_configuration_id);
CREATE INDEX idx_cards_status ON credit_cards (status);

CREATE TABLE statements (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id                     BIGINT         NOT NULL,
    bin_configuration_id        BIGINT         NOT NULL,
    rule_version                INT            NOT NULL,
    -- snapshot of the billing rules in force when the statement was generated
    billing_cycle_day           INT            NOT NULL,
    payment_due_days            INT            NOT NULL,
    min_due_percentage          DECIMAL(7, 4)  NOT NULL,
    min_due_floor               DECIMAL(19, 2) NOT NULL,
    finance_charge_monthly_rate DECIMAL(7, 4)  NOT NULL,
    gst_rate                    DECIMAL(7, 4)  NOT NULL,
    late_fee_percentage         DECIMAL(7, 4)  NOT NULL,
    late_fee_floor              DECIMAL(19, 2) NOT NULL,
    late_fee_cap                DECIMAL(19, 2) NOT NULL,
    late_fee_threshold          DECIMAL(19, 2) NOT NULL,
    period_start                DATE           NOT NULL,
    period_end                  DATE           NOT NULL,
    due_date                    DATE           NOT NULL,
    generated_at                DATETIME(6)    NOT NULL,
    generated_by                VARCHAR(190)   NOT NULL,
    previous_balance            DECIMAL(19, 2) NOT NULL,
    purchases_total             DECIMAL(19, 2) NOT NULL,
    credits_total               DECIMAL(19, 2) NOT NULL,
    fees_total                  DECIMAL(19, 2) NOT NULL,
    finance_charge              DECIMAL(19, 2) NOT NULL,
    gst_on_finance_charge       DECIMAL(19, 2) NOT NULL,
    total_amount_due            DECIMAL(19, 2) NOT NULL,
    minimum_amount_due          DECIMAL(19, 2) NOT NULL,
    paid_amount                 DECIMAL(19, 2) NOT NULL DEFAULT 0,
    payment_status              VARCHAR(20)    NOT NULL,
    version                     INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_statements_card_period UNIQUE (card_id, period_start, period_end),
    CONSTRAINT fk_statements_card FOREIGN KEY (card_id) REFERENCES credit_cards (id),
    CONSTRAINT fk_statements_bin FOREIGN KEY (bin_configuration_id) REFERENCES bin_configurations (id),
    CONSTRAINT ck_statements_period CHECK (period_end >= period_start),
    CONSTRAINT ck_statements_due CHECK (due_date > period_end),
    CONSTRAINT ck_statements_min_due CHECK (minimum_amount_due >= 0),
    CONSTRAINT ck_statements_paid CHECK (paid_amount >= 0),
    CONSTRAINT ck_statements_status CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID'))
);
CREATE INDEX idx_statements_card_period_end ON statements (card_id, period_end);
CREATE INDEX idx_statements_due_date ON statements (due_date);
CREATE INDEX idx_statements_status ON statements (payment_status);

CREATE TABLE billing_entries (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id          BIGINT         NOT NULL,
    statement_id     BIGINT,
    reference_id     VARCHAR(64)    NOT NULL,
    entry_type       VARCHAR(20)    NOT NULL,
    amount           DECIMAL(19, 2) NOT NULL,
    description      VARCHAR(255)   NOT NULL,
    transaction_date DATE           NOT NULL,
    posted_at        DATETIME(6)    NOT NULL,
    created_by       VARCHAR(190)   NOT NULL,
    CONSTRAINT uk_entries_card_reference UNIQUE (card_id, reference_id),
    CONSTRAINT fk_entries_card FOREIGN KEY (card_id) REFERENCES credit_cards (id),
    CONSTRAINT fk_entries_statement FOREIGN KEY (statement_id) REFERENCES statements (id),
    CONSTRAINT ck_entries_type CHECK (entry_type IN ('PURCHASE', 'FEE', 'LATE_FEE', 'REFUND')),
    CONSTRAINT ck_entries_amount CHECK (amount > 0)
);
CREATE INDEX idx_entries_card_unbilled ON billing_entries (card_id, statement_id, transaction_date);
CREATE INDEX idx_entries_statement ON billing_entries (statement_id);

CREATE TABLE payment_methods (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id       BIGINT       NOT NULL,
    method_type       VARCHAR(20)  NOT NULL,
    display_name      VARCHAR(80)  NOT NULL,
    masked_identifier VARCHAR(60)  NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_methods_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_methods_type CHECK (method_type IN ('UPI', 'NET_BANKING', 'DEBIT_CARD', 'BANK_TRANSFER'))
);
CREATE INDEX idx_methods_customer ON payment_methods (customer_id);

CREATE TABLE payments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id         BIGINT         NOT NULL,
    statement_id        BIGINT         NOT NULL,
    payment_method_id   BIGINT         NOT NULL,
    idempotency_key     VARCHAR(64)    NOT NULL,
    request_fingerprint VARCHAR(64)    NOT NULL,
    amount              DECIMAL(19, 2) NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    initiated_at        DATETIME(6)    NOT NULL,
    completed_at        DATETIME(6)    NOT NULL,
    reversed_at         DATETIME(6),
    reversal_reason     VARCHAR(255),
    reversed_by         VARCHAR(190),
    version             INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_customer_idem UNIQUE (customer_id, idempotency_key),
    CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_payments_statement FOREIGN KEY (statement_id) REFERENCES statements (id),
    CONSTRAINT fk_payments_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('COMPLETED', 'REVERSED'))
);
CREATE INDEX idx_payments_statement ON payments (statement_id);
CREATE INDEX idx_payments_customer_time ON payments (customer_id, initiated_at);

-- Append-only record of how each payment was applied (reversals are new negative rows).
CREATE TABLE payment_allocations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT         NOT NULL,
    statement_id    BIGINT         NOT NULL,
    allocation_type VARCHAR(20)    NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    CONSTRAINT uk_alloc_payment_type UNIQUE (payment_id, allocation_type),
    CONSTRAINT fk_alloc_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_alloc_statement FOREIGN KEY (statement_id) REFERENCES statements (id),
    CONSTRAINT ck_alloc_type CHECK (allocation_type IN ('ALLOCATION', 'REVERSAL')),
    CONSTRAINT ck_alloc_amount CHECK (
        (allocation_type = 'ALLOCATION' AND amount > 0) OR (allocation_type = 'REVERSAL' AND amount < 0))
);
CREATE INDEX idx_alloc_statement ON payment_allocations (statement_id);

-- Append-only late-fee ledger.
CREATE TABLE late_fee_ledger (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    statement_id       BIGINT         NOT NULL,
    card_id            BIGINT         NOT NULL,
    billing_entry_id   BIGINT,
    fee_event          VARCHAR(30)    NOT NULL,
    fee_amount         DECIMAL(19, 2) NOT NULL,
    gst_rate           DECIMAL(7, 4)  NOT NULL,
    gst_amount         DECIMAL(19, 2) NOT NULL,
    total_amount       DECIMAL(19, 2) NOT NULL,
    unpaid_minimum_due DECIMAL(19, 2) NOT NULL,
    rule_version       INT            NOT NULL,
    rule_snapshot      VARCHAR(1000)  NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    assessed_at        DATETIME(6)    NOT NULL,
    assessed_by        VARCHAR(190)   NOT NULL,
    CONSTRAINT uk_late_fee_statement_event UNIQUE (statement_id, fee_event),
    CONSTRAINT fk_late_fee_statement FOREIGN KEY (statement_id) REFERENCES statements (id),
    CONSTRAINT fk_late_fee_card FOREIGN KEY (card_id) REFERENCES credit_cards (id),
    CONSTRAINT fk_late_fee_entry FOREIGN KEY (billing_entry_id) REFERENCES billing_entries (id),
    CONSTRAINT ck_late_fee_amounts CHECK (fee_amount > 0 AND gst_amount >= 0 AND total_amount = fee_amount + gst_amount),
    CONSTRAINT ck_late_fee_status CHECK (status IN ('POSTED'))
);
CREATE INDEX idx_late_fee_card ON late_fee_ledger (card_id);

-- Append-only audit trail.
CREATE TABLE audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at DATETIME(6)   NOT NULL,
    actor_type  VARCHAR(20)   NOT NULL,
    actor_id    BIGINT,
    actor_name  VARCHAR(190),
    action      VARCHAR(60)   NOT NULL,
    entity_type VARCHAR(40)   NOT NULL,
    entity_id   VARCHAR(64),
    details     VARCHAR(1000),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('CUSTOMER', 'ADMIN', 'SYSTEM'))
);
CREATE INDEX idx_audit_time ON audit_logs (occurred_at);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_actor ON audit_logs (actor_type, actor_id);
