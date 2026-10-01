# API Contract: Play or Resume Video (`POST /api/videos/{id}/play`)

Instructs the TV to load and begin playback of a previously stored video. By default, automatically resumes playback from `lastPlayedPositionMs` if previously watched past 5 seconds and not marked completed.

---

## Endpoint Details

- **Method**: `POST`
- **Path**: `/api/videos/{id}/play`
- **Authentication**: Optional / Accepts `X-Auth-Token` header.

---

## Path Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `id` | String | Yes | Video identifier (e.g. `vid_1790601332361`). |

---

## Query Parameters

| Parameter | Type | Required | Default | Description |
| :--- | :---: | :---: | :---: | :--- |
| `resume` | Boolean | No | `true` | When `true`, resumes playback from `lastPlayedPositionMs`. When `false`, restarts from `0:00`. |

---

## Request Headers

| Header | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `X-Auth-Token` | String | No | Client UUID auth token. |

---

## Response Schema (`200 OK`)

Content-Type: `application/json`

```json
{
  "status": "success",
  "resumedAtMs": 45000
}
```

### Response Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `status` | String | Status indicator (`"success"`). |
| `resumedAtMs` | Long | The millisecond position at which ExoPlayer started playback. |

---

## TV Side Effects

1. **Player UI Opens**: `ServerRepository.openPlayer(title)` brings the ExoPlayer fullscreen overlay into foreground focus on the TV screen.
2. **Media Loaded**: `MediaRepository.loadMedia("file://<path>", startPos, id, mimeType)` prepares the player and triggers playback.
3. **HUD Toast Notification**: TV displays an animated floating HUD pill:
   - Resumed: `🎬 Playing (Resumed) - <Title>`
   - Restarted: `🎬 Playing - <Title>`
4. **WebSocket Broadcast**: Emits a `STATE_UPDATE` event with the updated playback state and position to the active controller.

---

## Error Responses

| Status Code | Reason | Body |
| :---: | :--- | :--- |
| `400 Bad Request` | Missing video ID in path | `"Missing video ID"` |
| `404 Not Found` | Video metadata record not found | `"Video not found"` |
| `404 Not Found` | Metadata exists but video file missing on disk | `"Video file missing on TV"` |
