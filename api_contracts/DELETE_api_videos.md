# API Contract: Clear All Videos (`DELETE /api/videos`)

Permanently deletes all stored video files and their accompanying metadata files from TV internal storage (`context.filesDir/uploads`). Resets the TV video library.

---

## Endpoint Details

- **Method**: `DELETE`
- **Path**: `/api/videos`
- **Authentication**: Optional / Accepts `X-Auth-Token` header.

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
  "deletedCount": 5
}
```

### Response Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `status` | String | Status indicator (`"success"`). |
| `deletedCount` | Integer | Total count of video files deleted from storage. |

---

## TV Side Effects

1. **Storage Wiped**: Removes all files inside `context.filesDir/uploads/`.
2. **Library Reset**: Updates `uploadedVideosFlow` to an empty list (`[]`).
3. **Player Stopped**: If any video was actively playing, playback is halted.
