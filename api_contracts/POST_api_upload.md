# API Contract: Upload Media (`POST /api/upload`)

Uploads a local media file from a client device to TV internal storage (`context.filesDir/uploads`). Supports **instant progressive streaming**: as soon as the initial 64 KB is written to disk, the TV automatically begins playing via `/api/stream/{uploadId}` (< 0.5s playback startup) while the remaining bytes continue uploading in the background.

---

## Endpoint Details

- **Method**: `POST`
- **Path**: `/api/upload`
- **Supported Encodings**:
  1. **Direct Raw Binary Stream** (Recommended for performance and lowest latency): Sends raw file bytes directly in the HTTP body.
  2. **Multipart Form-Data** (`multipart/form-data`): Standard multipart form upload.

---

## Query Parameters

| Parameter | Type | Required | Default | Description |
| :--- | :---: | :---: | :---: | :--- |
| `playImmediately` | Boolean | No | `true` | When `true`, automatically opens the player and starts progressive playback within 64 KB of upload start. |
| `play` | Boolean | No | `true` | Alias for `playImmediately`. |
| `title` | String | No | Filename | Custom display title shown on the TV player and stored in metadata. |
| `fileName` | String | No | `"uploaded_media.mp4"` | Original filename with extension (used for MIME resolution in direct stream mode). |
| `totalSize` | Long | No | `0` | Total file size in bytes (helps HTTP Range header estimation). |
| `token` | String | No | Empty | Optional auth token if not provided in header. |

---

## Request Headers

| Header | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `Content-Type` | String | Yes | `video/*`, `audio/*`, `application/octet-stream` (Direct Stream) OR `multipart/form-data` |
| `X-Auth-Token` | String | No | Valid UUID auth token from pairing. |
| `X-File-Name` | String | No | URL-encoded original filename with extension (e.g. `vacation.mkv`). |
| `X-File-Size` | Long | No | Total file size in bytes. |

---

## Request Body Modes

### Mode 1: Direct Raw Binary Stream (Recommended)
Pass file or blob bytes directly as the request body. Zero boundary parsing overhead, immediate disk chunk writes.

```http
POST /api/upload?playImmediately=true&title=Nature%204K&totalSize=104857600&fileName=nature.mp4 HTTP/1.1
Host: 192.168.1.100:8080
Content-Type: video/mp4
Content-Length: 104857600
X-File-Size: 104857600
X-File-Name: nature.mp4

<raw binary bytes...>
```

### Mode 2: Multipart Form-Data
Standard multipart form fields.

```http
POST /api/upload HTTP/1.1
Host: 192.168.1.100:8080
Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryXYZ

------WebKitFormBoundaryXYZ
Content-Disposition: form-data; name="title"

Nature 4K
------WebKitFormBoundaryXYZ
Content-Disposition: form-data; name="playImmediately"

true
------WebKitFormBoundaryXYZ
Content-Disposition: form-data; name="file"; filename="nature.mp4"
Content-Type: video/mp4

<binary file data...>
------WebKitFormBoundaryXYZ--
```

---

## Response Schema (`200 OK`)

Content-Type: `application/json`

```json
{
  "status": "success",
  "uploadId": "upload_1790601332361",
  "fileName": "1790601332361_nature.mp4",
  "title": "Nature 4K",
  "size": 104857600,
  "videoUrl": "/api/videos/1790601332361_nature.mp4",
  "streamUrl": "/api/stream/upload_1790601332361",
  "playedImmediately": true
}
```

### Response Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `status` | String | Status indicator (`"success"`). |
| `uploadId` | String | Unique upload tracking ID used for `/api/stream/{uploadId}`. |
| `fileName` | String | Persisted filename on TV storage (`<timestamp>_<cleanName>`). |
| `title` | String | Display title stored in TV metadata. |
| `size` | Long | Total size in bytes verified on disk. |
| `videoUrl` | String | Relative URL to access the completed video file. |
| `streamUrl` | String | Relative URL used by ExoPlayer for live progressive streaming. |
| `playedImmediately` | Boolean | Confirms whether playback was triggered during upload. |

---

## Error Responses

| Status Code | Reason | Body |
| :---: | :--- | :--- |
| `400 Bad Request` | Missing file payload or invalid params | `"No file uploaded"` |
| `401 Unauthorized` | Invalid or revoked token | `"Invalid authentication token"` |
| `500 Internal Server Error` | Disk I/O or server exception | `"Upload failed: <message>"` |

---

## Supported Media Formats

- **Video Containers**: MP4, MKV (Matroska), WebM, MPEG-TS (`.ts`, `.m2ts`), AVI, MOV, FLV, WMV, 3GP, Ogg.
- **Adaptive Streams**: HLS (`.m3u8`), DASH (`.mpd`), RTSP.
- **Audio Formats**: MP3, AAC, FLAC, WAV, Opus, M4A, OGG.
