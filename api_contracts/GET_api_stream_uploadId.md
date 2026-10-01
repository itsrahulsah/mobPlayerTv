# API Contract: Progressive Live Stream (`GET /api/stream/{uploadId}`)

Streams an actively uploading, growing media file directly to ExoPlayer or external video players. Implements dynamic HTTP Range requests (`bytes=start-end`) and streams chunks as they are written to disk. If playback consumes data faster than the incoming upload rate, the stream suspends non-blockingly until new chunks arrive.

---

## Endpoint Details

- **Method**: `GET`
- **Path**: `/api/stream/{uploadId}`
- **Authentication**: None required (public playback endpoint allowing local ExoPlayer `DefaultHttpDataSource` access).

---

## Path Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `uploadId` | String | Yes | Unique upload identifier returned by `POST /api/upload` (e.g. `upload_1790601332361`). |

---

## Request Headers

| Header | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `Range` | String | No | Standard HTTP byte range request (e.g. `bytes=0-`, `bytes=65536-131071`). |

---

## Response Headers & Codes

### 1. Partial Content Response (`206 Partial Content`)
Returned when the client includes a `Range` header.

```http
HTTP/1.1 206 Partial Content
Content-Type: video/mp4
Accept-Ranges: bytes
Content-Range: bytes 0-104857599/104857600
Content-Length: 104857600
Access-Control-Allow-Origin: *
```

### 2. Full Stream Response (`200 OK`)
Returned when no `Range` header is present.

```http
HTTP/1.1 200 OK
Content-Type: video/mp4
Accept-Ranges: bytes
Content-Length: 104857600
Access-Control-Allow-Origin: *
```

---

## Live Progressive Streaming Behavior

```
Client Upload Loop                     TV RandomAccessFile Reader Loop
------------------                     -------------------------------
Chunk 1 (64 KB written) ------------> write(0..64KB)
                                        |
                                        v
                                      read(0..64KB) ---> Transmitted to ExoPlayer
                                        |
                                        v
                                      currentPos >= available -> delay(40ms)
Chunk 2 (64 KB written) ------------> write(64..128KB)
                                        |
                                        v
                                      read(64..128KB) -> Transmitted to ExoPlayer
...                                   ...
Upload Complete (isCompleted=true) -> Streams remainder to EOF -> Closes connection cleanly
```

- **Seamless Transition to Local File**: Once upload completes (`upload.isCompleted = true`), the file remains intact on disk. Any subsequent seek requests or Range requests are served directly from the completed file.
- **Fast Re-buffer Recovery**: If network conditions slow down the upload, ExoPlayer enters short buffering without resetting the stream.

---

## Error Responses

| Status Code | Reason | Body |
| :---: | :--- | :--- |
| `400 Bad Request` | Missing upload ID parameter | `"Missing upload ID"` |
| `404 Not Found` | Upload session expired, failed, or invalid ID | `"Active upload stream not found"` |
| `500 Internal Server Error` | File I/O read exception | Error stack message |
