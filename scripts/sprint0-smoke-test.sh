#!/usr/bin/env bash

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
COURSEWARE_FILE="${COURSEWARE_FILE:-./demo-courseware.pptx}"
AUDIO_FILE="${AUDIO_FILE:-./question.webm}"
COURSEWARE_ID="${COURSEWARE_ID:-}"
SESSION_ID="${SESSION_ID:-}"
QUESTION="${QUESTION:-这一页内容的重点是什么？}"

echo "=== Sprint0 MVP smoke test helper ==="
echo "BASE_URL=${BASE_URL}"
echo
echo "This script is a curl command collection for manual smoke testing."
echo "It does not require jq and does not auto-parse IDs for you."
echo

echo "1) Upload courseware"
cat <<EOF
curl -X POST "${BASE_URL}/api/v1/courseware/upload" \\
  -F "file=@${COURSEWARE_FILE}" \\
  -F "name=冲刺0联调示例课件"
EOF
echo

echo "2) Get courseware detail"
cat <<EOF
curl "${BASE_URL}/api/v1/courseware/<coursewareId>"
EOF
echo

echo "3) Generate script"
cat <<EOF
curl -X POST "${BASE_URL}/api/v1/courseware/<coursewareId>/script/generate"
EOF
echo

echo "4) Get script"
cat <<EOF
curl "${BASE_URL}/api/v1/courseware/<coursewareId>/script"
EOF
echo

echo "5) Start lecture"
cat <<EOF
curl -X POST "${BASE_URL}/api/v1/lecture/start" \\
  -H "Content-Type: application/json" \\
  -d "{\\"coursewareId\\":\\"<coursewareId>\\",\\"userId\\":\\"demo-user\\"}"
EOF
echo

echo "6) Pause lecture"
cat <<EOF
curl -X POST "${BASE_URL}/api/v1/lecture/<sessionId>/pause"
EOF
echo

echo "7) Resume lecture"
cat <<EOF
curl -X POST "${BASE_URL}/api/v1/lecture/resume" \\
  -H "Content-Type: application/json" \\
  -d "{\\"sessionId\\":\\"<sessionId>\\"}"
EOF
echo

echo "8) Ask text"
cat <<EOF
curl -X POST "${BASE_URL}/api/v1/qa/ask-text" \\
  -H "Content-Type: application/json" \\
  -d "{\\"sessionId\\":\\"<sessionId>\\",\\"question\\":\\"${QUESTION}\\"}"
EOF
echo

echo "9) ASR recognize"
cat <<EOF
curl -X POST "${BASE_URL}/api/v1/asr/recognize" \\
  -F "file=@${AUDIO_FILE}" \\
  -F "sessionId=<sessionId>" \\
  -F "pageIndex=1"
EOF
echo

echo "10) QA stream"
cat <<EOF
curl -N "${BASE_URL}/api/v1/qa/stream?sessionId=<sessionId>&question=%E8%BF%99%E4%B8%80%E9%A1%B5%E7%9A%84%E9%87%8D%E7%82%B9%E6%98%AF%E4%BB%80%E4%B9%88%EF%BC%9F&topK=5"
EOF
echo

echo "11) Get lecture records"
cat <<EOF
curl "${BASE_URL}/api/v1/lecture/<sessionId>/records"
EOF
echo

if [ -n "${COURSEWARE_ID}" ]; then
  echo "Quick command with your COURSEWARE_ID:"
  echo "curl \"${BASE_URL}/api/v1/courseware/${COURSEWARE_ID}\""
  echo
fi

if [ -n "${SESSION_ID}" ]; then
  echo "Quick commands with your SESSION_ID:"
  echo "curl \"${BASE_URL}/api/v1/lecture/${SESSION_ID}/records\""
  echo "curl -N \"${BASE_URL}/api/v1/qa/stream?sessionId=${SESSION_ID}&question=%E8%BF%99%E4%B8%80%E9%A1%B5%E7%9A%84%E9%87%8D%E7%82%B9%E6%98%AF%E4%BB%80%E4%B9%88%EF%BC%9F&topK=5\""
  echo
fi

echo "Done. Copy the commands above and replace <coursewareId> / <sessionId> with real values."
