CREATE TABLE quizzes (
 id UUID PRIMARY KEY,
 owner_token VARCHAR(36) NOT NULL,
 filename VARCHAR(255) NOT NULL,
 status VARCHAR(20) NOT NULL,
 source_text TEXT,
 questions_json TEXT,
 error_message VARCHAR(500),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_quizzes_owner_created ON quizzes(owner_token, created_at);
CREATE INDEX idx_quizzes_status_created ON quizzes(status, created_at);
