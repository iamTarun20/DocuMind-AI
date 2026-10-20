-- No user content: deletion of a quiz must not reset the AI cost budget.
CREATE TABLE generation_requests (
 id UUID PRIMARY KEY,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_requests_created ON generation_requests(created_at);
