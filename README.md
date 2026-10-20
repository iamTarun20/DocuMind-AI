# DocuMind AI

**Turn reading material into a focused study session.**

Reading a chapter can feel productive without revealing how much of it you remember. DocuMind AI helps bridge that gap: upload your notes, answer ten questions, and review the explanations alongside excerpts from your document.

Built with **React, TypeScript, Java, Spring Boot, PostgreSQL, and the Gemini API**.

[Development guide](docs/DEVELOPMENT.md) · [API reference](docs/API.md) · [Roadmap](docs/ROADMAP.md) · [Build checks](https://github.com/iamTarun20/DocuMind-AI/actions/workflows/ci.yml)

## What it does

- **Creates document-based quizzes.** Upload a readable PDF or a UTF-8 text file to generate ten multiple-choice questions.
- **Makes answers reviewable.** Each question includes an explanation and a supporting document excerpt after submission.
- **Checks recall before revealing answers.** Correct answers stay on the server until the quiz is submitted, where scoring also takes place.
- **Keeps study sessions accessible.** Reopen recent quizzes, retake them, or delete them from the same browser session.
- **Processes generation in the background.** A persisted job tracks whether a quiz is queued, processing, ready, or failed.
- **Handles failures explicitly.** Invalid documents, quota limits, and rejected AI responses produce visible errors rather than fabricated quizzes.

## How it works

The backend extracts text with Apache PDFBox, saves a generation job in PostgreSQL, and processes it with a background worker. The worker asks Gemini for structured questions, checks the response, and stores the completed quiz. The frontend polls for progress and presents the questions once they are ready.

Validation checks the number of questions, option uniqueness, answer indices, duplicate prompts, and whether supporting excerpts appear in the extracted text. These checks improve traceability; they do not guarantee that every answer is factually correct or that ten questions cover an entire chapter.

| Layer | Technology | Responsibility |
| --- | --- | --- |
| Interface | React, TypeScript, Vite | Uploads, quiz navigation, progress, results, and history |
| Application | Java, Spring Boot | Document validation, API endpoints, scoring, and job processing |
| Persistence | PostgreSQL, Spring Data JPA, Flyway | Queued jobs, quizzes, generation budgets, and schema migrations |
| AI integration | Gemini REST API | Structured question generation from extracted document text |
| Verification | JUnit, Spring Boot Test, Playwright, GitHub Actions | Backend integration tests and browser workflow checks |
| Development | GitHub Codespaces, Docker | Repeatable environment with remotely running development services |

No local AI model or GPU is required. When developed in Codespaces, the application and database run in the cloud rather than on the developer's laptop.

## Run the project

1. Create a Gemini API key in [Google AI Studio](https://aistudio.google.com/apikey).
2. Add it as a GitHub Codespaces secret named `GEMINI_API_KEY`, with access to this repository.
3. Create a Codespace from the `main` branch. Its configuration installs the dependencies and starts PostgreSQL.
4. From the repository root, run:

   ```bash
   bash scripts/dev.sh
   ```

5. Open forwarded port **5173** from the **Ports** panel. Keep it private while developing.

The application includes downloadable sample study notes for a first test. For configuration, Docker commands, troubleshooting, and verification instructions, see the [development guide](docs/DEVELOPMENT.md).

## Current scope

DocuMind AI is an early working version intended for private development and evaluation. It is not yet a publicly hardened service.

| Area | Current behavior |
| --- | --- |
| Files | Readable PDF and UTF-8 TXT; up to 10 MB |
| PDF length | Up to 100 pages |
| Extracted text | 300–60,000 characters; larger inputs are rejected rather than silently shortened |
| Quiz format | Ten questions, four options each, with explanations and source excerpts |
| Processing | One backend instance and one worker; at most five queued or processing jobs |
| Generation budget | Thirty admitted generations per rolling 24 hours by default, across the application |
| History | Latest twenty quizzes for the current browser session |
| Retention | Quizzes expire after seven days; cleanup runs hourly |

Scanned PDFs, OCR, DOCX, cross-device accounts, and large-document chunking are planned improvements. Complex textbook layouts and PDF text extraction can affect generation quality; not every PDF within the size limit will produce a valid quiz.

## Privacy and access

Document text is sent to Google's Gemini API only after the upload consent is accepted. The API key stays on the backend. Original uploads are not retained by the application; extracted source text is cleared when processing completes or fails. Completed quizzes still contain supporting excerpts until deleted or expired. Provider retention and database backups are governed separately.

Google's free-tier terms may allow submitted data to improve its products. Use non-sensitive material and review the [current pricing and data-use terms](https://ai.google.dev/gemini-api/docs/pricing).

The current access model uses a random browser-session capability token, not a user account. Clearing browser storage loses access to that browser's collection. Authentication and per-user abuse controls are required before a public multi-user launch.

## Verification

The repository includes backend tests for extraction, scoring, answer-key visibility, session isolation, queue admission, generation budgets, and restart handling. Browser tests cover the upload-to-results interaction, error display, retakes, and mobile layout.

CI runs backend integration tests against PostgreSQL 17 and builds and tests the frontend. AI responses are mocked in automated tests; real-document quality and live provider access require separate evaluation. No latency, throughput, or accuracy benchmarks are claimed yet.

## Next steps

- Improve PDF extraction diagnostics and evaluate real chapter-length documents.
- Add OCR and chunk-based processing for longer or image-based material.
- Introduce user accounts, per-user quotas, and stronger upload abuse controls.
- Deploy the frontend and backend independently of the development Codespace, using persistent managed PostgreSQL.
- Introduce worker leases and deployment-safe job recovery before running overlapping backend instances.
- Measure question quality, processing time, memory use, and API costs on a documented evaluation set.

See the [roadmap](docs/ROADMAP.md) for the reasoning and acceptance criteria behind these steps.
