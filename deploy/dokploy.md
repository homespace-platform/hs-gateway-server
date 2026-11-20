# Gateway: Application riêng, Docker Hub webhook

Luồng duy nhất: push `master` → GitHub Actions test/build → Docker Hub → Docker Hub webhook → Dokploy cập nhật riêng Application Gateway. Không cần `DOKPLOY_DEPLOY_WEBHOOK` trong GitHub.

## 1. Publish image

1. Docker Hub tạo repository `nvminh162/hs-gateway-server`.
2. GitHub `homespace-platform/hs-gateway-server` → Settings → Secrets and variables → Actions: cho repo sử dụng org secrets `DOCKERHUB_USERNAME` và `DOCKERHUB_TOKEN` có quyền push. Nếu org secrets không dùng được với repo private, thêm hai secrets ở repo.
3. Review rồi commit/push các file deploy lên `master`; chờ Actions **Build and publish Gateway** thành công. Image có tag Git SHA và `latest`.

## 2. Tạo Application

Deploy Discovery và Core trước. Trong Dokploy → homespace → production → **Create Service → Application**, tên `hs-gateway-server`, cùng server infra.

1. General → Provider **Docker**, Image **`nvminh162/hs-gateway-server:latest`**, Save. Nếu image private, cấu hình registry `docker.io` và credential pull.
2. Environment: dán nguyên nội dung file riêng tư **`hs-gateway-server/.env.dokploy`**, Save. File đã đổi địa chỉ infra sang production và giữ secret cũ; dấu single quote là cú pháp dotenv hợp lệ. Không dùng `.env` local hoặc env của repo khác.
3. Advanced → Networks: gắn mạng overlay attachable **`hs-network`**, có alias **`gateway`**, giữ `dokploy-network`. Nếu dùng Advanced → Swarm Settings → Network, nhập Target `hs-network`, Aliases `gateway`. Save, replica 1.
4. Mạng này phải được tạo và gắn cho infra trước; Discovery cần alias `discovery`. Env production Core đã chuẩn bị `SPRING_CLOUD_DISCOVERY_ENABLED=true` và `EUREKA_CLIENT_ENABLED=true`; deploy Core khi Discovery healthy để nó đăng ký với Spring name `hs-core-api`. Thứ tự deploy: **Discovery → Core → Gateway**.
5. Advanced → Resources: memory limit `805306368` bytes (768 MiB), nếu trường UI nhận raw bytes.
6. General → Deploy, chờ deployment Done và container healthy. Image tự kiểm tra `http://127.0.0.1:8080/actuator/health`.

Route hiện tại vẫn dùng `lb://hs-core-api` qua Eureka, không đổi sang IP/public domain. `GATEWAY_INTERNAL_SECRET` phải khớp Core. JWT issuer dùng domain Auth HTTPS; JWK lấy nội bộ `keycloak:8080`. Các route Chat/News/Notification/AI sẽ chỉ hoạt động khi service tương ứng được deploy và đăng ký Eureka.

## 3. Domain API

iNET → Bản ghi của `homespace.id.vn`: thêm bản ghi **A**, Host **`api`**, Value là Elastic IP hiện đang gắn với EC2 (đối chiếu AWS trước khi lưu).

Application Gateway → Domains → Add Domain:

| Trường | Giá trị |
| --- | --- |
| Host | `api.homespace.id.vn` |
| Container Port | `8080` |
| Path / Internal Path | `/` |
| Strip Path | Tắt |
| HTTPS / Certificate | Bật / Let's Encrypt |

Không mở port 8080 trên Security Group. Traefik nhận HTTPS 443. CORS production hiện dùng `https://homespace.id.vn,https://admin.homespace.id.vn`; cập nhật khi domain frontend thực tế khác.

## 4. Webhook đặt trên Docker Hub

1. Application Gateway → General: bật **Autodeploy**, Save.
2. **Deployments → Webhook URL**: copy URL Application Gateway, dạng `https://dokploy.homespace.id.vn/api/deploy/TOKEN`.
3. Docker Hub → `nvminh162/hs-gateway-server` → **Webhooks → New webhook**.
4. Name **`dokploy-gateway`**, Webhook URL: URL vừa copy, **Add webhook**.
5. GitHub Actions → Run workflow trên `master`. Khi push tag `latest`, Docker Hub gọi webhook; Dokploy cập nhật riêng Gateway. Tag SHA không khớp tag cấu hình `latest` nên Dokploy bỏ qua sự kiện đó.

Không dùng webhook của infra/Core/Discovery hoặc URL `/api/deploy/compose/...`. Không thêm webhook GitHub cho deploy vì Docker Hub đã đảm nhiệm trigger.

## 5. Kiểm tra sau deploy

```bash
curl --fail --silent --show-error https://api.homespace.id.vn/actuator/health
curl --fail --silent --show-error https://api.homespace.id.vn/api/v1/actuator/health/readiness
curl --fail --silent --show-error 'https://api.homespace.id.vn/api/v1/public/listings?page=1&size=1'
```

Endpoint `/actuator/health` kiểm tra Gateway; `/api/v1/actuator/health/readiness` đi qua route đến Core. Cần Core và Gateway cùng đăng ký Discovery để kiểm tra route này. Kiểm tra anonymous không đọc được feed/admin API và đăng nhập qua frontend sau khi các frontend được deploy.

Giữ `.env.dokploy` ngoài Git; không đổi hoặc bỏ secret để vượt lỗi auth. Các file chuẩn bị chưa tự commit, push hoặc deploy lên production.
