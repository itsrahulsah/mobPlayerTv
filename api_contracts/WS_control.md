# API Contract: WebSocket Remote Control & State Synchronization (`WS /control`)

The primary full-duplex control protocol between client controller applications (mobile apps, web apps) and MobPlayer TV. Handles PIN-based device pairing, token-based re-authentication, playback commands, media loading, volume adjustment, D-pad navigation, and continuous real-time playback state updates.

---

## Endpoint Details

- **Protocol**: `ws://`
- **Path**: `/control`
- **Full URL Format**: `ws://<TV_IP_ADDRESS>:8080/control`
- **Ping / Keepalive Period**: 15 seconds
- **Connection Policy**: Single active controller (connection requests while active prompt the TV user for approval).

---

## Message Envelope Structure

All messages exchanged over the WebSocket channel must adhere to the `WebSocketMessage` JSON schema:

```json
{
  "type": "STRING_EVENT_TYPE",
  "payload": "STRING_OR_JSON_ENCODED_PAYLOAD"
}
```

---

## 1. Authentication & Pairing Flow

```
Client App                                             MobPlayer TV
    |                                                       |
    | --- AUTH_REQUEST (payload: "saved_token_or_empty") -->|
    |                                                       |
    | <-- PIN_REQUIRED (payload: "") ---------------------- | (If token missing/invalid)
    |                                                       |
    | --- PIN_SUBMIT (payload: "1234") -------------------->|
    |                                                       |
    | <-- AUTH_SUCCESS (payload: "uuid-auth-token") --------| (PIN verified)
    |     (OR AUTH_FAILED: "Invalid PIN")                   |
```

### `AUTH_REQUEST` (Client -> TV)
Initiates authentication with a previously saved token or empty string.

```json
{
  "type": "AUTH_REQUEST",
  "payload": "d3b07384-d113-40e1-a083-d5d1c25bbcb5"
}
```

### `PIN_REQUIRED` (TV -> Client)
Sent by the TV when a client connects without a token or with an invalid token. Displays a 4-digit pairing PIN on the TV screen.

```json
{
  "type": "PIN_REQUIRED",
  "payload": ""
}
```

### `PIN_SUBMIT` (Client -> TV)
Submits the 4-digit PIN currently shown on the TV.

```json
{
  "type": "PIN_SUBMIT",
  "payload": "4892"
}
```

### `AUTH_SUCCESS` (TV -> Client)
Confirms successful pairing or token validation. The payload contains the new/confirmed UUID token to save for future reconnects.

```json
{
  "type": "AUTH_SUCCESS",
  "payload": "e81d77b8-b80c-4eb3-8182-411ea21b8c19"
}
```

### `AUTH_FAILED` (TV -> Client)
Sent if the PIN is incorrect or the connection is rejected by the TV user.

```json
{
  "type": "AUTH_FAILED",
  "payload": "Invalid PIN"
}
```

---

## 2. Playback & Remote Control Commands

Once authenticated, the client sends commands inside a `COMMAND` message envelope.

### Payload Schema: `CommandPayload`

```json
{
  "action": "STRING_ACTION",
  "seekToMs": 45000,
  "volume": 0.8
}
```

### Supported Actions

| Action | Payload Example | Description |
| :--- | :--- | :--- |
| `PLAY` | `{"action": "PLAY"}` | Resumes or starts playback in ExoPlayer. |
| `PAUSE` | `{"action": "PAUSE"}` | Pauses playback. |
| `STOP` | `{"action": "STOP"}` | Stops playback, clears media items, closes player view. |
| `SEEK` | `{"action": "SEEK", "seekToMs": 120000}` | Seeks ExoPlayer to the specified millisecond offset. |
| `SEEK_FORWARD` | `{"action": "SEEK_FORWARD"}` | Skips forward by 10 seconds. |
| `SEEK_BACKWARD` | `{"action": "SEEK_BACKWARD"}` | Skips backward by 10 seconds. |
| `SET_VOLUME` | `{"action": "SET_VOLUME", "volume": 0.75}` | Sets ExoPlayer audio volume (0.0 to 1.0). |
| `VOLUME_UP` | `{"action": "VOLUME_UP"}` | Increases volume by +10%. |
| `VOLUME_DOWN` | `{"action": "VOLUME_DOWN"}` | Decreases volume by -10%. |
| `MUTE` | `{"action": "MUTE"}` | Toggles mute on/off (restores previous volume when unmuted). |
| `BACK` | `{"action": "BACK"}` | Simulates the Android TV Back navigation button. |
| `HOME` | `{"action": "HOME"}` | Closes the player view and returns to `TvHomeScreen`. |
| `DPAD_UP` | `{"action": "DPAD_UP"}` | Simulates D-pad UP key event. |
| `DPAD_DOWN` | `{"action": "DPAD_DOWN"}` | Simulates D-pad DOWN key event. |
| `DPAD_LEFT` | `{"action": "DPAD_LEFT"}` | Simulates D-pad LEFT key event. |
| `DPAD_RIGHT` | `{"action": "DPAD_RIGHT"}` | Simulates D-pad RIGHT key event. |
| `DPAD_CENTER` | `{"action": "DPAD_CENTER"}` | Simulates D-pad SELECT / OK key event. |

### Example Command Message (Client -> TV)

```json
{
  "type": "COMMAND",
  "payload": "{\"action\": \"SEEK\", \"seekToMs\": 65000}"
}
```

### Command Acknowledgment (`COMMAND_SUCCESS`) (TV -> Client)

```json
{
  "type": "COMMAND_SUCCESS",
  "payload": "SEEK"
}
```

---

## 3. Load External Media URL (`LOAD_MEDIA`)

Streams an external media URL (MP4, MKV, WebM, HLS `.m3u8`, DASH `.mpd`, RTSP, or audio stream) directly on the TV.

### Request (Client -> TV)

```json
{
  "type": "LOAD_MEDIA",
  "payload": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
}
```

---

## 4. Real-Time State Broadcast (`STATE_UPDATE`)

The TV continuously broadcasts playback state updates down the WebSocket:
- Whenever playback begins, pauses, seeks, or completes.
- Polled every 1 second while video is actively playing.
- Whenever volume changes.

### Broadcast Message (TV -> Client)

```json
{
  "type": "STATE_UPDATE",
  "payload": "{\"isPlaying\":true,\"positionMs\":24500,\"durationMs\":180000,\"volume\":1.0}"
}
```

### `PlayerStatePayload` Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `isPlaying` | Boolean | `true` if media is actively playing; `false` if paused/buffering/ended. |
| `positionMs` | Long | Current playback position in milliseconds. |
| `durationMs` | Long | Total duration of active media item in milliseconds. |
| `volume` | Float | Current volume level from `0.0` (muted) to `1.0` (maximum). |
