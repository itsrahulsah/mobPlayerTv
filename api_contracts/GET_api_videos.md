# API Contract: List Stored Videos (`GET /api/videos`)

Retrieves a complete list of all video files stored in TV internal storage (`context.filesDir/uploads`), including persisted metadata, durations, last-played timestamps, and computed watch progress percentages.

---

## Endpoint Details

- **Method**: `GET`
- **Path**: `/api/videos`
- **Authentication**: Optional / Public within local network. Accepts `X-Auth-Token` header.

---

## Request Headers

| Header | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `X-Auth-Token` | String | No | Client UUID auth token. |

---

## Response Schema (`200 OK`)

Content-Type: `application/json`

```json
[
  {
    "id": "vid_1790601332361",
    "fileName": "1790601332361_nature.mp4",
    "originalFileName": "nature.mp4",
    "title": "Nature 4K",
    "fileSize": 104857600,
    "fileSizeFormatted": "100.0 MB",
    "mimeType": "video/mp4",
    "uploadedAt": 1790601332361,
    "durationMs": 360000,
    "lastPlayedPositionMs": 45000,
    "lastPlayedAt": 1790601400000,
    "isCompleted": false,
    "progress": 0.125,
    "videoUrl": "/api/videos/1790601332361_nature.mp4"
  }
]
```

### Response Item Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | String | Unique video identifier (e.g. `vid_1790601332361`). |
| `fileName` | String | Stored filename on the TV filesystem. |
| `originalFileName` | String | Client's original filename prior to upload. |
| `title` | String | User-defined display title. |
| `fileSize` | Long | Total file size in bytes. |
| `fileSizeFormatted` | String | Human-readable file size (e.g. `"100.0 MB"`). |
| `mimeType` | String | Container MIME type (e.g. `"video/mp4"`, `"video/x-matroska"`). |
| `uploadedAt` | Long | Epoch timestamp in milliseconds of upload creation. |
| `durationMs` | Long | Total media duration in milliseconds (updated during playback). |
| `lastPlayedPositionMs` | Long | Saved playback position in milliseconds. |
| `lastPlayedAt` | Long | Epoch timestamp of last playback session. |
| `isCompleted` | Boolean | `true` if watched to >= 95% of duration. |
| `progress` | Float | Watch progress between `0.0` and `1.0`. |
| `videoUrl` | String | Relative URL to stream the completed file. |

---

## Error Responses

| Status Code | Reason | Body |
| :---: | :--- | :--- |
| `500 Internal Server Error` | Metadata parsing failure | Error message string |
