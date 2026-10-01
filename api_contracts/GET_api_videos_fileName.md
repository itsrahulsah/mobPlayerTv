# API Contract: Stream / Download Completed Video (`GET /api/videos/{fileName}`)

Serves a completed media file from TV internal storage (`context.filesDir/uploads`). Provides full HTTP Range request support (`206 Partial Content`), allowing clients, web browsers, and media players to seek and scrub seamlessly across the timeline.

---

## Endpoint Details

- **Method**: `GET`
- **Path**: `/api/videos/{fileName}`
- **Authentication**: Optional / Public within local network.

---

## Path Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `fileName` | String | Yes | Stored file name on the TV filesystem (e.g. `1790601332361_nature.mp4`). |

---

## Request Headers

| Header | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `Range` | String | No | Standard HTTP byte range request (e.g. `bytes=1048576-2097151`). |

---

## Response Headers & Codes

### 1. Partial Content (`206 Partial Content`)
Returned when a `Range` header is provided by the client.

```http
HTTP/1.1 206 Partial Content
Content-Type: video/mp4
Accept-Ranges: bytes
Content-Range: bytes 1048576-2097151/104857600
Content-Length: 1048576
Access-Control-Allow-Origin: *
```

### 2. Full Content (`200 OK`)
Returned when no `Range` header is present.

```http
HTTP/1.1 200 OK
Content-Type: video/mp4
Accept-Ranges: bytes
Content-Length: 104857600
Access-Control-Allow-Origin: *
```

---

## Error Responses

| Status Code | Reason | Body |
| :---: | :--- | :--- |
| `400 Bad Request` | Missing `fileName` path parameter | `"Missing filename"` |
| `404 Not Found` | File does not exist on disk | `"Video not found"` |
