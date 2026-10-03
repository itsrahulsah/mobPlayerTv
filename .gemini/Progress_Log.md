# Development Progress Log

## Phase 1: Project Setup & Architecture
**Status: COMPLETED**
- Initialized Android TV project using Kotlin.
- Added all necessary dependencies (`Ktor`, `Media3`, `Compose TV`, `Serialization`).
- Created base UI (`MainActivity.kt`) and `AndroidManifest.xml`.
- Successfully ran Gradle sync and verified the build.

---

## Phase 2: Core Server & Background Execution
**Status: COMPLETED**
- **Task 2.3:** Implementing Data Models (`WebSocketModels.kt`) for serialization.
- **Task 2.1:** Implementing the Ktor WebSocket Server (`KtorServerManager.kt`).
- **Task 2.2:** Wrapping the server in an Android Foreground Service (`WebSocketServerService.kt`).

---

## Phase 3: Network Discovery & Resilience
**Status: COMPLETED**
- **Task 3.1:** Implement Android Network Service Discovery (NSD) (`NsdHelper.kt`).
- **Task 3.2:** Create a network state listener (`NetworkStateMonitor.kt`).
- **Task 3.3:** Wire network changes to auto-restart the Ktor Server and NSD broadcast.

## Phase 4: Authentication & Connection Management
**Status: COMPLETED**
- **Task 4.1 & 4.2:** PIN generation and Token storage via `EncryptedSharedPreferences` (`AuthManager.kt`).
- **Task 4.3:** Single active controller logic in `KtorServerManager`.
- **Task 4.4:** Jetpack Compose UI for displaying the PIN and Connection Prompts.

## Phase 5: Media Integration & State Sync
**Status: COMPLETED**
- **Task 5.1:** Set up Media3 ExoPlayer and `MediaSession` (`MediaManager.kt`).
- **Task 5.2:** Route WebSocket commands to `ExoPlayer`.
- **Task 5.3 & 5.4:** Observe Player state changes and broadcast to the connected client.

## Phase 6: Testing & Quality Assurance
**Status: COMPLETED**

---

## Phase 7: Local Video Upload, Metadata & Playback Resume
**Status: COMPLETED**
- **Task 7.1:** Design and implement `VideoMetadata.kt` model with title, size, duration, and `lastPlayedPositionMs`.
- **Task 7.2:** Implement `VideoUploadManager.kt` for local storage in `context.filesDir/uploads`, metadata persistence, and progressive growing-file buffer tracking (`ActiveUpload`).
- **Task 7.3:** Implement Ktor REST routes in `KtorServerManager.kt`:
  - `POST /api/upload`: multipart upload with progressive instant streaming.
  - `GET /api/stream/{uploadId}`: HTTP Range progressive stream for ExoPlayer.
  - `GET /api/videos`: list stored videos with watch progress.
  - `POST /api/videos/{id}/play`: play/resume video.
  - `DELETE /api/videos/{id}`: delete video and metadata.
- **Task 7.4:** Wire `MediaRepository.kt` to track current position and update `lastPlayedPositionMs` in `VideoUploadManager`.
- **Task 7.5:** Update `TvHomeScreen.kt` to display an "Uploaded Videos" rail using `CardType.CONTINUE_WATCHING` showing custom title, thumbnail, and watch progress bar.
- **Task 7.6:** Update `test_client.html` with video file picker, editable title, fast-start detection, live upload progress, and stored videos library with `Resume` and `Start Over` buttons.
- **Task 7.7:** Unit tests for `VideoUploadManager` and progressive stream tracking.

---

## Phase 8: Instant Progressive Streaming (< 0.5s) & Universal Media Formats
**Status: COMPLETED**
- **Task 8.1:** Configured aggressive fast-start `DefaultLoadControl` in `MediaRepository.kt` (`bufferForPlaybackMs = 250`, `bufferForPlaybackAfterRebufferMs = 500`, `prioritizeTimeOverSizeThresholds = true`). ExoPlayer transitions to `READY` in < 300 ms.
- **Task 8.2:** Lowered progressive stream start trigger threshold from 3MB/256KB to 64 KB in `KtorServerManager.kt`.
- **Task 8.3:** Fixed upload completion bug: updated completion handler to not interrupt or reset ongoing progressive streams when upload completes while the player is buffering.
- **Task 8.4:** Added direct raw binary streaming upload via `call.receiveChannel()` for zero-overhead streaming alongside multipart form-data.
- **Task 8.5:** Expanded media container and protocol support across all Media3/ExoPlayer supported formats (MP4, MKV, WebM, TS, AVI, MOV, FLV, HLS, DASH, RTSP, and audio codecs) with dynamic MIME type detection in `VideoMetadata.resolveMimeType()`.
- **Task 8.6:** Added `network_security_config.xml` and `android:usesCleartextTraffic="true"` to permit cleartext HTTP traffic for local network and loopback streaming.
- **Task 8.7:** Added client-side in-browser MP4 fast-start `moov` atom relocation (`ensureFastStart()`) in `test_client.html` with chunk offset patching (`stco`/`co64`) and direct binary XHR streaming.


---

## Phase 9: YouTube Integration, Remote Keyboard & Playback Fixes
**Status: COMPLETED** (merged to `master` as PR #5, `07f666f`, 2026-10-03)
- **Task 9.1:** YouTube home feed, category bar, Search tab and recent searches (`youtubecrawler` module, `YouTubeRepository`, `YouTubeFeedViewModel`, `YouTubeSearchViewModel`, `TvYouTubeCategoryBar`, `TvYouTubeSearchBar`).
- **Task 9.2:** YouTube playback via vendored SmartTube MediaServiceCore AARs in `app/libs/` (~36 MB, un-ignored with `!app/libs/*.aar`). `SmartTubePlayerEngine` resolves DASH MPD / HLS / DASH URL / progressive sources; `TvYouTubePlayerScreen` + `YouTubePlayerViewModel` play them on the shared `MediaRepository` ExoPlayer so the phone remote keeps working.
- **Task 9.3:** Remote keyboard: `TEXT_INPUT` / `TEXT_SUBMIT` WebSocket messages mirror phone text into the TV's YouTube search field (documented in `APP_CONTROL_CONTRACT.md` §6.3, added to `test_client.html`).
- **Task 9.4:** `ServerRepository.isUiNavigating` lets remote D-pad/OK/Back navigate UI over playing media instead of always seeking / toggling / closing the player.
- **Task 9.5:** `AuthManager` creates `EncryptedSharedPreferences` lazily (Keystore setup took ~2 s on the main thread on low-end TVs).
- **Task 9.6:** Replaced `material-icons-extended` with a small local icon set (`ui/icons/ExtendedIcons.kt`); removed `MockMediaRepository` and the Movies / Shows / Live TV tabs.
- **Task 9.7 (fix):** `SocketTimeoutException` when streaming a large (~600 MB) MKV during upload. See "Upload Streaming: MKV Timeout Fix" in `project_context.md`.
- **Task 9.8 (fix):** TV display slept during playback. `MainActivity` now sets `FLAG_KEEP_SCREEN_ON` while a video is playing.
- **Task 9.9 (review fixes, before merge):**
  - Phone casts / uploads now replace a YouTube video even while it is loading or on its error screen (`MediaRepository.loadCount` takeover detection).
  - Phone remote drives the YouTube error panel (Retry/Back) instead of seeking or toggling playback.
  - Stream lookup fallback loop checks for cancellation, so an abandoned lookup stops rotating the shared YouTube client; DASH manifest is built off the main thread.
  - Minimised player: survives configuration changes, comes back for any new load (including same-title casts), but not for the open YouTube video's own stream finishing.
  - YouTube player fades out as itself on close (no flash of the regular player).
  - Search: spinner no longer sticks when a running search is cancelled; recent-search chips use `recent_` keys, line breaks are stripped and recents de-duplicated on load (duplicate keys crashed the list).
  - Only `LOCKUP_CONTENT_TYPE_VIDEO` lockups become video cards (albums/podcasts failed with "No playable stream found").
  - Back on the pairing dialog closes a playing video first and only exits the app when nothing is playing.
  - Back via the phone's key-injection fallback closes the "Up next" row first.

## Phase 10: YouTube Autoplay
**Status: IN PROGRESS** (branch `feature-and-flow-fix`)
- **Task 10.1:** When a YouTube video ends, the next "Up next" suggestion plays automatically. See "YouTube Autoplay" in `project_context.md`.
- **Task 10.2:** Persistent YouTube watch history (`WatchHistoryStore`), shown as a "Watch history" row below "Up next" in the player. See "YouTube Watch History" in `project_context.md`.

## Open Items
- **4K H.264 on low-end TV decoders**: e.g. a 3840×1728 `avc1.640033` (High@5.1) upload fails on `OMX.MS.AVC.Decoder` with `NO_EXCEEDS_CAPABILITIES`. Hardware limit, not an app bug; the file must be re-encoded (1080p H.264 or HEVC). Possible improvement: show a readable message on the phone and TV error screen instead of the raw ExoPlayer text.
