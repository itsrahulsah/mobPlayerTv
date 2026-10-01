# MobPlayer TV - Server API Contracts Directory

This directory contains individual API contract specifications for every REST endpoint, progressive streaming route, and WebSocket protocol hosted by the **MobPlayer TV** embedded server (`Ktor/CIO` on port `8080`).

---

## Server Architecture & Addressing

| Property | Value | Notes |
| :--- | :--- | :--- |
| **Server Engine** | Ktor CIO (`io.ktor:ktor-server-cio:2.3.9`) | Coroutine-based asynchronous non-blocking I/O |
| **Default Port** | `8080` | Broadcast via Android NSD mDNS (`_mobplayer._tcp.`) |
| **Base URL** | `http://<TV_IP_ADDRESS>:8080` | Local network Wi-Fi / Ethernet |
| **WebSocket URL** | `ws://<TV_IP_ADDRESS>:8080/control` | Full-duplex JSON remote control channel |
| **Cleartext HTTP** | Supported (`usesCleartextTraffic="true"`) | Configured via `network_security_config.xml` |

---

## API Contract Index

| # | Endpoint | Method / Protocol | Contract Document | Description |
| :---: | :--- | :---: | :--- | :--- |
| 1 | `/api/upload` | `POST` | [POST_api_upload.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/POST_api_upload.md) | Upload media via direct raw binary stream or multipart with < 0.5s instant progressive playback. |
| 2 | `/api/stream/{uploadId}` | `GET` | [GET_api_stream_uploadId.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/GET_api_stream_uploadId.md) | Progressive HTTP Range-capable stream for playing growing files as they upload. |
| 3 | `/api/videos` | `GET` | [GET_api_videos.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/GET_api_videos.md) | List all stored videos with metadata, duration, and watch progress. |
| 4 | `/api/videos/{fileName}` | `GET` | [GET_api_videos_fileName.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/GET_api_videos_fileName.md) | Stream or download a completed video file with seekable Range support. |
| 5 | `/api/videos/{id}/play` | `POST` | [POST_api_videos_id_play.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/POST_api_videos_id_play.md) | Play or resume a stored video from its last-played timestamp. |
| 6 | `/api/videos/{id}` | `DELETE` | [DELETE_api_videos_id.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/DELETE_api_videos_id.md) | Delete a specific stored video and its persisted metadata. |
| 7 | `/api/videos` | `DELETE` | [DELETE_api_videos.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/DELETE_api_videos.md) | Clear all stored videos and reset the TV library. |
| 8 | `/control` | `WebSocket` | [WS_control.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/WS_control.md) | PIN pairing, token auth, remote control commands, and real-time ExoPlayer state broadcast. |
| 9 | `/api/{...}` | `OPTIONS` | [OPTIONS_cors.md](file:///Users/ojasmac_8/Do-something/MobPlayer%20Tv/api_contracts/OPTIONS_cors.md) | CORS preflight options handler for web browser controllers. |

---

## Authentication & Security Model

- **WebSocket Control (`/control`)**:
  - Unauthenticated clients receive a `PIN_REQUIRED` prompt.
  - The client submits the 4-digit PIN displayed on the TV screen.
  - Upon successful PIN validation, the server generates a long-lived UUID auth token stored in Android `EncryptedSharedPreferences`.
  - Clients reuse this token in subsequent `AUTH_REQUEST` payloads to bypass PIN entry.
- **REST Endpoints (`/api/*`)**:
  - Accept `X-Auth-Token` in HTTP headers or `?token=` query parameter.
  - Public endpoints (`GET /api/stream/{uploadId}` and `GET /api/videos/{fileName}`) permit direct playback from ExoPlayer and media renderers without headers.
