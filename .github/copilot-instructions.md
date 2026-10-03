Follow the rules in [AGENTS.md](../AGENTS.md) at the repo root. In short:

- Every feature, bug fix or behaviour change must include new or updated tests: JVM unit tests with MockK in `app/src/test/`, device tests (ExoPlayer, Ktor server, Compose UI) in `app/src/androidTest/`.
- After changing any `.kt`/`.kts` file, run `./gradlew test` and fix failures before finishing. Run `./gradlew connectedDebugAndroidTest` too when touching server, playback, auth or UI code and a device is connected.
- Never delete, skip or weaken a failing test to make it pass, and never commit with `--no-verify`.
