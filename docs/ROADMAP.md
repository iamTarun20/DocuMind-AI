# Development phases and acceptance gates

## 1. Working private MVP — current implementation

Upload readable PDF/TXT → persisted job → hosted AI generation → 10 questions → server grading → explanations and source excerpts. Develop entirely inside Codespaces. A browser accesses the cloud environment, so a local Java installation, PostgreSQL server and local LLM are unnecessary.

Before marking this phase verified, run tests, then use a real API key to generate from the sample document, test provider quota/error behavior, and manually inspect all ten question-answer-excerpt combinations. Record actual results in the PR or issue; do not claim tests or benchmarks that have not run.

## 2. Safe public beta

Add real authentication with secure session cookies, enforce ownership by authenticated identity, CSRF protection where cookie auth requires it, per-user and per-IP quotas, upload request limits at the gateway, and operational logs without document content or secrets. Browser capability tokens are an MVP convenience only. Use TLS, configure frontend/API routing, and verify secrets are absent from frontend assets.

Move PostgreSQL to a managed service with required TLS, a connection pool within the provider's limit, backups, restore drills, and deletion/retention documentation. Deploy one backend replica initially. Verify that restarts, database disconnects and AI throttling do not lose accepted jobs or repeat provider charges silently. The Docker packaging is a starting point, not an automated cloud deployment.

## 3. Large-document pipeline

Add object storage and direct presigned uploads; do not store raw PDF blobs in PostgreSQL. Validate file types/content, bound extraction memory and CPU in an isolated worker, and add OCR as an opt-in hosted feature. Use a durable queue with leases, idempotent job transitions, explicit retries, backoff, failure queues and cancellation. Add document chunking, section-level metadata, and budget-aware hierarchical selection so quiz coverage is visible to the learner. Never promise arbitrary page counts or exhaustive coverage from ten questions.

Large files still have service limits. Set upload size, pages, extracted characters/tokens, execution time, retained bytes and per-user cost budgets. Show users when only selected sections are included. Scale by measurement rather than by removing all caps.

## 4. Measured reliability and portfolio evidence

Create a non-sensitive evaluation set spanning short notes, structured chapters, tables, malformed PDFs and image-only scans. Human-label correctness, ambiguity, evidence support and topic coverage. Measure extraction duration, queue wait, generation latency, end-to-end p50/p95, schema rejection rate, provider failures and estimated token cost. Report sample size, document sizes, deployment resources and concurrent users beside every benchmark. Never invent a percentage or latency for the resume.

For concurrency tests, mock the paid AI boundary first, then run a small controlled live sample within quota. Measure actual worker memory, including hostile/complex PDFs, before increasing limits. Update Spring/PDFBox/Vite dependencies and inspect advisories before public exposure.

## Suggested implementation order

1. Verify the current private Codespaces flow with a real key.
2. Improve question quality from observed failures.
3. Add identity, access controls and abuse limits.
4. Deploy the small version on remote infrastructure with managed PostgreSQL.
5. Add object storage, durable workers and larger-document processing when measured demand warrants them.
6. Collect reproducible benchmark and quality data for the README and resume.

React and Spring Boot are the product foundation. Redis, Kafka, microservices and vector search are optional architectural choices; add them only when a measured requirement justifies their operational cost.
