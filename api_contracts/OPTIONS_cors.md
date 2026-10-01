# API Contract: CORS Preflight Handler (`OPTIONS /api/{...}`)

Handles Cross-Origin Resource Sharing (CORS) preflight requests issued by modern web browsers when web-based controller applications (such as `test_client.html` or custom web controllers) interact with TV REST endpoints.

---

## Endpoint Details

- **Method**: `OPTIONS`
- **Path**: `/api/{...}`
- **Authentication**: None required.

---

## Request Headers (Typical Browser Preflight)

```http
OPTIONS /api/upload HTTP/1.1
Host: 192.168.1.100:8080
Origin: http://localhost:3000
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type, X-Auth-Token, X-File-Size, X-File-Name
```

---

## Response Headers & Code (`200 OK`)

```http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, X-Auth-Token, X-Auth-PIN, Authorization, Range, X-File-Size, X-File-Name
Content-Length: 0
```

### Policy Summary

| Header | Value | Purpose |
| :--- | :--- | :--- |
| `Access-Control-Allow-Origin` | `*` | Permits web controllers hosted on any origin, LAN IP, or localhost. |
| `Access-Control-Allow-Methods` | `GET, POST, DELETE, OPTIONS` | Allows all standard CRUD operations. |
| `Access-Control-Allow-Headers` | `Content-Type, X-Auth-Token, X-Auth-PIN, Authorization, Range, X-File-Size, X-File-Name` | Permits streaming headers and authentication tokens. |
