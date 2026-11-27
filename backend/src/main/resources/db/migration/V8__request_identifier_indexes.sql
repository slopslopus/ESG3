-- Explicit btree indexes on REST identifiers (spec 2.3 letter).
-- UNIQUE constraints already create backing indexes; standalone names
-- make EXPLAIN output and the spec checklist unambiguous. IF NOT EXISTS
-- keeps the migration idempotent for databases migrated by hand.
CREATE INDEX IF NOT EXISTS idx_visit_requests_uuid
  ON visit_requests(uuid);
CREATE INDEX IF NOT EXISTS idx_visit_requests_request_code
  ON visit_requests(request_code);
