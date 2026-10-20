# API contract — v0.1

All quiz routes require `X-Session-Id: <random UUID>`. Keep this capability private. Return values never include full source text. Errors are JSON: `{ "message": "Human-readable description" }`.

| Method | Route | Request | Response |
| --- | --- | --- | --- |
| POST | `/api/quizzes` | Multipart `file` and `consent=true` | 202 summary with UUID, filename and QUEUED status |
| GET | `/api/quizzes` | Session header | Latest 20 summaries for this session |
| GET | `/api/quizzes/{id}` | Session header | `{ quiz: Summary, questions: PublicQuestion[] }` |
| POST | `/api/quizzes/{id}/attempts` | `{ "answers": [0,1,2,3,0,1,2,3,0,1] }` | Score, total, and review with explanations/excerpts |
| DELETE | `/api/quizzes/{id}` | Session header | 204; processing jobs cannot be deleted |
| GET | `/actuator/health` | None | Basic health status, no infrastructure details |

`Summary`: `{ id, filename, status, error, createdAt }`. Status transitions: QUEUED → PROCESSING → READY or FAILED. A processing job interrupted by restart becomes FAILED. Generation errors appear in the summary; the initial upload response does not wait for hosted generation. Invalid extraction fails immediately with HTTP 400. Admission returns HTTP 429 if the global daily budget or queue capacity is exhausted.

`PublicQuestion`: `{ index, prompt, options }`. Options contain four strings; no answer key or source excerpt appears until submission. Answer indices are zero-based. Submission requires all ten selections in the range 0–3. Attempts are graded statelessly and are not persisted. Retaking is allowed; once submitted, the caller has received the answer key, so this is a study tool rather than a secure examination platform.

For a capability that does not own a quiz, read, grade and delete all return 404. Clearing localStorage creates a new browser capability and loses access to the previous collection. Token recovery and cross-device identity are future features.
