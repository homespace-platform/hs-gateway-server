```
Request vào
    │
    ▼
[1] SecurityConfig    → Verify JWT, phân quyền path
    │
    ▼
[2] Rate Limiter      → Giới hạn số request/giây theo IP
    │
    ▼
[3] Path Rewrite      → Chuyển đổi URL trước khi forward
    │
    ▼
[4] Circuit Breaker   → Ngắt khi service con chết
    │
    ▼
[5] UserHeaderFilter  → Inject header user info (GlobalFilter)
    │
    ▼
Service con

```