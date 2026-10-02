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

## Video Upload, Instant Progressive Streaming & Universal Media Support
- **Universal Container & Protocol Support**:
  - Expanded Media3 dependencies to include `media3-exoplayer-hls`, `media3-exoplayer-dash`, and `media3-exoplayer-rtsp`.
  - Dynamic container and MIME type resolution via `VideoMetadata.resolveMimeType()` supporting MP4, MKV, WebM, MPEG-TS, AVI, MOV, FLV, WMV, 3GP, Ogg, HLS (.m3u8), DASH (.mpd), RTSP, and all common audio formats (MP3, AAC, FLAC, WAV, Opus).
  - Cleartext HTTP traffic permitted via `network_security_config.xml` and `android:usesCleartextTraffic="true"` for local-network streaming.

- **Instant Progressive Playback (< 0.5s Start)**:
  - **Aggressive Fast-Start LoadControl**: Configured `DefaultLoadControl` in `MediaRepository.kt` with `bufferForPlaybackMs = 250ms`, `bufferForPlaybackAfterRebufferMs = 500ms`, and `prioritizeTimeOverSizeThresholds = true`. ExoPlayer transitions to `STATE_READY` and renders video immediately with only a fraction of a second buffered.
  - **64 KB Initial Playback Trigger**: Playback begins when `bytesWritten >= 65536L` (or half of total size for tiny files), cutting startup latency to < 500ms.
  - **Dual Upload Support**: `POST /api/upload` handles both direct raw binary streams (`call.receiveChannel()`) with zero multipart overhead and standard `multipart/form-data`.
  - **Non-Interrupting Completion Handler**: Completed uploads no longer reset or abort active progressive streams. ExoPlayer plays seamlessly through the end of the video without interruption.
  - **Client-Side Fast-Start Optimization**: `test_client.html` features in-browser zero-copy `ensureFastStart()` that dynamically relocates the `moov` atom ahead of `mdat` in MP4 files using `Blob.slice()` and patches `stco`/`co64` chunk offsets before streaming.

## Video Upload & Playback Resume REST Endpoints
- `POST /api/upload`: Direct binary stream or multipart upload with `playImmediately`, `title`, `fileName`, and instant progressive streaming.
- `GET /api/stream/{uploadId}`: Progressive HTTP Range-capable stream for playing growing files with automatic EOF resolution.
- `GET /api/videos`: List all stored videos with metadata and watch progress.
- `GET /api/videos/{fileName}`: Direct streaming / downloading of stored video.
- `POST /api/videos/{id}/play`: Play / Resume video at saved position.
- `DELETE /api/videos/{id}` / `DELETE /api/videos`: Storage management and cleanup.


## YouTube Integration (Phase 9)
- **Feed & search**: `youtubecrawler` Gradle library module + `YouTubeRepository` provide the home feed, categories and search. Remote text (`TEXT_INPUT` / `TEXT_SUBMIT`) opens the Search tab and mirrors the phone's text into the search field.
- **Playback**: `SmartTubePlayerEngine` (initialised in `MobPlayerTvApp.onCreate`) uses the SmartTube AARs in `app/libs/` to decipher streams. Source priority: HLS for live → DASH MPD built from adaptive formats → HLS URL → DASH URL → progressive URL. Media sources use a `DefaultHttpDataSource` with the SmartTube user agent and 20 s timeouts.
- **Shared player**: YouTube playback goes through `MediaRepository.loadMediaSource()`, the same ExoPlayer used for uploads, so remote play/pause/seek/volume work for both.
- **Key routing**: `ServerRepository.isUiNavigating` is true while the user browses UI over a playing video; `KtorServerManager.playerHandlesKeys()` then sends Left/Right/OK/Back to the UI instead of the player.

## Upload Streaming: MKV Timeout Fix
- **Symptom**: Web client → select video → start streaming of a ~600 MB MKV failed with `ExoPlaybackException: Source error … SocketTimeoutException: timeout` (stack: `ProgressiveMediaPeriod` → `MatroskaExtractor` → `DefaultHttpDataSource`).
- **Cause**: The player reads `http://127.0.0.1:<port>/api/stream/{uploadId}` while the file is still being written; the server holds the response until the requested bytes arrive. Large MKVs store their Cues (seek index) at the end of the file, and `MatroskaExtractor` seeks there first, so the server waits for almost the whole upload. ExoPlayer's default 8 s read timeout fired and retries ran out.
- **Fix** (`MediaRepository.buildGrowingUploadSource`, used only for localhost `/api/stream/` URLs): `MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES`, 60 s read timeout, `DefaultLoadErrorHandlingPolicy(30)` retries. Stored files (`file://`), YouTube and phone-cast URLs are unchanged.
- **Trade-offs**: an MKV played this way can't be seeked until it is replayed from storage; MP4s with `moov` at the end still wait for nearly the full upload (the client-side `ensureFastStart()` helps); uploads slower than the video bitrate cause rebuffering.

## Keep Screen On During Playback
- `MainActivity` adds `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` while `isPlayerActive && playerState.isPlaying`, and clears it when paused, stopped or the player closes. Applies to both the regular player and the YouTube player.
- **Known gap**: buffering (`isPlaying == false`) does not hold the screen on, so a stall longer than the TV's sleep timeout (e.g. slow upload stream) can still let it sleep.

## Player Takeover & Minimise Rules (Phase 9 review fixes)
- **Takeover detection**: `MediaRepository.loadCount` increments on every `loadMedia` / `loadMediaSource`. Phone URL casts load with no media id, so `currentMediaId` alone can't detect them (and `activePlayingVideoId` drives upload resume progress, so casts must not get a fake id). `YouTubePlayerViewModel` stores `expectedLoadCount` (set in `play()` and just before its own load); any other load dismisses the YouTube screen and cancels the pending lookup. `currentMediaId` going null while playing still means "stopped elsewhere".
- **Minimise** (`isPlayerMinimized`, `rememberSaveable`): typing from the phone minimises the player behind Search. It is restored when `isPlayerActive`/title change or a new load arrives, except the open YouTube video's own load (`YouTubePlayerViewModel.isOwnLoad`). Last key and load count are saved so a configuration change doesn't un-minimise.
- **Screen transition**: `AnimatedContent` target is `(playerOpen, youTubeStateOrNull)` keyed only on `playerOpen`, so a closing YouTube screen fades out with its last state and YouTube changes while minimised don't re-key the browse screen.
- **Remote key routing**: `isUiNavigating` = minimised || "Up next" open || YouTube error panel showing (`onErrorVisibleChange`). Key-injection fallback sends Back through `onBackPressedDispatcher` (a raw Back key would become a Compose focus move), so the YouTube screen also has a `BackHandler` that closes "Up next" first.
- **Pairing dialog** (`TvModalOverlay` with `onBack`): a disconnect can show it over a playing video; Back closes that player first, then `finish()` when nothing is playing.

## YouTube Stream Lookup Notes
- `SmartTubePlayerEngine.resolvePlaybackSource` runs on IO; its library calls block and ignore cancellation, so `ensureActive()` is checked between strategies, at each fallback attempt and before `switchNextClientNow()` (the client is shared global state).
- `buildMediaSource` (DASH manifest parse) runs on `Dispatchers.Default`.
- InnerTube lockups: only `LOCKUP_CONTENT_TYPE_VIDEO` → `CompactVideo`, `..._PLAYLIST` → `CompactPlaylist`, others skipped.

## Device Decoder Limits
- Single-track files (uploads/casts) are played even if the decoder reports `NO_EXCEEDS_CAPABILITIES`, then fail mid-decode (seen: 4K H.264 High@5.1 on `OMX.MS.AVC.Decoder`). YouTube adaptive streams avoid this because the track selector skips unsupported renditions. Decoder fallback doesn't help (it only applies at decoder init).
