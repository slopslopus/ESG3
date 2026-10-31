CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- widen KR1 surrogate keys to BIGINT for JPA Long @Id (V1/V2 stay verbatim KR1) --
ALTER TABLE employees ALTER COLUMN id TYPE BIGINT;
ALTER TABLE visitors ALTER COLUMN id TYPE BIGINT;
ALTER TABLE visit_requests ALTER COLUMN id TYPE BIGINT;
ALTER TABLE visit_requests ALTER COLUMN visitor_id TYPE BIGINT;
ALTER TABLE visit_requests ALTER COLUMN host_id TYPE BIGINT;


-- extensions of visit_requests (KR1 already has created_at) --
ALTER TABLE visit_requests
    ADD COLUMN uuid UUID,
    ADD COLUMN request_code VARCHAR(20),
    ADD COLUMN guest_count INT,
    ADD COLUMN needs_escort BOOLEAN,
    ADD COLUMN version BIGINT,
    ADD COLUMN archived BOOLEAN,
    ADD COLUMN updated_at TIMESTAMP;

CREATE SEQUENCE visit_request_code_seq START 13;

-- backfill existing rows: VR-000001..VR-000012 in id order --
WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM visit_requests
)
UPDATE visit_requests vr
SET uuid = gen_random_uuid(),
    request_code = 'VR-' || LPAD(numbered.rn::text, 6, '0'),
    guest_count = 1,
    needs_escort = FALSE,
    version = 0,
    archived = FALSE,
    updated_at = NOW()
FROM numbered
WHERE vr.id = numbered.id;

SELECT setval('visit_request_code_seq',
    (SELECT COUNT(*) FROM visit_requests));

ALTER TABLE visit_requests
    ALTER COLUMN uuid SET NOT NULL,
    ALTER COLUMN request_code SET NOT NULL,
    ALTER COLUMN guest_count SET NOT NULL,
    ALTER COLUMN needs_escort SET NOT NULL,
    ALTER COLUMN version SET NOT NULL,
    ALTER COLUMN archived SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE visit_requests ADD CONSTRAINT uq_visit_requests_uuid UNIQUE (uuid);
ALTER TABLE visit_requests ADD CONSTRAINT uq_visit_requests_request_code UNIQUE (request_code);
ALTER TABLE visit_requests ADD CONSTRAINT ck_visit_requests_guest_count
    CHECK (guest_count BETWEEN 1 AND 50);

-- extensions of employees --
ALTER TABLE employees
    ADD COLUMN uuid UUID,
    ADD COLUMN active BOOLEAN;

UPDATE employees
SET uuid = gen_random_uuid(),
    active = TRUE;

ALTER TABLE employees
    ALTER COLUMN uuid SET NOT NULL,
    ALTER COLUMN active SET NOT NULL;

ALTER TABLE employees ADD CONSTRAINT uq_employees_uuid UNIQUE (uuid);

-- new tables (users first: visit_notes references it) --
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL CHECK (char_length(name) BETWEEN 2 AND 150),
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL
);

CREATE TABLE visit_notes (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    request_id BIGINT NOT NULL REFERENCES visit_requests(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    text TEXT NOT NULL CHECK (char_length(text) BETWEEN 1 AND 2000),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE visit_tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE CHECK (char_length(name) BETWEEN 1 AND 50)
);

CREATE TABLE visit_request_tags (
    request_id BIGINT NOT NULL REFERENCES visit_requests(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES visit_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (request_id, tag_id)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL
        CHECK (role IN ('VIEWER', 'OPERATOR', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    ip VARCHAR(45),
    user_agent VARCHAR(500)
);

CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_uuid UUID NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    description TEXT,
    correlation_id VARCHAR(36)
);

-- indexes (spec 2.3) --
CREATE INDEX idx_visit_requests_status ON visit_requests(status);
CREATE INDEX idx_visit_requests_visit_date ON visit_requests(visit_date);
CREATE INDEX idx_visit_requests_archived ON visit_requests(archived);
CREATE INDEX idx_visit_requests_host ON visit_requests(host_id);
CREATE INDEX idx_visit_requests_guest_count ON visit_requests(guest_count);
CREATE INDEX idx_audit_events_user ON audit_events(user_id);
CREATE INDEX idx_audit_events_created ON audit_events(created_at);
CREATE INDEX idx_audit_events_type ON audit_events(event_type);
CREATE INDEX idx_visit_notes_request ON visit_notes(request_id);
CREATE INDEX idx_visit_request_tags_tag ON visit_request_tags(tag_id);
CREATE INDEX idx_refresh_sessions_user ON refresh_sessions(user_id);
