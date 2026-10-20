#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
npm --prefix frontend ci
mvn -f backend/pom.xml -B -q -DskipTests package
printf '\nReady. Add your GEMINI_API_KEY Codespaces secret, then run: bash scripts/dev.sh\n'
