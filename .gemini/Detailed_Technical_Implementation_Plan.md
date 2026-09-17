# Detailed Technical Implementation Plan: Android TV App

This document expands on the `Android_TV_App_Development_Plan.md` and provides specific technical details, architecture decisions, and code structures required to implement the Android TV application.

## System Architecture Overview
- **Pattern:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow.
- **Asynchrony:** Kotlin Coroutines and Kotlin Flow (`StateFlow` / `SharedFlow`).
- **UI Toolkit:** Jetpack Compose for TV (recommended for modern Android TV apps) or Android Leanback.
- **Dependency Injection:** Hilt (Dagger) for managing lifecycles of the Ktor server, ExoPlayer, and Repositories.

---

## Phase 1: Project Setup & Architecture

### 1.1 Initialization
- Create an **Android TV** specific module or configure the main app module with `<uses-feature android:name="android.software.leanback" android:required="false" />` and `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`.
- Configure `minSdkVersion 24` (or higher, depending on Ktor/Media3 requirements).

### 1.2 Dependencies (`build.gradle.kts`)
```kotlin
// Core & Architecture
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("com.google.dagger:hilt-android:2.51")

// Ktor Server (WebSockets & Networking)
val ktorVersion = "2.3.9"
implementation("io.ktor:ktor-server-core:$ktorVersion")
implementation("io.ktor:ktor-server-cio:$ktorVersion")
implementation("io.ktor:ktor-server-websockets:$ktorVersion")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

// Media3 (ExoPlayer & MediaSession)
val media3Version = "1.3.0"
implementation("androidx.media3:media3-exoplayer:$media3Version")
implementation("androidx.media3:media3-session:$media3Version")
implementation("androidx.media3:media3-ui:$media3Version")

// Security
implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
```

---

## Phase 2: Core Server & Background Execution

### 2.1 Ktor WebSocket Server
- Create a `KtorServerManager` singleton or Hilt-provided component.
- Configure `embeddedServer(CIO, port = 8080)`.
- Use the `WebSockets` plugin to handle `ws://<ip>:8080/control`.

### 2.2 Android Foreground Service
- Create `WebSocketServerService : Service()`.
- **Permissions Required:** 
  - `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` (or `MEDIA_PLAYBACK` depending on primary use case).
- On `onCreate`/`onStartCommand`, start the foreground notification and start the Ktor server in an IO coroutine scope.

### 2.3 Data Models
```kotlin
@Serializable
data class WebSocketMessage(
    val type: String, // "AUTH", "COMMAND", "STATE_UPDATE"
    val payload: String // JSON string of the specific payload
)

@Serializable
data class PlayerStatePayload(val isPlaying: Boolean, val positionMs: Long, val volume: Float)

@Serializable
data class PlayerCommandPayload(val action: String, val seekToMs: Long? = null) // "PLAY", "PAUSE", "SEEK"
```

---

## Phase 3: Network Discovery & Resilience

### 3.1 Network Service Discovery (NSD)
- Use `android.net.nsd.NsdManager`.
- Create an `NsdRegistrationInfo` with service name (e.g., "MobPlayer TV") and service type `_http._tcp.` (or a custom protocol like `_mobplayer._tcp.`).
- Call `nsdManager.registerService()` upon server start and unregister upon destruction.

### 3.2 & 3.3 Network State Listener
- Implement `ConnectivityManager.NetworkCallback`.
- Override `onAvailable` and `onLost`.
- When network changes (e.g., Ethernet -> WiFi), stop the Ktor server, fetch the new local IP address, restart the Ktor server, and re-register the NSD service with the new configuration.

---

## Phase 4: Authentication & Connection Management

### 4.1 PIN Generation & Verification
- When a client connects via WebSocket without a valid token, generate a random 4-digit PIN.
- Send a local broadcast or update a `StateFlow` to the UI to display the PIN Dialog.
- The client sends the PIN back over the WebSocket. If it matches, authentication succeeds.

### 4.2 Secure Token Storage
- Upon successful PIN match, generate a UUID (token).
- Save to `EncryptedSharedPreferences`: `sharedPrefs.edit().putString("auth_token", token).apply()`.
- Send the token to the client to use for future connections.

### 4.3 & 4.4 Single Active Controller Connection
- In `KtorServerManager`, maintain a reference to the active session: `var activeSession: DefaultWebSocketServerSession? = null`.
- If `activeSession` is not null and a new client connects (and authenticates):
  - Pause the connection process.
  - Trigger a UI Dialog: "Device [Name] is trying to connect. Disconnect current controller?"
  - If user selects "Allow": call `activeSession?.close()`, set new connection as `activeSession`.
  - If user selects "Reject": close the new connection.

---

## Phase 5: Media Integration & State Sync

### 5.1 Media3 Setup
- Initialize `ExoPlayer` instance.
- Wrap it in a `MediaSession`: `MediaSession.Builder(context, player).build()`.
- Attach `PlayerView` (or Compose AndroidView) to the `ExoPlayer`.

### 5.2 Mapping WebSocket Commands
- In the Ktor routing block, collect incoming messages.
- Parse `PlayerCommandPayload`.
- Map to ExoPlayer:
  - "PLAY" -> `player.play()`
  - "PAUSE" -> `player.pause()`
  - "SEEK" -> `player.seekTo(payload.seekToMs)`
  - Run these commands on the Main thread using `withContext(Dispatchers.Main)`.

### 5.3 & 5.4 State Broadcast
- Implement `Player.Listener` on the `ExoPlayer`.
- Override `onIsPlayingChanged`, `onPlaybackStateChanged`, `onVolumeChanged`.
- When state changes, construct `PlayerStatePayload`, serialize it to JSON.
- Launch a coroutine to send the payload to `activeSession?.send(Frame.Text(json))`.

---

## Phase 6: Testing & Quality Assurance

### 6.1 Unit Tests
- Use `mockk` to mock ExoPlayer interfaces.
- Test ViewModels and pure Kotlin logic without Android context.
- Use Ktor's `testApplication` engine to simulate WebSocket connections without opening actual ports.

### 6.2 UI Tests
- Use Jetpack Compose UI Testing (`composeTestRule`) or Espresso (for Views).
- Test the appearance of the PIN dialog and the Authorization Prompt.

### 6.3 & 6.4 Integration & Latency
- Write instrumentation tests that spin up the actual local server.
- Measure the time from sending a WebSocket text frame to the `Player.Listener` firing a state change. Assert time < 100ms.

### 6.5 Local-Only Verification
- Run tests on a device with Airplane mode enabled (but WiFi connected to an isolated local router) to guarantee no external cloud calls are made.
