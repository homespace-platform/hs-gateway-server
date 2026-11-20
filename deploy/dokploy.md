# Gateway — Compose riêng, network trong YAML

Thử nghiệm cùng Discovery; giữ nguyên Compose infra, database và các service khác. File [`compose.production.yml`](../compose.production.yml) dùng image đã publish, dùng service name `hs-gateway-server` làm DNS và tham chiếu external network `hs-network`. Không chỉnh Advanced → Networks và không thay đổi route, xác thực hoặc rate limiting của Gateway.

Tên service, DNS nội bộ và Spring application name đều là `hs-gateway-server`; container khác cùng mạng gọi `http://hs-gateway-server:8080`. Domain public vẫn là `api.homespace.id.vn`. Nếu đã deploy bản Compose cũ có service key `gateway`, Stop bản cũ trước khi chuyển; đổi Service Name trong Domains thành `hs-gateway-server` rồi Deploy, xác nhận không còn container Gateway cũ đang chạy. Các consumer từng dùng `http://gateway:8080` phải chuyển sang hostname mới; không thêm alias tương thích thừa.

## 1. Chuẩn bị

1. Infra đang healthy; `hs-network` là overlay attachable. Discovery Compose đã chạy với tên DNS `hs-discovery-server`. Không xóa infra hoặc tạo một mạng khác cùng tên.
2. GitHub Actions `Build and publish Gateway` đã publish image. Environment bắt buộc có `GATEWAY_IMAGE=nvminh162/hs-gateway-server:latest` hoặc tag SHA đã publish. Workflow giữ nguyên, chỉ test/build/push image; không gọi deploy API.
3. Giữ file `.env.dokploy` của Gateway ngoài Git. Không thay `GATEWAY_INTERNAL_SECRET`, password Redis, JWT issuer hoặc bỏ kiểm tra auth để sửa lỗi kết nối.
4. Lưu lại cấu hình và domain của Application Gateway cũ để có thể quay lại. Không chạy song song hai Gateway khi chuyển đổi.

## 2. Tạo Compose Gateway trên Dokploy

1. **Projects → homespace → production → Create Service → Docker Compose**, tên `gateway-compose`, cùng server với infra/Discovery.
2. **General → Provider Git**, nhập:

   | Trường | Giá trị |
   | --- | --- |
   | Repository URL | `https://github.com/homespace-platform/hs-gateway-server.git` |
   | Branch | `master` |
   | Compose Path | `./compose.production.yml` |
   | Compose Type | Docker Compose, không phải Stack |
   | Autodeploy | Tắt trong lần chuyển đổi đầu |

3. Save. Repo public HTTPS không cần SSH key; private cần quyền đọc. File YAML phải có trên Git trước khi deploy; không dùng file local chưa push.
4. **Environment**: dán đầy đủ `.env.dokploy` riêng của Gateway, bao gồm `GATEWAY_IMAGE` → Save. Toàn bộ 15 biến tham chiếu trong Compose đều bắt buộc bằng `${VARIABLE:?}`, không có fallback; thiếu hoặc rỗng bất kỳ biến nào sẽ làm `config`/deploy báo lỗi. Compose ánh xạ đủ 14 biến runtime qua `environment:`; biến image dùng ở trường `image`.
5. Kiểm tra các URL công khai/nội bộ, không đổi credential:

   ```dotenv
   EUREKA_CLIENT_SERVICE_URL=http://hs-discovery-server:8761/eureka/
   JWT_JWK_SET_URI=http://keycloak:8080/realms/homespace-platform/protocol/openid-connect/certs
   JWT_ISSUER_URI=https://auth.homespace.id.vn/realms/homespace-platform
   REDIS_HOST=redis
   REDIS_PORT=6379
   ```

   `GATEWAY_INTERNAL_SECRET` phải khớp Core và các consumer; Redis password phải khớp infra. Không dùng env của infra thay cho env Gateway. Issuer công khai và JWK URL nội bộ có vai trò khác nhau, không đổi issuer thành `http://keycloak:8080`.
6. **Preview Compose**: service `hs-gateway-server`, image đúng, environment đã ánh xạ, default network external `hs-network`, memory limit 768 MiB. Healthcheck kế thừa từ image. Không có `ports:` public. Giữ tắt Isolated Deployment cho luồng mạng chung; kiểm tra Preview còn `hs-network`.
7. **Domains → Add Domain** trên Compose mới: Host `api.homespace.id.vn`, Service `hs-gateway-server`, Container Port `8080`, Path `/`, Internal Path `/` hoặc trống, Strip Path tắt, HTTPS bật, Let's Encrypt. Chuyển domain từ Application cũ theo bước tiếp theo; không giữ hai router cùng Host. Sau khi có domain, Dokploy thêm kết nối Traefik `dokploy-network` và giữ mạng backend trong YAML.

## 3. Chuyển từ Application cũ

1. Chờ Discovery Compose healthy và image Gateway publish thành công. Trong Application Gateway cũ, tắt Autodeploy, vô hiệu hóa webhook Docker Hub trỏ tới Application cũ.
2. **Stop Application Gateway cũ**, chờ container/task cũ dừng. Gỡ domain `api.homespace.id.vn` khỏi Application cũ rồi lưu domain đó ở Compose mới. Dự kiến API gián đoạn trong khoảng chuyển đổi này; không ảnh hưởng dữ liệu infra.
3. **Compose `gateway-compose` → General → Deploy**. Theo dõi deployment, Logs và Containers. Cần container Gateway healthy trước khi kiểm tra route Core.
4. Xác nhận HTTPS, Gateway health và Gateway đăng ký Eureka. Chỉ xóa thẻ Application cũ sau khi bản Compose ổn định. Không xóa infra hoặc volumes.
5. Nếu chuyển đổi lỗi: Stop Compose Gateway mới, gỡ domain mới, khôi phục domain và Start lại Application Gateway cũ. Không để hai Gateway cùng hoạt động để thử sửa lỗi.

## 4. Kiểm tra kết nối và health

Kiểm tra container mới trên EC2:

```bash
sudo docker ps --filter label=com.docker.compose.service=hs-gateway-server --format 'table {{.ID}}\t{{.Names}}\t{{.Status}}'
GATEWAY_CONTAINER='DAN_CONTAINER_ID_DANG_CHAY_O_TREN'
sudo docker exec "$GATEWAY_CONTAINER" getent hosts hs-discovery-server keycloak redis
sudo docker exec "$GATEWAY_CONTAINER" curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health
sudo docker exec "$GATEWAY_CONTAINER" curl -fsS --max-time 5 -H 'Accept: application/json' http://hs-discovery-server:8761/eureka/apps/HS-GATEWAY-SERVER
```

Trên máy cá nhân:

```bash
curl --fail --silent --show-error https://api.homespace.id.vn/actuator/health
```

Gateway health phải `UP`, Eureka phải có `HS-GATEWAY-SERVER` sau khi đăng ký. Nếu health nội bộ UP nhưng public 502, kiểm tra domain Port `8080`, nhãn Traefik, mạng `dokploy-network` và log Traefik. Nếu DNS `hs-discovery-server` không phân giải, kiểm tra Discovery Compose đã chạy và hai Compose cùng `hs-network`, không sửa sang IP container.

Chỉ khi Core đã deploy, cùng mạng và đăng ký Eureka mới kiểm tra:

```bash
curl --fail --silent --show-error https://api.homespace.id.vn/api/v1/actuator/health/readiness
curl --fail --silent --show-error 'https://api.homespace.id.vn/api/v1/public/listings?page=1&size=1'
```

`/actuator/health` kiểm tra Gateway; `/api/v1/actuator/health/readiness` đi qua Core. Chưa deploy Core thì lỗi route Core không đồng nghĩa Gateway hay Discovery bị hỏng. Kiểm tra anonymous vẫn bị từ chối ở feed/admin API khi xác minh đăng nhập frontend; không bỏ security filters.

## 5. Cập nhật và webhook

**Git provider (thử nghiệm):** push lên master → chờ Actions test/build/push thành công → bấm Deploy Compose. Network tự áp dụng từ YAML; DNS dùng tên service `hs-gateway-server`, env và domain được giữ trên Dokploy. Không cần rebuild chỉ vì đổi network YAML nếu dùng lại image đã publish.

Git provider trên Dokploy v0.31.4 không nhận trực tiếp Docker Hub payload vì thiếu branch. Webhook push Git có thể deploy image cũ trước khi build xong; không bật tự động này cho lần thử. Workflow chưa có bước chờ publish xong rồi deploy.

**Nếu muốn giữ Docker Hub trigger:** chọn provider **Raw**, dán nội dung `compose.production.yml`, dùng cùng Environment/domain, bật Autodeploy. Docker Hub → `nvminh162/hs-gateway-server` → Webhooks: thay URL Application cũ bằng URL **Compose mới** dạng `/api/deploy/compose/TOKEN`. Raw không tự cập nhật YAML từ Git; workflow push SHA và latest có thể gây hai delivery/deploy, không có lọc tag như Application Docker. Không gắn cả webhook Application cũ và Compose mới. Giữ kín webhook token.

[Dokploy Compose](https://docs.dokploy.com/docs/core/docker-compose) · [Compose webhook v0.31.4](https://github.com/Dokploy/dokploy/blob/v0.31.4/apps/dokploy/pages/api/deploy/compose/%5BrefreshToken%5D.ts)
