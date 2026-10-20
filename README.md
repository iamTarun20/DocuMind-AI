# DocuMind AI

Turn a text PDF or TXT document into a focused 10-question study session.

**React + TypeScript · Java + Spring Boot · PostgreSQL · Gemini API · GitHub Codespaces**

## Start in GitHub Codespaces

1. Create an API key in [Google AI Studio](https://aistudio.google.com/apikey). Use a project with available free-tier quota. Do not paste the key into chat or commit it.
2. In GitHub, open **Settings → Codespaces → Secrets → New secret**. Name it `GEMINI_API_KEY`, paste the key as its value, and grant this repository access.
3. Open [this repository](https://github.com/iamTarun20/DocuMind-AI), choose **Code → Codespaces → Create codespace on main**. The committed dev container installs Java 21, Maven and Node 22, starts PostgreSQL, and installs/builds the dependencies. Choose the smallest available machine to begin.
4. When setup finishes, run from the repository root:

   ```bash
   bash scripts/dev.sh
   ```

5. Open port **5173** from the Codespaces **Ports** panel. Keep its visibility **Private**. The frontend proxies API requests to the backend on port 8080. PostgreSQL is not publicly forwarded.
6. Download the sample study notes from the app, upload the TXT file, accept the hosted processing notice, and generate a quiz.

If the Codespace already existed when you added the secret, stop and restart it to load the secret. Rebuild the container after changing its configuration. If the automatic dependency setup failed, run `bash scripts/setup.sh`, then start the application again.

A Codespace is a development environment, **not production hosting**. Stop it when finished. Stopped Codespaces still consume storage; commit your work before deleting one. Persistent database volumes survive ordinary container restarts, but not deletion of the Codespace.

## What the first release does

- Accepts readable PDF and UTF-8 TXT files.
- Returns a persisted job immediately after extraction, then generates questions on a single background worker.
- Generates exactly 10 questions, each with four distinct options, one correct answer, an explanation, and a supporting excerpt.
- Validates structure, duplicate prompts/options, answer indices, and excerpt presence before making a quiz available.
- Keeps correct answers and explanations out of the quiz GET response; grades attempts on the server.
- Allows retakes, quiz history, and deletion in the browser session that created the quiz.
- Shows provider quota failures and interrupted jobs explicitly; does not silently retry calls that might consume quota.
- Includes Flyway migrations, backend tests, browser tests, CI, and Docker packaging.

**AI validity:** checking that an excerpt appears in the source does not prove the correct answer follows from it. Review generated questions before relying on them for assessment. Ten questions cannot exhaustively cover a long document.

## Deliberate first-release limits

| Area | Initial behavior |
| --- | --- |
| Upload | Up to 10 MB; PDF or UTF-8 TXT |
| PDF pages | Up to 100 |
| Extracted text | 300–60,000 characters; rejected above the limit, never silently truncated |
| Scanned or encrypted PDFs | Rejected; OCR is a later feature |
| Quiz | Exactly 10 MCQs with four choices |
| Queue | At most five queued/processing quizzes; one worker |
| Cost budget | At most 30 admitted generations per rolling 24 hours across the application by default |
| Text retention | Original uploaded file not retained; extracted source text cleared on completion/failure |
| Quiz retention | Quizzes, answers, explanations and excerpts retained for seven days; cleanup runs hourly |
| Browser history | Latest 20 quizzes; clearing browser storage loses access to that browser's quizzes |
| Account model | Pseudonymous browser capability token, not real user authentication |

These limits bound processing and API exposure. They are software configuration choices, not a dependency on the developer laptop's RAM. An API provider can still impose smaller request/rate quotas. A successful admission followed by a failure still consumes the application budget. Deleting a quiz does not reset that budget.

## Privacy and access model

The API key stays on the backend. The frontend never receives it. The document text is sent to Google's hosted Gemini API after explicit consent. Google's free-tier pricing page states that data may be used to improve its products; use non-sensitive study material and check the current provider terms. Excerpts remain in stored quizzes, even after full source text is cleared. Database deletion is logical application deletion; provider retention and managed database backup policies are separate.

A random UUID stored in localStorage is sent as `X-Session-Id`. It acts as an unguessable capability for that browser's collection. Anyone who obtains it can access those quizzes. This is not an identity system, and it is not sufficient for sensitive or public multi-user hosting. Keep the Codespace private.

## Architecture and build order

Start with **Codespaces → API contract → thin backend/database flow → frontend integration → live AI verification → deployment**. Build complete vertical slices so each stage is observable. The current implementation already includes the first slice.

```text
frontend/            React, TypeScript, Vite, UI and browser tests
backend/             Spring Boot API, extraction, generation, scoring, persistence
.devcontainer/       Repeatable Codespaces setup with PostgreSQL
scripts/             Setup and development launch commands
docs/                API contract and next implementation phases
.github/workflows/   Frontend and backend verification
compose.yml          Private single-host Docker packaging
```

PDFBox extracts document text in the backend. PostgreSQL stores queued jobs and completed quizzes. A scheduled single worker claims queued jobs with a conditional update, calls Gemini outside a database transaction, validates the output, and persists completion or failure. Polling reads the job status. No local LLM, embedding service, vector database, Redis or Kafka is required for this release.

On restart, queued jobs resume, while processing jobs become failed with a clear message, preventing automatic repeat API calls. Run **one backend replica only**. The admission lock, worker recovery logic and global budget admission are designed for a single-instance MVP. Multiple replicas need distributed admission, worker leases and idempotency.

## Configuration

Prefer Codespaces secrets. Alternatively copy `.env.example` to `.env`, enter values locally, and use `scripts/dev.sh`, which loads it. `.env` is ignored by Git. Never use a `VITE_` variable for the AI key.

| Variable | Default / requirement |
| --- | --- |
| `GEMINI_API_KEY` | Required for real generation |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite`; configurable if model access changes |
| `DB_URL` | `jdbc:postgresql://localhost:5432/documind` |
| `DB_USERNAME` | `documind` |
| `DB_PASSWORD` | Development-only password from dev container; change for hosting |
| `DAILY_QUIZ_LIMIT` | `30`, global rolling 24-hour cap |
| `PORT` | `8080` |
| `WORKER_ENABLED` | `true`; tests disable automatic processing |

Provider integration follows the documented Gemini `generateContent` REST structured output format. Model availability, free-tier eligibility and quota are controlled by Google. The application does not enable billing or guarantee a free quota. There is no hard provider-side currency limit in this code; configure provider billing/budget controls separately if enabling paid usage.

## Verify

```bash
mvn -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run build
cd frontend
npx playwright install chromium
npm run test:e2e
```

Backend tests use H2 in PostgreSQL compatibility mode by default, with Gemini mocked. CI runs the same integration tests against PostgreSQL 17. Browser tests mock API responses to verify UI behavior without consuming AI quota. These tests do **not** establish live AI accuracy or provider compatibility for your account. A real sample-document run with your API key remains required.

To run backend tests against an external test PostgreSQL database, set `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`. Use a dedicated disposable test database: the test suite clears its tables.

## Docker packaging

For a private machine with Docker Compose, set `GEMINI_API_KEY` and a strong `DB_PASSWORD` in a local `.env`, then:

```bash
docker compose up --build
```

Open `http://localhost:3000`. Only the frontend is bound to the host, on loopback. This packaging uses Nginx to proxy `/api` to the backend and serves the frontend build. It does not buy hosting, configure TLS, create a managed database, or make the project publicly production-ready.

## Public hosting and larger documents

See [docs/ROADMAP.md](docs/ROADMAP.md) for staged upgrades. Before public exposure: add identity-based authentication and authorization, per-user/IP abuse controls, TLS, managed database backups and monitoring. Next, move to managed PostgreSQL, deploy the backend separately, and host frontend assets with a same-origin API proxy. For large files, add object storage with presigned uploads, a durable queue, bounded-memory extraction, chunking, hierarchical coverage selection, and worker time/resource budgets. Increase limits only after measuring representative files.

## Official references

- [GitHub Codespaces billing](https://docs.github.com/en/billing/concepts/product-billing/github-codespaces)
- [Gemini model availability](https://ai.google.dev/gemini-api/docs/models)
- [Gemini pricing and data-use distinction](https://ai.google.dev/gemini-api/docs/pricing)
- [Gemini Generate Content structured output](https://ai.google.dev/gemini-api/docs/generate-content/structured-output)

No throughput, latency or accuracy benchmarks are claimed before measurement.
