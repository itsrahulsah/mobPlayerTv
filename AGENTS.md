# Agent Instructions: MobPlayer TV

These rules apply to every coding agent working in this repo (Claude Code, Gemini, Copilot, Cursor, Codex, ...) and to humans.

## Tests are required for every change

Every feature, bug fix or behaviour change must come with tests in the same change:

- **New code** gets new tests. **Modified code** gets its existing tests updated, plus a test for the new or fixed behaviour (for a bug fix, a test that fails without the fix).
- **Unit tests** (`app/src/test/`, JVM): ViewModels, repositories, storage, models, pure logic. Mock collaborators with MockK (`mockk`, `coEvery`, `mockkObject` for Kotlin objects such as `SmartTubePlayerEngine`, `mockkStatic(Log::class)` where Android logging is hit). Use `MainDispatcherRule` + `runTest` for `viewModelScope` code. Helpers live in `app/src/test/java/com/mobplayer/tv/testutil/` (`FakeSharedPreferences`, `youTubeItem(s)`).
- **Instrumentation tests** (`app/src/androidTest/`): anything needing a real device: ExoPlayer/`MediaRepository` (call it on the main thread), the Ktor server routes and `/control` WebSocket (`KtorServerManagerTest` pattern: real server on a free port), `AuthManager`, and Compose UI (`createComposeRule`).
- Follow the naming and structure of the neighbouring test class; keep the README test table (`## Testing`) counts current.
- Don't delete, skip or weaken a failing test to make it pass; fix the code, or explain why the expectation changed.

## Run the tests after every code change

- After changing any `.kt`/`.kts` file, run `./gradlew test` (Windows: `gradlew.bat test`) and fix failures before you report the work as done. Report the result.
- When you change `androidTest/`, the server, media playback, auth or UI code, also run `./gradlew connectedDebugAndroidTest` if `adb devices` lists a device (~3 min). If none is connected, say that instrumentation tests were not run.

## Enforcement (applies to every agent)

- **Git pre-commit hook** (`.githooks/pre-commit`): blocks a commit with staged `.kt`/`.kts` changes if `./gradlew test` fails. Gradle installs it automatically (`installGitHooks`, runs before every build); to install by hand: `git config core.hooksPath .githooks`. Don't bypass it with `--no-verify`.
- **CI** (`.github/workflows/tests.yml`): runs the unit tests on every push and pull request.
- **Claude Code** additionally runs the unit tests at the end of each turn via `.claude/hooks/run-tests.sh`.

## Project context

Architecture, requirements and progress notes live in `.gemini/` (`project_context.md`, `Progress_Log.md`, `requirements.md`). Save new context and plans there, matching the existing style.
