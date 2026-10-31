DROP TABLE IF EXISTS visit_requests;
DROP TABLE IF EXISTS visitors;
DROP TABLE IF EXISTS employees;

CREATE TABLE employees (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL CHECK (char_length(full_name) BETWEEN 2 AND 150),
    department VARCHAR(100) NOT NULL CHECK (char_length(department) BETWEEN 2 AND 100),
    role VARCHAR(20) NOT NULL
        CHECK (role IN ('SECURITY', 'HR', 'IT', 'FINANCE', 'SALES', 'OTHER'))
);

CREATE TABLE visitors (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL CHECK (char_length(full_name) BETWEEN 2 AND 150),
    document_type VARCHAR(20) NOT NULL
        CHECK (document_type IN ('PASSPORT', 'DRIVER_LICENSE', 'MILITARY_ID', 'OTHER'))
);

CREATE TABLE visit_requests (
    id SERIAL PRIMARY KEY,
    visitor_id INT NOT NULL REFERENCES visitors(id) ON DELETE RESTRICT,
    host_id INT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    purpose TEXT NOT NULL CHECK (char_length(purpose) BETWEEN 5 AND 1000),
    visit_date DATE NOT NULL CHECK (visit_date <= CURRENT_DATE + INTERVAL '6 months'),
    time_from TIME NOT NULL,
    time_to TIME NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('SUBMITTED', 'ISSUED', 'COMPLETED', 'CANCELLED')),
    pass_number VARCHAR(30) UNIQUE,
    arrived_at TIMESTAMP,
    left_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (time_from < time_to),
    CHECK (time_from >= TIME '08:00' AND time_to <= TIME '20:00'),
    CHECK ((time_to - time_from) BETWEEN INTERVAL '15 minutes' AND INTERVAL '12 hours'),
    CHECK (left_at IS NULL OR (arrived_at IS NOT NULL AND left_at >= arrived_at)),
    CHECK ((status IN ('ISSUED', 'COMPLETED') AND pass_number IS NOT NULL AND pass_number ~ '^PASS-[0-9]{3,6}$')
        OR (status IN ('SUBMITTED', 'CANCELLED') AND pass_number IS NULL))
);
