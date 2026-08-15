# Triển khai GreenSense lên droplet

Repo: `github.com/DatHoQuoc/greenIOT` (monorepo — backend và frontend chung một repo,
workflow lọc theo `paths` nên chỉ service nào đổi mới deploy).

Luồng: **GitHub Actions build → push image lên GHCR → SSH vào droplet → `docker compose pull`**.
Droplet không clone repo, không build gì cả — nó chỉ kéo image về chạy.

> **Lưu ý trước khi bắt đầu:** commit đầu tiên đã kích hoạt `deploy-backend` và
> `deploy-frontend`. Job `test` và `build-push` có thể đã xanh, nhưng job `deploy` **chắc
> chắn đỏ** vì chưa có secrets. Đó là bình thường — làm xong Bước 4 rồi chạy lại.

---

## Neo vào droplet visualedu có sẵn?

Được, và tiết kiệm được kha khá. Hai project chạy song song ở hai thư mục khác nhau
(`/opt/visualedu` và `/opt/greensense`) là **hai compose project riêng biệt** — mạng riêng,
volume riêng, container riêng. Không đụng nhau.

### Cổng: không đụng gì cả

Trên `visualedu-prod` (đã kiểm tra bằng `docker stats`) đang chạy: billing, auth, gateway,
ai-core, orchestrator, knowledge, rendering, sympy-sidecar, nginx, certbot, kafka, kafka-ui,
redis, zookeeper.

**Không có postgres, không có mongodb** — `docker-compose.yml` trong repo visualedu là bản
*dev*; production dùng database ngoài (managed). Nên:

| Cổng | GreenSense | |
|---|---|---|
| 8080 | backend | ✅ trống (gateway visualedu ở 8085) |
| 3000 | frontend | ✅ trống |
| 1883 | mosquitto | ✅ trống |
| 27017 | mongodb | ✅ trống |

Không cần đổi cổng nào.

### RAM — vừa, nhưng phải đặt trần

Hiện trạng: `total 7.8Gi`, `used 5.2Gi`, `available 2.5Gi`, **`Swap: 0B`**.

GreenSense xin ~1.2 GB (đã có trần trong compose):

| | trần |
|---|---|
| backend (`-Xmx448m`) | 768 MB |
| mongodb (`wiredTigerCacheSizeGB 0.5`) | 768 MB |
| frontend | 320 MB |
| mosquitto | 128 MB |

Lọt, còn dư ~1.3 GB. Nhưng **hai thứ phải làm trước**, không phải tuỳ chọn:

**1. Giới hạn cache của MongoDB.** WiredTiger mặc định lấy `50% × (RAM host − 1GB)`.
Container không bị giới hạn thì Mongo nhìn thấy RAM của **cả máy** — trên droplet 8 GB nó
sẽ phình dần tới ~3.4 GB và giết service của visualedu. `docker-compose.yml` đã có
`--wiredTigerCacheSizeGB 0.5` + `limits.memory`; đừng gỡ ra.

**2. Thêm swap.** `Swap: 0B` nghĩa là mọi spike đi thẳng tới OOM killer, và nó chọn nạn nhân
theo điểm số — thường là tiến trình JVM lớn nhất, tức `visualedu-ai-core` (615 MB), chứ
không phải thứ vừa gây ra spike. 2 GB swap là bảo hiểm rẻ nhất bạn mua được:

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl -w vm.swappiness=10          # chỉ dùng swap khi thật sự bí
echo 'vm.swappiness=10' >> /etc/sysctl.conf
```

### Hai cách lấy lại RAM nếu muốn thoải mái hơn

- **`kafka-ui` đang ăn 301 MB.** Đó là công cụ debug chạy trên production. Tắt khi không
  dùng: `docker stop visualedu-kafka-ui` — bật lại lúc cần.
- **Đẩy frontend lên Vercel** thay vì chạy trên droplet, đúng như visualedu đang làm (repo
  frontend của nó không có Dockerfile nào). Tiết kiệm 320 MB, thêm CDN, và bỏ luôn workflow
  `deploy-frontend`. Chỉ cần trỏ `NEXT_PUBLIC_API_BASE_URL` vào domain API.

`visualedu-kafka` một mình ăn 1.32 GB — chỉnh heap của nó xuống được nhiều, nhưng đó là
service đang chạy thật, đừng đụng vào lúc đang triển khai thứ khác.

### Dùng chung database của visualedu?

Không được nữa — production của nó không có Mongo trên máy này. GreenSense chạy Mongo
riêng, và như vậy lại tốt hơn: hai project độc lập vòng đời dữ liệu.

### Những bước được bỏ qua

Vì droplet đã dựng sẵn, bạn **bỏ Bước 1, 3, 4** ở dưới:

- Docker đã cài, đã có user deploy
- `GHCR_PAT` đã có sẵn — dùng lại đúng token đó
- SSH key CI đã có sẵn — dùng lại đúng key đó

Chỉ còn phải làm:

0. **Thêm swap 2 GB** (lệnh ở mục RAM phía trên) — làm trước tiên
1. `mkdir -p /opt/greensense/deploy/mosquitto` rồi copy config (**Bước 2**)
2. Tạo `/opt/greensense/.env` (**Bước 2**)
3. **Khai lại secrets trong repo `greenIOT`** (**Bước 5**) — secret không dùng chung giữa
   các repo, nhưng **giá trị thì y hệt**: copy `DROPLET_HOST`, `DROPLET_USER`,
   `DROPLET_SSH_KEY`, `GHCR_PAT` từ repo visualedu sang. (Nếu không copy lại được vì
   GitHub không cho xem secret cũ, lấy từ chỗ bạn lưu ban đầu, hoặc sinh key CI mới và
   thêm vào `authorized_keys` — key cũ vẫn giữ nguyên cho visualedu.)
4. `docker compose up -d mongodb mosquitto` rồi chạy workflow (**Bước 6**)

### Deploy đồng thời có an toàn không?

Có. visualedu khoá `/tmp/visualedu-deploy.lock`, GreenSense khoá
`/tmp/greensense-deploy.lock` — hai khoá khác nhau, nhưng vì chúng thao tác trên hai compose
project khác nhau nên chạy song song vẫn đúng. **Đừng đổi cho chúng dùng chung một khoá**:
làm vậy chỉ khiến deploy của bên này phải xếp hàng chờ bên kia mà chẳng ngăn được xung đột
nào.

### Reverse proxy — dùng lại nginx của visualedu

Droplet đã có `visualedu-nginx` + `visualedu-certbot` giữ cổng 80/443. **Đừng dựng Caddy hay
nginx thứ hai** — tiến trình khởi động sau sẽ chết vì không bind được cổng, và rất có thể nó
là cái đang phục vụ visualedu.

Thêm một `server` block mới vào config của nginx đó:

```nginx
server {
    listen 443 ssl;
    server_name greensense.example.vn;

    ssl_certificate     /etc/letsencrypt/live/greensense.example.vn/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/greensense.example.vn/privkey.pem;

    location /api/ {
        proxy_pass http://172.17.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket cho realtime — thiếu hai dòng Upgrade thì STOMP không bắt tay được
    # và app im lặng rơi về "chỉ cập nhật khi refetch".
    location /ws {
        proxy_pass http://172.17.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host       $host;
        proxy_read_timeout 3600s;
    }

    location / {
        proxy_pass http://172.17.0.1:3000;
        proxy_set_header Host $host;
    }
}
```

`172.17.0.1` là gateway của docker bridge — nginx nằm trong compose project khác nên không
gọi được `backend:8080` theo tên. Cách sạch hơn: cho `greensense-backend` join vào network
của visualedu-nginx rồi gọi bằng tên container.

Xin cert bằng certbot đã có:

```bash
docker exec visualedu-certbot certbot certonly --webroot \
  -w /var/www/certbot -d greensense.example.vn
```

---

## Bước 1 — Tạo và chuẩn bị droplet

> Bỏ qua bước này nếu neo vào droplet visualedu.

Tối thiểu **2 GB RAM**. Trên 1 GB thì MongoDB + JVM + Next.js sẽ OOM.
Ubuntu 24.04 LTS.

```bash
ssh root@<DROPLET_IP>

# Docker Engine + compose plugin
curl -fsSL https://get.docker.com | sh

# Tạo user riêng cho deploy — Actions không nên SSH bằng root
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy

mkdir -p /opt/greensense/deploy/mosquitto
chown -R deploy:deploy /opt/greensense
```

## Bước 2 — Copy file cấu hình lên droplet

Chạy **trên máy của bạn**, từ thư mục `greenIOT`:

```bash
scp docker-compose.yml deploy@<DROPLET_IP>:/opt/greensense/
scp deploy/mosquitto/mosquitto.conf deploy@<DROPLET_IP>:/opt/greensense/deploy/mosquitto/
```

Đường dẫn `deploy/mosquitto/mosquitto.conf` phải giữ đúng cấu trúc — `docker-compose.yml`
mount nó bằng đường dẫn tương đối.

### Tạo file `.env`

`docker-compose.yml` khai báo `JWT_SECRET: ${JWT_SECRET:?...}` nên compose sẽ **từ chối
khởi động** nếu thiếu. Đó là cố ý: một secret mặc định lọt lên production còn tệ hơn là
deploy fail.

Sinh khoá (chạy trên máy bạn):

```bash
openssl rand -base64 32
```

Rồi trên droplet:

```bash
ssh deploy@<DROPLET_IP>
cat > /opt/greensense/.env <<'EOF'
JWT_SECRET=<chuỗi base64 vừa sinh>
CORS_ORIGINS=https://greensense.example.vn
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
WEATHER_ENABLED=false
SPRING_PROFILES_ACTIVE=prod
EOF
chmod 600 /opt/greensense/.env
```

> **Ba biến này quyết định chuyện đăng nhập có chạy được hay không.** Xem Bước 7 trước khi
> chọn giá trị — nếu frontend và backend khác domain, `SameSite=Lax` sẽ khiến cookie
> refresh không bao giờ được gửi đi, và người dùng bị đá ra sau 30 phút mà không có lỗi
> nào hiện lên.

## Bước 3 — Cho droplet quyền kéo image từ GHCR

Package đẩy lên GHCR mặc định là **private**, nên droplet cần token đọc.

1. GitHub → Settings → Developer settings → **Personal access tokens (classic)** →
   Generate new token
2. Scope: chỉ cần **`read:packages`**
3. Copy token (dạng `ghp_...`) — sẽ dùng ở cả Bước 4

Test ngay trên droplet:

```bash
echo "<ghp_token>" | docker login ghcr.io -u DatHoQuoc --password-stdin
```

Phải thấy `Login Succeeded`.

## Bước 4 — SSH key cho GitHub Actions

Sinh **key riêng cho CI**, đừng dùng lại key cá nhân — key này nằm trong secret của repo và
bạn muốn thu hồi được nó độc lập.

Trên máy bạn:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/greensense_deploy -N "" -C "github-actions-greensense"
```

Đẩy public key lên droplet:

```bash
ssh-copy-id -i ~/.ssh/greensense_deploy.pub deploy@<DROPLET_IP>
# hoặc thủ công: dán nội dung .pub vào /home/deploy/.ssh/authorized_keys
```

Kiểm tra vào được không có mật khẩu:

```bash
ssh -i ~/.ssh/greensense_deploy deploy@<DROPLET_IP> "docker ps"
```

## Bước 5 — Khai báo secrets và variables trên GitHub

`github.com/DatHoQuoc/greenIOT` → **Settings → Secrets and variables → Actions**

### Tab *Secrets* → New repository secret

| Tên | Giá trị |
|---|---|
| `DROPLET_HOST` | IP droplet, ví dụ `139.59.x.x` |
| `DROPLET_USER` | `deploy` |
| `DROPLET_SSH_KEY` | **toàn bộ** nội dung `~/.ssh/greensense_deploy` (private key), gồm cả dòng `-----BEGIN...` và `-----END...` |
| `GHCR_PAT` | token `ghp_...` ở Bước 3 |

> `GITHUB_TOKEN` **không cần tạo** — Actions tự cấp, workflow dùng nó để *push* image.
> `GHCR_PAT` là để droplet *pull*, đó là lý do cần hai token khác nhau.

### Tab *Variables* → New repository variable

| Tên | Giá trị |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `https://api.greensense.example.vn` |

Đây là **variable chứ không phải secret**, vì `NEXT_PUBLIC_*` được nhúng thẳng vào bundle
JavaScript lúc build — ai mở DevTools cũng đọc được. Cất nó vào secret chỉ tạo cảm giác an
toàn giả.

Hệ quả: **đổi URL này phải rebuild image**, restart container không có tác dụng.

## Bước 6 — Khởi động lần đầu

Hạ tầng phải chạy trước, vì `docker compose up -d backend` chỉ kéo theo `mongodb`
(qua `depends_on`), không kéo `mosquitto`:

```bash
ssh deploy@<DROPLET_IP>
cd /opt/greensense
docker compose up -d mongodb mosquitto
docker compose ps        # mongodb phải "healthy"
```

Rồi chạy deploy từ GitHub: **Actions → deploy-backend → Run workflow → main**.

Theo dõi 3 job: `test` → `build-push` → `deploy`. Xong thì kiểm tra:

```bash
curl http://<DROPLET_IP>:8080/actuator/health
# {"status":"UP"}
```

Sau đó **Actions → deploy-frontend → Run workflow**, rồi mở `http://<DROPLET_IP>:3000`.

Từ giờ mỗi lần `git push` vào `main`:
- đụng `backend/**` → chỉ `deploy-backend` chạy
- đụng `frontend/**` → chỉ `deploy-frontend` chạy
- đụng cả hai → cả hai chạy, nhưng `flock` trên droplet đảm bảo chúng không giẫm lên nhau

---

## Bước 7 — HTTPS và cookie (đọc kỹ, đây là chỗ dễ sập nhất)

Refresh token đi bằng cookie `HttpOnly`. Ba tình huống:

| Bố trí | `REFRESH_COOKIE_SECURE` | `REFRESH_COOKIE_SAME_SITE` | HTTPS |
|---|---|---|---|
| Chỉ test bằng IP, HTTP | `false` | `Lax` | không cần |
| FE và BE **cùng** domain (`app.x.vn` + `app.x.vn/api`) | `true` | `Lax` | bắt buộc |
| FE và BE **khác** domain (`app.x.vn` + `api.x.vn`) | `true` | `None` | bắt buộc |

`SameSite=None` **bắt buộc đi kèm** `Secure=true`; trình duyệt sẽ âm thầm bỏ cookie nếu
thiếu. Triệu chứng: đăng nhập được, dùng 30 phút, rồi bị đá ra và không có lỗi nào ở
console.

Cách đơn giản nhất là đặt cả hai sau một reverse proxy cùng domain. Caddy tự lo Let's
Encrypt:

```
# /etc/caddy/Caddyfile
greensense.example.vn {
    handle /api/* {
        reverse_proxy localhost:8080
    }
    handle /ws* {
        reverse_proxy localhost:8080
    }
    handle {
        reverse_proxy localhost:3000
    }
}
```

Với bố trí này: `NEXT_PUBLIC_API_BASE_URL=https://greensense.example.vn`,
`CORS_ORIGINS=https://greensense.example.vn`, `SameSite=Lax`, `Secure=true`.

Nhớ mở firewall và **đóng cổng 8080/3000 ra ngoài** sau khi có proxy:

```bash
ufw allow 22,80,443/tcp
ufw enable
```

## Bước 8 — Trước khi coi là production

- [ ] **MQTT broker đang mở anonymous.** `deploy/mosquitto/mosquitto.conf` để
      `allow_anonymous true` cho tiện dev. Trên máy public, ai cũng có thể publish telemetry
      giả và bật máy bơm của bạn. Comment dòng đó, tạo `password_file`, và **đừng expose
      cổng 1883** ra internet nếu thiết bị nằm cùng mạng.
- [ ] Bỏ `ports: 27017` của mongodb khỏi `docker-compose.yml` — chỉ backend cần nói chuyện
      với nó qua mạng nội bộ của compose.
- [ ] Tài khoản demo `demo@greensense.vn / Green@123` chỉ được seed ở profile `dev`.
      Với `SPRING_PROFILES_ACTIVE=prod` nó không tồn tại — đăng ký tài khoản thật qua
      `POST /api/v1/auth/register`.
- [ ] Backup MongoDB: `docker exec greensense-mongodb mongodump --archive` theo cron.

---

## Xử lý sự cố

**Job `deploy` fail: `Permission denied (publickey)`**
`DROPLET_SSH_KEY` thiếu dòng `-----BEGIN`/`-----END`, hoặc bạn dán nhầm file `.pub`.
Phải là **private key**, dán nguyên vẹn.

**`docker compose pull` báo `denied` / `manifest unknown`**
`GHCR_PAT` hết hạn hoặc thiếu scope `read:packages`. Đăng nhập tay trên droplet để xác nhận.

**Job `deploy` timeout ở vòng chờ healthcheck**
Script in 100 dòng log cuối trước khi thoát. Thường là `JWT_SECRET` thiếu trong `.env`, hoặc
Mongo chưa healthy. Xem trực tiếp:
```bash
docker logs --tail 100 greensense-backend
```

**Frontend gọi API sai địa chỉ**
`NEXT_PUBLIC_API_BASE_URL` đã nhúng vào image cũ. Sửa variable rồi **chạy lại
`deploy-frontend`** — restart container không đổi được.

**Rollback**
Mỗi build có tag theo SHA commit:
```bash
cd /opt/greensense
docker compose stop backend
docker run -d --name greensense-backend --env-file .env \
  ghcr.io/dathoquoc/greensense-backend:<sha-cũ>
```
Hoặc sửa tag trong `docker-compose.yml` rồi `docker compose up -d backend`.
