-- KR3 column defaults pinned by spec 2.2 (V3 added NOT NULL without
-- DEFAULT; raw-SQL inserts without those columns must still work) --
ALTER TABLE visit_requests ALTER COLUMN guest_count SET DEFAULT 1;
ALTER TABLE visit_requests ALTER COLUMN needs_escort SET DEFAULT FALSE;
ALTER TABLE visit_requests ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE visit_requests ALTER COLUMN archived SET DEFAULT FALSE;
ALTER TABLE visit_requests ALTER COLUMN updated_at SET DEFAULT NOW();
ALTER TABLE employees ALTER COLUMN active SET DEFAULT TRUE;
