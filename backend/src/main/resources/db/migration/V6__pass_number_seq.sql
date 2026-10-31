-- Task 5(fix round 1): server-side passNumber generation.
-- passNumber is never accepted from clients; on first transition to
-- ISSUED/COMPLETED the service assigns next value from this sequence.
CREATE SEQUENCE pass_number_seq START 1;
