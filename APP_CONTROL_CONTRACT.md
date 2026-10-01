# MobPlayer TV - App Control Contract Specification

This document defines the network discovery, authentication, command execution, and real-time state synchronization protocol contract between client controller applications (Mobile/Web) and the **MobPlayer TV** application.

---

## 1. Overview & Architecture

MobPlayer TV acts as a WebSocket server running locally on the TV device (default port **8080**). Clients discover the TV on the local network via mDNS (NSD), authenticate using a 4-digit TV-displayed PIN or saved UUID token, and exchange control commands and state updates over a persistent WebSocket session.

```
+------------------+         mDNS / NSD Discovery        +-------------------+
|  Mobile / Web    | <=================================> |   MobPlayer TV    |
|  Controller App  |                                     |    (Host App)     |
+------------------+                                     +-------------------+
         |                                                         |
         | -------------- 1. WS Connect (ws://ip:8080/control) --->|
         | <------------- 2. AUTH_REQUEST / PIN_REQUIRED --------- |
         | -------------- 3. PIN_SUBMIT / Token Auth ------------->|
         | <------------- 4. AUTH_SUCCESS (Returns Token) -------- |
         |                                                         |
         | ======================================================= |
         |               Authenticated Control Session             |
         | ======================================================= |
         |                                                         |
         | -------------- COMMAND (PLAY / PAUSE / SEEK) ---------->|
         | -------------- LOAD_MEDIA (url) ----------------------->|
         | <------------- STATE_UPDATE (broadcast) ----------------|
```

---

## 2. Local Network Discovery (mDNS / NSD)

Clients discover MobPlayer TV devices on the local Wi-Fi network using standard **Android Network Service Discovery (NSD) / mDNS / ZeroConf**.

- **Service Name**: `MobPlayer TV` (or fallback with dynamic index)
- **Service Type**: `_mobplayer._tcp.` (resolves on mDNS to `_mobplayer._tcp.local.`)
- **Default Port**: `8080`
- **Transport**: TCP
- **Full Domain**: `MobPlayer TV._mobplayer._tcp.local.`

---

## 3. WebSocket Endpoint

The same server also serves the interactive browser test client at `GET /` and
`GET /test_client.html`. Open `http://<TV_IP_ADDRESS>:8080/`, or the PC's forwarded
HTTP address when using an emulator (for example, `http://<PC_LAN_IP>:18081/`).
The page defaults its WebSocket host and port to the address used to open it.
Click **Connect**, then submit the PIN displayed on the TV to test remote control.
Serving the page does not require authentication; control commands still require
the existing PIN/token authentication over `/control`.

- **URL Protocol**: `ws://`
- **Endpoint Path**: `/control`
- **Full URL Format**: `ws://<TV_IP_ADDRESS>:8080/control`
- **Ping / Keepalive Period**: 15 seconds
- **Timeout**: 15 seconds

---

## 4. Message Envelope Contract

All messages exchanged between the client and TV server over the WebSocket must be JSON objects formatted according to the `WebSocketMessage` contract envelope.

### JSON Schema

```json
{
  "type": "STRING_MESSAGE_TYPE",
  "payload": "STRING_OR_ESCAPED_JSON_STRING"
}
```

### Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `type` | String | Defines the message action or event type (e.g. `AUTH_REQUEST`, `COMMAND`, `STATE_UPDATE`). |
| `payload` | String | Message body data or a nested JSON string depending on the message `type`. |

---

## 5. Authentication Protocol Flow

MobPlayer TV requires client authentication before processing playback commands.

```
Client                                                  MobPlayer TV
  |                                                          |
  | --- AUTH_REQUEST (payload: "saved_token_or_empty") ----> |
  |                                                          |
  | <-- PIN_REQUIRED (payload: "") [If token invalid] ------ |
  |                                                          |
  | --- PIN_SUBMIT (payload: "1234") ----------------------> |
  |                                                          |
  | <-- AUTH_SUCCESS (payload: "generated-uuid-token") ----- |  (OR AUTH_FAILED)
```

### 5.1. Authentication Request (`AUTH_REQUEST`)
Client sends token if previously stored, or an empty string.

- **Direction**: Client -> Server
- **Payload**: Stored authentication token (`String`) or `"no-token-yet"`.

```json
{
  "type": "AUTH_REQUEST",
  "payload": "a1b2c3d4-e5f6-7890-abcd-1234567890ab"
}
```

### 5.2. PIN Required Response (`PIN_REQUIRED`)
Server responds with `PIN_REQUIRED` if the token is missing or invalid.

- **Direction**: Server -> Client
- **Payload**: `""`

```json
{
  "type": "PIN_REQUIRED",
  "payload": ""
}
```

### 5.3. PIN Submission (`PIN_SUBMIT`)
User enters the 4-digit PIN displayed on the TV screen.

- **Direction**: Client -> Server
- **Payload**: 4-digit PIN string (e.g., `"4829"`).

```json
{
  "type": "PIN_SUBMIT",
  "payload": "4829"
}
```

### 5.4. Authentication Success (`AUTH_SUCCESS`)
Sent when PIN or token validation succeeds. The payload contains a persistent authentication token that the client must store locally for future auto-connects.

- **Direction**: Server -> Client
- **Payload**: UUID Auth Token (`String`).

```json
{
  "type": "AUTH_SUCCESS",
  "payload": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

### 5.5. Authentication Failed (`AUTH_FAILED`)
Sent when an incorrect PIN is submitted. The server closes the socket connection immediately after.

- **Direction**: Server -> Client
- **Payload**: Error explanation message (`"Invalid PIN"`).

```json
{
  "type": "AUTH_FAILED",
  "payload": "Invalid PIN"
}
```

---

## 6. Control Commands Protocol

Once authenticated, clients can issue playback control and media loading commands.

### 6.1. Execute Player Command (`COMMAND`)

- **Direction**: Client -> Server
- **Payload**: Escaped JSON string of `PlayerCommandPayload`.

#### `PlayerCommandPayload` Schema
```json
{
  "action": "PLAY | PAUSE | PLAY_PAUSE | SEEK | SET_VOLUME",
  "seekToMs": 120000,
  "volume": 0.8
}
```

#### Example Messages

**1. Play Action**
```json
{
  "type": "COMMAND",
  "payload": "{\"action\":\"PLAY\"}"
}
```

**2. Pause Action**
```json
{
  "type": "COMMAND",
  "payload": "{\"action\":\"PAUSE\"}"
}
```

**3. Play/Pause Toggle Action**
```json
{
  "type": "COMMAND",
  "payload": "{\"action\":\"PLAY_PAUSE\"}"
}
```

**4. Seek Action**
```json
{
  "type": "COMMAND",
  "payload": "{\"action\":\"SEEK\",\"seekToMs\":45000}"
}
```

**5. Set Volume Action**
```json
{
  "type": "COMMAND",
  "payload": "{\"action\":\"SET_VOLUME\",\"volume\":0.75}"
}
```

#### Client Shortcut / D-pad Mapping Guidelines
Controllers may map common directional or physical remote buttons directly to these commands:
- **Center / OK**: Toggle `PLAY` / `PAUSE`
- **Left**: Seek backward (`positionMs - 10000ms`) via `SEEK`
- **Right**: Seek forward (`positionMs + 10000ms`) via `SEEK`
- **Up**: Increase volume (`volume + 0.05f`) via `SET_VOLUME`
- **Down**: Decrease volume (`volume - 0.05f`) via `SET_VOLUME`

### 6.2. Load Media URL (`LOAD_MEDIA`)

- **Direction**: Client -> Server
- **Payload**: Direct HTTPS video URL string to stream on the TV.

```json
{
  "type": "LOAD_MEDIA",
  "payload": "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
}
```

### 6.3. Command Execution Success (`COMMAND_SUCCESS`)

- **Direction**: Server -> Client
- **Payload**: Action name that was successfully executed (`"PLAY"`, `"PAUSE"`, `"SEEK"`, `"SET_VOLUME"`, `"LOAD_MEDIA"`).

```json
{
  "type": "COMMAND_SUCCESS",
  "payload": "PLAY"
}
```

---

## 7. Real-Time State Synchronization Broadcast

The TV server continuously broadcasts playback status updates to the connected authenticated controller whenever playback state, position, or volume changes.

- **Direction**: Server -> Client
- **Type**: `STATE_UPDATE`
- **Payload**: Escaped JSON string of `PlayerStatePayload`.

### `PlayerStatePayload` Schema
```json
{
  "isPlaying": true,
  "positionMs": 34520,
  "durationMs": 180000,
  "volume": 1.0
}
```

### Example Broadcast Message
```json
{
  "type": "STATE_UPDATE",
  "payload": "{\"isPlaying\":true,\"positionMs\":34520,\"durationMs\":180000,\"volume\":1.0}"
}
```

---

## 8. Session Management & Conflict Handling

1. **Single Active Controller Policy**: MobPlayer TV supports one active controller session at a time.
2. **Conflict Resolution**: If a second client attempts to connect while a session is active, the TV prompts the user with an on-screen dialog to approve or deny the new connection request. If the TV user denies the request, the TV server transmits an `AUTH_FAILED` message (`{"type":"AUTH_FAILED","payload":"Connection rejected by TV"}`) and closes the WebSocket connection.
3. **PIN Rotation**: The on-screen PIN automatically rotates after every successful authentication session to prevent unauthorized reuse.

---

## 9. Quick Reference Summary Table

| Message Type | Direction | Payload Schema / Format | Description |
| :--- | :--- | :--- | :--- |
| `AUTH_REQUEST` | Client -> TV | Token string or `"no-token-yet"` | Initiate authentication with existing token |
| `PIN_REQUIRED` | TV -> Client | `""` | Signal client that PIN submission is required |
| `PIN_SUBMIT` | Client -> TV | 4-digit PIN string (e.g. `"1234"`) | Submit PIN displayed on TV UI |
| `AUTH_SUCCESS` | TV -> Client | UUID Token string | Authentication successful; save token |
| `AUTH_FAILED` | TV -> Client | `"Invalid PIN"` or `"Connection rejected by TV"` | Auth failed or denied; connection closed |
| `COMMAND` | Client -> TV | `{"action":"PLAY\|PAUSE\|PLAY_PAUSE\|SEEK\|SET_VOLUME", "seekToMs": Long?, "volume": Float?}` | Control playback or volume actions |
| `LOAD_MEDIA` | Client -> TV | Video URL string | Load and auto-play new media item |
| `COMMAND_SUCCESS` | TV -> Client | Action string | Acknowledgment of executed command |
| `STATE_UPDATE` | TV -> Client | `{"isPlaying": Boolean, "positionMs": Long, "durationMs": Long, "volume": Float}` | Real-time state broadcast from ExoPlayer |

---

## 10. Video Upload, Metadata & Playback Resume HTTP API

In addition to WebSocket control, the embedded Ktor server provides RESTful HTTP endpoints for uploading videos from client devices, progressive streaming (play-while-uploading), metadata persistence, and resuming playback.

### 10.1. Upload Video (`POST /api/upload`)
Uploads a local media file from the client to TV internal app storage (`context.filesDir/uploads`). Supports instant progressive streaming: as soon as the initial buffer (64 KB) is written, the TV automatically begins playing via the `/api/stream/{uploadId}` endpoint without waiting for upload completion (< 0.5s playback latency). Supports both raw binary streaming (`call.receiveChannel()`) and standard `multipart/form-data`.

- **Method**: `POST`
- **Path**: `/api/upload?playImmediately=true|false&title=<customTitle>&totalSize=<bytes>&fileName=<originalName>`
- **Headers**:
  - `X-Auth-Token`: Client UUID auth token (or `?token=` query param).
  - `X-File-Size`: Total file size in bytes (optional, helps range estimation).
  - `X-File-Name`: Original filename including extension (e.g. `video.mkv`).
  - `Content-Type`: Either `video/*`, `audio/*`, `application/octet-stream` (direct binary streaming) or `multipart/form-data`.
- **Payload Modes**:
  1. **Direct Binary Stream**: Send raw file bytes directly in the HTTP request body. Zero boundary parsing overhead, immediate chunk-by-chunk disk writes and instant playback trigger.
  2. **Multipart Form-Data**:
     - `file`: Media binary file payload.
     - `title`: Optional custom media title string.
     - `playImmediately`: Optional boolean `"true"` or `"false"` (default: `"true"`).
- **Supported Formats**: MP4, MKV, WebM, MPEG-TS, AVI, MOV, FLV, WMV, 3GP, Ogg, HLS (.m3u8), DASH (.mpd), RTSP, and audio files (MP3, AAC, FLAC, WAV, Opus).
- **Response**: HTTP 200 JSON
```json
{
  "status": "success",
  "uploadId": "upload_1727715600",
  "fileName": "1727715600_Vacation.mp4",
  "title": "Summer Vacation",
  "fileSize": 145281920,
  "videoUrl": "/api/videos/1727715600_Vacation.mp4",
  "streamUrl": "/api/stream/upload_1727715600",
  "playedImmediately": true
}
```

### 10.2. Progressive Stream (`GET /api/stream/{uploadId}`)
Serves an active, growing video file while it is currently being uploaded. Supports HTTP Range requests (`bytes=start-end`) and streams bytes as they are written to disk. If playback catches up to the upload write pointer, it smoothly suspends until more chunks arrive.

- **Method**: `GET`
- **Path**: `/api/stream/{uploadId}`
- **Headers**: Standard HTTP `Range: bytes=start-end` supported.
- **Response**: HTTP 206 Partial Content / HTTP 200 with video stream.

### 10.3. List Stored Videos with Watch Progress (`GET /api/videos`)
Returns a JSON list of all stored videos on the TV along with their metadata, duration, last played position, and watch progress percentage.

- **Method**: `GET`
- **Path**: `/api/videos`
- **Response**: HTTP 200 JSON
```json
[
  {
    "id": "vid_1727715600",
    "fileName": "1727715600_Vacation.mp4",
    "title": "Summer Vacation",
    "originalFileName": "Vacation.mp4",
    "fileSize": 145281920,
    "fileSizeFormatted": "138.5 MB",
    "mimeType": "video/mp4",
    "uploadedAt": 1727715600000,
    "durationMs": 1824000,
    "lastPlayedPositionMs": 345000,
    "lastPlayedAt": 1727716200000,
    "progress": 0.189,
    "isCompleted": false,
    "videoUrl": "/api/videos/1727715600_Vacation.mp4"
  }
]
```

### 10.4. Play or Resume Video (`POST /api/videos/{id}/play`)
Triggers playback of a previously stored video on the TV. By default, resumes from `lastPlayedPositionMs`. If `resume=false` is passed, restarts from the beginning (`0:00`).

- **Method**: `POST`
- **Path**: `/api/videos/{id}/play?resume=true|false`
- **Headers**: `X-Auth-Token`
- **Response**: HTTP 200 JSON `{"status": "success", "resumedAtMs": 345000}`

### 10.5. Stream or Download Completed Video (`GET /api/videos/{fileName}`)
Serves a completed video file with full HTTP Range request support (seeking/scrubbing in external players or browsers).

- **Method**: `GET`
- **Path**: `/api/videos/{fileName}`
- **Response**: HTTP 206 Partial Content / HTTP 200

### 10.6. Delete Video (`DELETE /api/videos/{id}`) and Clear All (`DELETE /api/videos`)
Deletes one or all stored videos and their accompanying `.meta.json` files from TV storage.

- **Method**: `DELETE`
- **Path**: `/api/videos/{id}` (or `/api/videos` for bulk delete)
- **Headers**: `X-Auth-Token`
- **Response**: HTTP 200 JSON `{"status": "success", "deleted": true}`

