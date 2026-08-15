# GreenSense — Smart Garden IoT

Monitors a home garden's temperature, air humidity, soil moisture, light and soil pH,
waters and ventilates it automatically, and turns the pH reading into a fertiliser plan.

```
greenIOT/
├── frontend/                Next.js 16 + React 19 mobile-first UI (Vietnamese)
├── backend/                 Spring Boot 3.4 + MongoDB API, MQTT device link, STOMP push
│   ├── docs/ARCHITECTURE.md the object design (BCE), collections, contracts
│   └── http/                request collections incl. fake IoT device scenarios
├── tools/device-simulator/  a running fake ESP32 fleet
├── deploy/                  mosquitto config
└── .github/workflows/       build → GHCR → droplet
```

## Run it

```bash
# infrastructure + API
docker compose up -d mongodb mosquitto
cd backend && mvn spring-boot:run          # :8080, swagger at /swagger-ui.html

# frontend
cd frontend
cp .env.example .env.local
pnpm install && pnpm dev                   # :3000
```

Demo login on a fresh database: `demo@greensense.vn` / `Green@123`.

No broker to hand? Start the API with `--greensense.mqtt.enabled=false`; commands are
logged instead of published and everything else works.

## Seeing data move

```bash
cd tools/device-simulator && npm install
node simulate.mjs --provision --once     # register the node's sensors and actuators
node simulate.mjs                        # a full simulated day every real minute
node simulate.mjs --scenario=drought     # soil dries out → the pump rule fires → device acks
node simulate.mjs --scenario=faulty      # probe loses its cable → flagged, pump NOT started
```

Or drive it by hand from [`backend/http/`](backend/http/) — five `.http` files covering
auth, provisioning, device telemetry, every screen's reads, and the failure modes.

## How the halves connect

The frontend holds its access token in memory only; the refresh token is an HttpOnly
cookie the browser returns on its own. `lib/api/client.ts` attaches the Bearer, retries
once on 401 after a single-flight silent refresh, and unwraps the response envelope.

| Screen element | Endpoint |
|---|---|
| Sensor grid, hero counters, actuator pills | `GET /api/v1/gardens/{id}/dashboard` |
| Chart + 24H / 7D / 30D selector | `GET /readings/series` |
| `Thấp nhất` / `Cao nhất` / `↑ Tăng 2°C so với hôm qua` | `GET /readings/summary` |
| `Lịch sử kích hoạt tự động` | `GET /events` |
| Bell badge | `GET /alerts/unread-count` |
| pH card + fertiliser recommendation | `GET /soil/latest` |
| `Đánh dấu đã bón phân hôm nay` | `POST /soil/fertilizer` |
| `Lịch tưới` | `GET /schedules`, `POST /schedules/{id}/run-now` |
| `Xuất dữ liệu` | `GET /export/readings.csv` |

Live updates arrive over STOMP at `/ws` on
`/topic/garden/{id}/reading|actuator|alert|event`. The socket is server-push only —
every write goes by HTTP, so there is one auditable path with one auth mechanism.

## Documentation

- **[backend/docs/ARCHITECTURE.md](backend/docs/ARCHITECTURE.md)** — Boundary / Control /
  Entity breakdown, every MongoDB collection and field, the index plan, the MQTT and REST
  contracts, security model, tests, frontend integration and CI/CD.
- **[backend/README.md](backend/README.md)** — configuration, device protocol, how to
  exercise it by hand.
- **[backend/http/mqtt-scenarios.md](backend/http/mqtt-scenarios.md)** — driving the broker
  path, including the command → ack round trip and the fault cases.

## Verification status

| | |
|---|---|
| Backend | `mvn test` — **51 passing** against an embedded MongoDB |
| Frontend | `tsc --noEmit` clean, `next build` succeeds |
| Not verified | no live run against a real MongoDB or MQTT broker — Docker was unavailable on the machine this was built on |
