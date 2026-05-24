# HTTP 接口文档

统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

## Auth

- `POST /api/v1/auth/wx/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/wx/logout`

## Users

- `GET /api/v1/users/me`
- `PUT /api/v1/users/me`
- `POST /api/v1/users/me/avatar`

## Devices

- `GET /api/v1/devices`
- `POST /api/v1/devices/bind`
- `GET /api/v1/devices/search`
- `PUT /api/v1/devices/{device_id}/info`
- `DELETE /api/v1/devices/{device_id}/binding`

## Events

- `GET /api/v1/events/unpulled`
- `PUT /api/v1/events/{event_id}/read`
- `DELETE /api/v1/events/{event_id}`
- `GET /api/v1/events/search`
- `POST /api/v1/events/{event_id}/refresh-url`

## Admin

- `GET /backend/v1/config`
- `PUT /backend/v1/config`
- `GET /backend/v1/system/info`
