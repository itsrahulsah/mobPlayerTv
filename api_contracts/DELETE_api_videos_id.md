# API Contract: Delete Video (`DELETE /api/videos/{id}`)

Permanently deletes a specific video file and its persisted `.meta.json` record from TV internal storage (`context.filesDir/uploads`). If the deleted video is currently active in the player, playback is stopped.

---

## Endpoint Details

- **Method**: `DELETE`
- **Path**: `/api/videos/{id}`
- **Authentication**: Optional / Accepts `X-Auth-Token` header.

---

## Path Parameters

| Parameter | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `id` | String | Yes | Video identifier (e.g. `vid_1790601332361`). |

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
  "deleted": true
}
```

### Response Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `status` | String | Status indicator (`"success"`). |
| `deleted` | Boolean | Confirms deletion (`true`). |

---

## TV Side Effects

1. **Storage Cleanup**: Removes `<fileName>` and `<fileName>.meta.json` from disk.
2. **Library Refresh**: Updates `VideoUploadManager.uploadedVideosFlow`, refreshing the "Uploaded Videos" rail on `TvHomeScreen`.

---

## Error Responses

| Status Code | Reason | Body |
| :---: | :--- | :--- |
| `400 Bad Request` | Missing video ID in path | `"Missing video ID"` |
| `404 Not Found` | Video ID not found or already deleted | `"Video not found or already deleted"` |
