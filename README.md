<p align="center">
  <img src="docs/images/banner.jpg" alt="MobPlayer TV — your phone is the remote, your TV is the player" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20TV-3DDC84?logo=android&logoColor=white" alt="Android TV">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin + Compose">
  <img src="https://img.shields.io/badge/Player-Media3%20ExoPlayer-FF0000" alt="Media3 ExoPlayer">
  <img src="https://img.shields.io/badge/Server-Ktor%20WebSockets-087CFA?logo=ktor&logoColor=white" alt="Ktor">
  <img src="https://img.shields.io/badge/minSdk-26-blue" alt="minSdk 26">
</p>

# MobPlayer TV

MobPlayer TV is an Android TV app that turns your TV into a media player you control from your phone or a web browser over the local network. It runs an embedded server on the TV. A phone or web controller finds the TV with mDNS, pairs with a 4-digit PIN, and then sends playback commands, videos and links in real time. Remote control does not depend on any cloud service.

The app can also browse, search and play YouTube, and it streams uploaded videos while they are still uploading.

---

## Screenshots

<table>
  <tr>
    <td width="50%"><img src="docs/images/home.jpg" alt="Home screen"><br><b>Home</b>: hero billboard, tabs and live controller status</td>
    <td width="50%"><img src="docs/images/pairing.jpg" alt="Pairing screen"><br><b>Pairing</b>: 4-digit PIN and the TV's server address</td>
  </tr>
  <tr>
    <td><img src="docs/images/youtube-feed.jpg" alt="YouTube feed"><br><b>YouTube feed</b>: Trending, Music, News, Movies and more</td>
    <td><img src="docs/images/youtube-search.jpg" alt="YouTube search"><br><b>Search</b>: type on the phone, results appear on the TV</td>
  </tr>
  <tr>
    <td><img src="docs/images/player.jpg" alt="Player"><br><b>Player</b>: timeline, ±10 s seek and "Up next"</td>
    <td><img src="docs/images/uploads.jpg" alt="Uploaded videos"><br><b>Uploaded videos</b>: files sent from the phone, stored on the TV</td>
  </tr>
</table>

### Web controller

Open `http://<TV_IP>:8080/` in any browser on the same network to get a full remote: pairing, D-pad, playback and volume, YouTube search, uploads, stream URLs and the stored-video library.

<p align="center">
  <img src="docs/images/web-controller.jpg" alt="Web controller" width="720">
</p>

---

## Features

### Phone / web remote control
- **Automatic discovery**: the TV advertises itself on the LAN as `MobPlayer TV` (`_mobplayer._tcp.`) using Android NSD (mDNS), so controllers find it without typing an IP address.
- **WebSocket control channel**: `ws://<TV_IP>:8080/control` handles play, pause, seek, volume, D-pad navigation and text input.
- **Live state sync**: the TV pushes playback state (playing/paused, position, volume) to the controller as `STATE_UPDATE` messages.
- **Text input from the phone**: typing on the phone fills the TV's YouTube search box.

### Secure pairing
- A 4-digit PIN appears on the TV the first time a device connects.
- After pairing, the device gets a long-lived token stored in `EncryptedSharedPreferences`, so it reconnects without the PIN.
- Only one controller can be active at a time. When a second device connects, the TV asks whether to **Allow** or **Reject** it.

### Upload & instant streaming
- **Play while uploading**: `POST /api/upload` accepts raw binary or `multipart/form-data` uploads. Playback starts after the first ~64 KB arrive, served through `GET /api/stream/{uploadId}`.
- **Fast-start buffering**: ExoPlayer is tuned for a quick first frame (250 ms initial buffer).
- **Large MKV support**: growing MKV uploads skip the end-of-file Cues seek and use longer timeouts, so big files don't time out.
- **Video library**: uploaded videos are saved on the TV with title, size, duration and watch progress, and appear in a "Continue Watching" rail.
- **Auto-resume**: stored videos resume from where you last stopped watching.
- **Library management**: list, play, delete or clear stored videos through the REST API.

### Wide format support
- Containers: MP4, MKV, WebM, MPEG-TS, AVI, MOV, FLV, WMV, 3GP, Ogg.
- Streaming protocols: HLS (`.m3u8`), DASH (`.mpd`), RTSP.
- Audio: MP3, AAC, FLAC, WAV, Opus.
- Cast any media URL from the phone (`LOAD_MEDIA`).

### YouTube
- Home feed, categories and search, powered by the bundled `youtubecrawler` module.
- Stream deciphering through the SmartTube MediaServiceCore libraries (`app/libs/`), with fallback from HLS for live, to DASH, to progressive streams.
- YouTube uses the same player as uploads, so the phone remote works the same way for both.

### TV-first UI
- Built entirely with Jetpack Compose and designed for D-pad focus navigation.
- Hero billboard, content rails, media cards, player overlay with timeline, and a remote-action HUD.
- The player can minimise to the background while you browse.
- The screen stays on during playback.

### Reliable background server
- The Ktor server runs inside a foreground service, so Android doesn't kill it.
- Network changes (Wi-Fi ↔ Ethernet, IP changes) are detected, and the server and mDNS broadcast restart automatically.

---

## Tech Stack

| Area | Technology |
| :--- | :--- |
| Language | Kotlin, Coroutines & Flow |
| UI | Jetpack Compose, Material 3, Coil |
| Playback | AndroidX Media3 (ExoPlayer, HLS, DASH, RTSP, MediaSession) |
| Server | Ktor 2.3 (CIO engine, WebSockets) |
| Serialization | kotlinx.serialization |
| DI | Dagger Hilt |
| Security | AndroidX Security (`EncryptedSharedPreferences`) |
| Discovery | Android NSD (mDNS) |
| YouTube | `youtubecrawler` module + SmartTube AARs |

**Requirements:** Android 8.0+ (minSdk 26), JDK 17.

---

## Project Structure

```
MobPlayerTv/
├── app/                      # Android TV application
│   ├── libs/                 # SmartTube AARs (YouTube deciphering)
│   └── src/main/java/com/mobplayer/tv/
│       ├── auth/             # PIN + token authentication
│       ├── network/          # NSD discovery, network monitoring
│       ├── server/           # Ktor HTTP/WebSocket server
│       ├── service/          # Foreground service hosting the server
│       ├── storage/          # Upload & progressive stream management
│       ├── repository/       # Media, server and YouTube repositories
│       ├── youtube/          # YouTube player engine
│       ├── viewmodel/        # ViewModels
│       └── ui/               # Compose screens & components
├── youtubecrawler/           # YouTube feed/search/stream extraction library
├── docs/images/              # README banner & screenshots
├── api_contracts/            # Per-endpoint API specifications
├── APP_CONTROL_CONTRACT.md   # Full controller protocol spec
├── test_client.html          # Browser-based test controller
└── forward-tv-port.{ps1,sh}  # Expose an emulator to the LAN
```

---

## Getting Started

### Build & install

```bash
# Windows: gradlew.bat
./gradlew installDebug
```

Or open the project in Android Studio and run the `app` configuration on an Android TV device or emulator.

### Connect a controller

1. Launch MobPlayer TV on the TV.
2. On a device on the same network, open `http://<TV_IP>:8080/` in a browser to load the built-in test controller (or use the companion mobile app).
3. Click **Connect** and enter the PIN shown on the TV.
4. Control playback, upload a video, or cast a URL.

### Using an emulator

An emulator isn't reachable from the LAN by default. Use the forwarding scripts to relay `LAN:18081 → Android:8080`:

```powershell
# Windows (Administrator PowerShell)
powershell -NoProfile -ExecutionPolicy Bypass -File .\forward-tv-port.ps1
```

```bash
# macOS (requires adb and socat)
bash ./forward-tv-port.sh
```

Then open `http://<PC_LAN_IP>:18081/` from your phone. mDNS discovery does not work through the relay.

---

## API Overview

| Endpoint | Method | Purpose |
| :--- | :---: | :--- |
| `/control` | WebSocket | Pairing, remote commands, live state |
| `/api/upload` | POST | Upload a video (optionally play immediately) |
| `/api/stream/{uploadId}` | GET | Progressive stream of an in-progress upload |
| `/api/videos` | GET | List stored videos with progress |
| `/api/videos/{fileName}` | GET | Stream/download a stored video |
| `/api/videos/{id}/play` | POST | Play or resume a stored video |
| `/api/videos/{id}` | DELETE | Delete one video |
| `/api/videos` | DELETE | Clear the library |

REST calls authenticate with an `X-Auth-Token` header or a `?token=` query parameter. See [`api_contracts/`](api_contracts/README.md) and [`APP_CONTROL_CONTRACT.md`](APP_CONTROL_CONTRACT.md) for full request/response details.

---

## Testing

```bash
./gradlew test                  # Unit tests (app + youtubecrawler)
./gradlew connectedAndroidTest  # Instrumentation tests (device/emulator)
```
