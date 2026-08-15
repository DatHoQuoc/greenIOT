# GreenSense Backend

Spring Boot 3.4 (Java 21) + MongoDB backend for the GreenSense smart-garden app.
Full object design and rationale: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Quick start

```bash
# infrastructure
docker run -d -p 27017:27017 --name greensense-mongo mongo:7
docker run -d -p 1883:1883 --name greensense-mqtt eclipse-mosquitto:2 \
  mosquitto -c /mosquitto-no-auth.conf

# api
mvn spring-boot:run
```

- API base: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- WebSocket: `ws://localhost:8080/ws` (STOMP)
- Health: `http://localhost:8080/actuator/health`

Without a broker, add `--greensense.mqtt.enabled=false`.

**Demo account** (seeded on an empty database in the `dev` profile):
`demo@greensense.vn` / `Green@123`

## Layout — Boundary / Control / Entity

```
boundary/    rest · mqtt · ws · dto    what the outside world touches
control/                              use-case logic, transactions, policy
entity/                               @Document domain model + enums
repository/                           Spring Data gateways + aggregations
common/      config · security · exception · dto
bootstrap/   DataSeeder
```

Dependencies run one way only: `boundary → control → repository → entity`.
A controller that injects a repository, or a control that returns an entity to a
controller, is a design defect.

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/greensense` | database |
| `MQTT_BROKER_URL` | `tcp://localhost:1883` | broker |
| `MQTT_ENABLED` | `true` | set `false` to run without a broker |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | empty | broker credentials |
| `JWT_SECRET` | dev key | **must be overridden in production** (Base64, ≥256-bit) |
| `JWT_EXPIRATION_MINUTES` | `30` | access-token lifetime |
| `JWT_REFRESH_DAYS` | `30` | refresh-token lifetime = "stay signed in" duration |
| `REFRESH_COOKIE_SECURE` | `false` | **set `true` behind HTTPS** |
| `REFRESH_COOKIE_SAME_SITE` | `Lax` | `None` (+ secure) when the frontend is on another domain |
| `CORS_ORIGINS` | `http://localhost:3000` | comma-separated frontend origins |
| `WEATHER_ENABLED` | `false` | on = Open-Meteo lookups for `skipIfRainForecast` (no API key needed) |
| `SERVER_PORT` | `8080` | http port |

## Tests

```bash
mvn test
```

51 tests against an embedded MongoDB — no local server required. The first run downloads a
mongod binary (~100 MB); later runs use the cache. See §11 of the architecture doc for
what each test protects.

## Exercising it by hand

Config lives at the top of each `.http` file as `@baseUrl`, `@email`, `@password` — edit in
place, no environment file to pick. Values captured from responses (`{{accessToken}}`,
`{{gardenId}}`) still come from the response handlers, since they change every run.

| Where | What |
|---|---|
| [`http/00-auth.http`](http/00-auth.http) | login, silent refresh, profile, password change |
| [`http/01-provisioning.http`](http/01-provisioning.http) | build a garden: sensors, actuators, thresholds, rules, schedule |
| [`http/02-device-telemetry.http`](http/02-device-telemetry.http) | **fake devices reporting in** — happy path, threshold breach, rule firing, faulty probes, rejected input |
| [`http/03-operations.http`](http/03-operations.http) | every screen's read path |
| [`http/04-edge-cases.http`](http/04-edge-cases.http) | requests that are supposed to fail, and why |
| [`http/mqtt-scenarios.md`](http/mqtt-scenarios.md) | the same scenarios over the broker, including the command→ack round trip |
| [`../tools/device-simulator`](../tools/device-simulator) | a running fake ESP32 fleet |

```bash
cd tools/device-simulator && npm install
node simulate.mjs --provision --once      # register this node's hardware
node simulate.mjs --scenario=drought      # dry the soil out until the pump rule fires
node simulate.mjs --scenario=faulty       # probe "loses its cable" — must NOT start the pump
node simulate.mjs --transport=http        # no broker needed (commands cannot be acked)
```

## Device protocol (MQTT)

| Topic | Direction | Payload |
|---|---|---|
| `greensense/{gardenId}/{deviceCode}/telemetry` | device → server | `{"channel":"soil-1","value":24.3,"ts":"2026-08-12T07:00:00Z"}` or `{"samples":[…]}` |
| `greensense/{gardenId}/{deviceCode}/status` | device → server | `{"online":true,"battery":87,"fw":"1.2.0"}` |
| `greensense/{gardenId}/{deviceCode}/command` | server → device | `{"correlationId":"…","channel":"pump-1","command":"TURN_ON","durationMinutes":15}` |
| `greensense/{gardenId}/{deviceCode}/ack` | device → server | `{"correlationId":"…","status":"OK","state":"ON"}` |

A `deviceCode`/`channel` pair that is not registered as a `Sensor` or `Actuator` in that
garden is dropped and logged — hardware is never auto-provisioned from traffic.

Devices with no MQTT client can `POST /api/v1/gardens/{gid}/readings/ingest` instead.
