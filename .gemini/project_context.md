# MobPlayer TV - Project Context

## Project Overview
MobPlayer TV is a local-network Android TV application. It acts as a headless media player (using AndroidX Media3/ExoPlayer) controlled entirely by a companion mobile app over the local Wi-Fi network via WebSockets. It requires absolutely no internet access or cloud dependency for remote control logic.

## Architecture & Core Components

### 1. Network Discovery (`NsdHelper.kt`)
- Uses Android's `NsdManager` (Multicast DNS / Bonjour) to broadcast a service named **"MobPlayer TV"** over `_http._tcp.`.
- Mobile clients scan the network for this signature to automatically resolve the TV's local IP address and port (8080) without user input.

### 2. WebSocket Server (`KtorServerManager.kt` & `WebSocketServerService.kt`)
- **Server Engine**: Runs a Ktor `CIO` engine listening on `0.0.0.0:8080`. 
- **Service Wrapper**: Wrapped in an Android Foreground Service (`WebSocketServerService`) so the OS doesn't kill the server when the user minimizes the app.
- **Connection Rules**:
  - Enforces a 1-active-controller limit.
  - Requires PIN authentication for new devices.
  - Returns a `COMMAND_SUCCESS` response when media commands are executed.

### 3. Authentication (`AuthManager.kt`)
- Generates random 4-digit PINs.
- Validates PINs.
- Generates and securely stores long-lived auth tokens using AndroidX Security `EncryptedSharedPreferences`. Returning mobile clients use this token to bypass the PIN screen.

### 4. Media Playback (`MediaManager.kt`)
- Wraps `ExoPlayer` and `MediaSession`.
- Listens to internal player state changes (isPlaying, currentPosition, volume) and streams them out via a Kotlin `StateFlow`.
- The `KtorServerManager` collects this flow and pushes it down the WebSocket as a `STATE_UPDATE` JSON payload in real-time.

### 5. UI & State Bus (`MainActivity.kt` & `ServerEventBus.kt`)
- Built entirely in Jetpack Compose for TV.
- `ServerEventBus` acts as a unidirectional state bridge between the background server Service and the foreground UI.
- The UI listens for events like `ConnectionEvent.PinRequested` or `ConnectionEvent.ConnectionConflict` and overlays transparent dialogs on top of the ExoPlayer `AndroidView` surface.

## JSON WebSocket API Protocol
- **Client -> Server**:
  - `{"type": "AUTH_REQUEST", "payload": "<token>"}`
  - `{"type": "PIN_SUBMIT", "payload": "<4-digit-pin>"}`
  - `{"type": "COMMAND", "payload": "{\"action\": \"PLAY\"}"}`
  - `{"type": "COMMAND", "payload": "{\"action\": \"PAUSE\"}"}`
  - `{"type": "COMMAND", "payload": "{\"action\": \"SEEK\", \"seekToMs\": 15000}"}`
  - `{"type": "LOAD_MEDIA", "payload": "<url>"}`
- **Server -> Client**:
  - `{"type": "PIN_REQUIRED", "payload": ""}`
  - `{"type": "AUTH_SUCCESS", "payload": "<new-token>"}`
  - `{"type": "AUTH_FAILED", "payload": "Invalid PIN"}`
  - `{"type": "COMMAND_SUCCESS", "payload": "<action>"}`
  - `{"type": "STATE_UPDATE", "payload": "{\"isPlaying\": true, \"positionMs\": 12000, \"volume\": 1.0}"}`

## State of the Project (As of completion)
- All 6 phases of the original `Android_TV_App_Development_Plan.md` are **COMPLETED**.
- The app builds successfully and runs properly on Android TV emulators (API 34).
- The `BindException` caused by network switches has been resolved.
- FGS (Foreground Service) permissions for Android 14 have been fixed.
- SLF4J logger implementation has been added.
- **Minimum SDK bumped to 26**: Obsolete API version checks (`Build.VERSION.SDK_INT >= O`) have been removed.
- **Unstable APIs Removed**: Replaced experimental `androidx.tv` Material 3 components with standard Compose `MaterialTheme` and removed `DefaultLoadControl` custom `@UnstableApi` in `MediaManager`.
- **Media Polling**: `MediaManager` now correctly polls `player.currentPosition` every 1 second while playing, which automatically broadcasts updates to the TV UI and WebSocket clients via `playerStateFlow`.

## Architecture & Core Components (Hilt DI)
The project uses Dagger Hilt with constructor injection and clean repository pattern:
1. **Network Discovery (`NsdHelper.kt`)**: Broadcasts service via Android `NsdManager`.
2. **WebSocket Server (`KtorServerManager.kt` & `WebSocketServerService.kt`)**: Embedded Ktor CIO server on port 8080 handling WebSocket control commands and REST endpoints.
3. **Authentication (`AuthManager.kt`)**: Generates 4-digit PINs, validates tokens via `EncryptedSharedPreferences`.
4. **Media Playback (`MediaRepository.kt`)**: Wraps Media3/ExoPlayer, emits `playerStateFlow`, handles playback actions and progress polling.
5. **Video Upload & Storage (`VideoUploadManager.kt`)**: 
   - Manages local video storage in `context.filesDir/uploads/`.
   - Handles progressive streaming (`ActiveUpload`) allowing ExoPlayer to start playing growing files within 1–2 seconds via `/api/stream/{uploadId}` while the remainder uploads in the background.
   - Manages persistent metadata (`VideoMetadata`) including custom title, file size, duration, and last-played timestamp (`lastPlayedPositionMs`).
   - Supports auto-resume: restores playback position when replaying stored videos.
6. **UI & State (`MainActivity.kt`, `TvHomeScreen.kt`, `TvMainViewModel.kt`)**:
   - Built with Jetpack Compose for TV.
   - `TvHomeScreen.kt` displays content rails, including an "Uploaded Videos" rail using `CardType.CONTINUE_WATCHING` showing video titles and watch progress bars.
   - `TvVideoPlayerOverlay.kt` renders player timeline and controls.
   - `TvRemoteActionHud.kt` displays floating action pills on the TV screen.

## Video Upload & Playback Resume REST Endpoints
- `POST /api/upload`: Multipart upload with `playImmediately`, `title`, and progressive streaming.
- `GET /api/stream/{uploadId}`: Progressive HTTP Range-capable stream for playing growing files.
- `GET /api/videos`: List all stored videos with metadata and watch progress.
- `GET /api/videos/{fileName}`: Direct streaming / downloading of stored video.
- `POST /api/videos/{id}/play`: Play / Resume video at saved position.
- `DELETE /api/videos/{id}` / `DELETE /api/videos`: Storage management and cleanup.

