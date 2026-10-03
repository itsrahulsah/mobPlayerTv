#!/usr/bin/env bash
# Stop hook: when Claude finishes a turn that changed Kotlin/Gradle code, run the JVM unit tests.
# On failure, blocks the stop so Claude sees the failures and fixes them.
# Skips the run when the changed code is identical to the last passing run.

input=$(cat)
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0

changed=$(git status --porcelain 2>/dev/null --untracked-files=all -- '*.kt' '*.kts' | awk '{print $NF}')
[ -z "$changed" ] && exit 0

state_file=".claude/.last-passing-tests"
hash=$( { git diff HEAD -- '*.kt' '*.kts' 2>/dev/null; for f in $changed; do [ -f "$f" ] && cat "$f"; done; } | sha1sum | cut -d' ' -f1)
[ -f "$state_file" ] && [ "$(cat "$state_file")" = "$hash" ] && exit 0

if [ -x ./gradlew ] && [ "$OS" != "Windows_NT" ]; then gradle=./gradlew; else gradle=./gradlew.bat; fi
output=$("$gradle" test --console=plain 2>&1)
status=$?

if [ $status -eq 0 ]; then
    echo "$hash" > "$state_file"
    echo '{"systemMessage": "Unit tests passed"}'
    exit 0
fi

# Already blocked once this turn: report instead of looping forever.
if echo "$input" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
    echo '{"systemMessage": "Unit tests still failing - see ./gradlew test"}'
    exit 0
fi

summary=$(echo "$output" | grep -E 'FAILED|e: |error:|Exception|tests completed' | head -40)
{
    echo "Unit tests failed after your code changes (./gradlew test). Fix the code or tests, then finish:"
    echo "$summary"
} >&2
exit 2
