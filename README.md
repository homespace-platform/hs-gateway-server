## Cấu hình local cho AI

- Cấu hình Gateway trong `.vscode/launch.json` của workspace đã nạp `hs-gateway-server/.env` qua `envFile`.
- `GATEWAY_INTERNAL_SECRET` phải có cùng giá trị với `hs-ai-service/.env`, là chuỗi ngẫu nhiên ít nhất 32 ký tự. Không commit giá trị thật hoặc đưa vào frontend.
- Sau khi sửa `.env`, dừng/chạy lại Gateway để nạp biến mới; chạy lại AI nếu service đang tắt. Tiến trình đang chạy không tự nhận thay đổi file này.
- Lỗi `403 Forbidden: untrusted request source` từ AI nghĩa là header secret nội bộ bị thiếu hoặc sai, không phải lỗi quyền ADMIN. Không tắt kiểm tra bảo mật để xử lý lỗi này.
- Nếu chạy bằng Maven thay vì cấu hình VS Code trên, cần nạp các biến môi trường khi khởi chạy; Spring không tự đọc file `.env` với cấu hình hiện tại.

## Luồng request

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
