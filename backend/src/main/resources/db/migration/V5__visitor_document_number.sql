-- Task 5: document number required by the request DTO contract
-- (CreateRequestDto.visitorDocumentNumber / visitor.documentNumber).
-- KR1/V3 schema has no such column; nullable for existing seed visitors.
ALTER TABLE visitors ADD COLUMN document_number VARCHAR(30);
