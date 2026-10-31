-- Task 13 (live defense check): align pass_number_seq past seed data.
-- V6 created the sequence START 1, but V2 seed already uses PASS-001..PASS-005,
-- so the first server-generated pass number collided (bulk SET_STATUS→ISSUED
-- failed with duplicate key PASS-001). Advance the sequence past the max used.
SELECT setval('pass_number_seq',
    COALESCE((SELECT MAX(CAST(SUBSTRING(pass_number FROM 6) AS INTEGER))
              FROM visit_requests
              WHERE pass_number ~ '^PASS-[0-9]+$'), 0));
