#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
if [[ -z "${GEMINI_API_KEY:-}" ]]; then
  printf 'GEMINI_API_KEY is missing. Add a Codespaces secret or use a local .env file.\n' >&2
  exit 1
fi
mvn -f backend/pom.xml spring-boot:run &
backend_pid=$!
cleanup() { kill "$backend_pid" "${frontend_pid:-}" 2>/dev/null || true; }
trap cleanup EXIT INT TERM
npm --prefix frontend run dev &
frontend_pid=$!
wait -n "$backend_pid" "$frontend_pid"
