CREATE TABLE IF NOT EXISTS customer (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    max_approved_amount NUMERIC(15,2) NOT NULL,
    current_approved_amount NUMERIC(15,2) NOT NULL DEFAULT 0
);


CREATE TABLE IF NOT EXISTS credit_application (
    id BIGSERIAL PRIMARY KEY,
    application_reference VARCHAR(100) NOT NULL UNIQUE,
    customer_id VARCHAR(50) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    term_months INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(50),
    processed_at TIMESTAMP NOT NULL,
    request_hash VARCHAR(64)
);