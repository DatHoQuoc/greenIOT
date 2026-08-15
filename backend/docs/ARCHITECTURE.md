# GreenSense Backend — Architecture & Object Design

> Smart-garden IoT platform. Spring Boot 3.4 (Java 21) + MongoDB, MQTT for device traffic,
> REST + WebSocket for the Next.js client in [`frontend/`](../../frontend/).
>
> Design method: **BCE (Boundary–Control–Entity)**, the ICONIX/RUP robustness model.
> Every use case is realised by `Boundary → Control → Entity`. Boundaries never touch
> the database; entities never call services; all logic lives in controls.

---

## 1. What the system does

GreenSense turns a home/urban garden into a monitored, self-watering plot.

| Capability | Source in the UI |
|---|---|
| Read 5 environment metrics live | `frontend/app/page.tsx` sensor grid — temperature, air humidity, soil moisture, light, pH |
| Per-metric history with 24H / 7D / 30D / custom ranges | `app/sensor/[id]/page.tsx` range selector |
| Min / max / trend vs. yesterday | sensor hero card (`Thấp nhất`, `Cao nhất`, `↑ Tăng 2°C so với hôm qua`) |
| Warning thresholds drawn on charts | `Ngưỡng cảnh báo 30°C` dashed line |
| Actuator state: pump, curtain, fan | `automation-row` pills (`Bơm nước: Đang chạy`, `Rèm: Đóng`, `Quạt: Tắt`) |
| Threshold-driven automation with an audit trail | `Lịch sử kích hoạt tự động` timeline (`14:32 Quạt tản nhiệt tự động bật (nhiệt độ vượt 30°C)`) |
| Irrigation schedules | `Lịch tưới` tab |
| Alerts with unread badge | `Cảnh báo` tab + bell badge `2` |
| Soil pH analysis → fertiliser advice | `app/sensor/ph/page.tsx` — `pH 6.2 → Đất chua nhẹ → NPK 16-16-8 + Vôi bột, 200g vôi/m² + 50g NPK/m²` |
| Mark fertiliser applied | `Đánh dấu đã bón phân hôm nay` |
| Export data | `Xuất dữ liệu` |
| Master system on/off, multi-garden | hero toggle + `Vườn Nhà / Garden Outdoor`, `12 Sensors · 2 Pumps · Live` |

**Assumptions I made** (the shared conversation link was not machine-readable — it serves a
JS-only shell, so this design is derived from the frontend code):

1. Multi-tenant: a `User` owns one or more `Garden`s. Single-user demo still works.
2. Devices are ESP32-class nodes speaking **MQTT**; HTTP ingest is offered as a fallback.
3. pH is measured by a sensor but *may* also be entered manually (the UI says `Đo lần cuối: Hôm nay, 08:30`).
4. `12 Sensors / 2 Pumps` are counts derived from registered documents, not hardcoded.

---

## 2. Physical architecture

```
┌──────────────┐   MQTT/TLS    ┌──────────────────────────────────────────┐
│ ESP32 nodes  │──telemetry───▶│              MQTT Broker                 │
│ DHT22, cap.  │◀──command─────│           (Mosquitto / EMQX)             │
│ soil, BH1750,│               └────────────────┬─────────────────────────┘
│ pH probe,    │                                │ subscribe / publish
│ relays       │                                ▼
└──────────────┘                ┌───────────────────────────────────────┐
                                │      GreenSense Spring Boot API       │
       Next.js  ── REST ───────▶│  boundary ▸ control ▸ entity          │
       (frontend/) ◀─ STOMP/WS ─│                                       │
                                └────────────────┬──────────────────────┘
                                                 │ Spring Data MongoDB
                                                 ▼
                                ┌───────────────────────────────────────┐
                                │  MongoDB 7  (time-series + documents) │
                                └───────────────────────────────────────┘
```

**Why MongoDB fits:** sensor readings are append-only, time-ordered, high-volume and
schema-varying per sensor type — a textbook **time-series collection** with automatic
bucketing and a TTL. Configuration documents (gardens, rules, schedules) are small,
nested and read as whole aggregates, which suits documents better than joins.

---

## 3. Package layout (BCE made physical)

```
backend/src/main/java/com/greeniot/greensense/
│
├── GreenSenseApplication.java
│
├── boundary/                    ← BOUNDARY objects: everything the outside world touches
│   ├── rest/                    ·  HTTP controllers (client-facing)
│   ├── mqtt/                    ·  device ingress (telemetry/status) + egress (commands)
│   ├── ws/                      ·  STOMP push to the browser
│   └── dto/                     ·  request/response records + static mappers
│
├── control/                     ← CONTROL objects: use-case logic, transactions, policy
│
├── entity/                      ← ENTITY objects: @Document domain model (persistent state)
│   └── enums/
│
├── repository/                  ← entity gateways (Spring Data + custom aggregations)
│
├── common/
│   ├── config/                  ·  Security, Mongo, MQTT, WebSocket, OpenAPI
│   ├── security/                ·  JWT issue/verify, principal
│   ├── exception/               ·  domain exceptions + @RestControllerAdvice
│   └── dto/                     ·  ApiResponse envelope, PageResponse
│
└── bootstrap/                   ← DataSeeder (demo garden matching the UI)
```

**Dependency rule (enforced by review, one direction only):**

```
boundary ──▶ control ──▶ repository ──▶ entity
    │            │                          ▲
    └────────────┴──── dto ⇄ entity mapping ┘
```

A boundary that autowires a `Repository` is a design defect. A control that returns an
`Entity` straight to a boundary is tolerated only inside the mapper call.

---

## 4. ENTITY objects (MongoDB collections)

| Collection | Entity class | Kind | Purpose |
|---|---|---|---|
| `users` | `User` | config | account, role, notification prefs |
| `gardens` | `Garden` | config | plot, location, master switch, plant profile ref |
| `sensors` | `Sensor` | config | one physical probe: type, unit, calibration, health |
| `sensor_readings` | `SensorReading` | **time-series** | every measurement (bucketed, TTL 180d) |
| `actuators` | `Actuator` | config | pump / curtain / fan / grow-light + current state |
| `device_commands` | `DeviceCommand` | operational | outbound command + ack lifecycle |
| `automation_rules` | `AutomationRule` | config | `WHEN metric op value FOR n min → action` |
| `irrigation_schedules` | `IrrigationSchedule` | config | cron-like watering plan |
| `alerts` | `Alert` | operational | threshold breach / device offline, read state |
| `automation_events` | `AutomationEvent` | operational | audit trail behind the UI timeline |
| `soil_analyses` | `SoilAnalysis` | operational | pH snapshot + derived zone + advice |
| `fertilizer_applications` | `FertilizerApplication` | operational | "đã bón phân" log |
| `plant_profiles` | `PlantProfile` | reference | optimal ranges per crop, seeds the thresholds |
| `refresh_tokens` | `RefreshToken` | operational | hashed, rotating session tokens |
| `sensor_daily_stats` | `SensorDailyStat` | rollup | one row per sensor per day; outlives the raw TTL |

### 4.1 Field detail

**`User`**
```
id, email(unique), passwordHash, fullName, role(OWNER|MEMBER|ADMIN),
phone, pushTokens[], notifyByPush, notifyByEmail, quietHoursStart/End,
enabled, createdAt, updatedAt
```

**`Garden`**
```
id, ownerId→users, name("Vườn Nhà"), description("Garden Outdoor"),
type(OUTDOOR|GREENHOUSE|BALCONY|HYDROPONIC), areaSqm, timezone("Asia/Ho_Chi_Minh"),
location{lat,lng,address}, plantProfileId→plant_profiles,
systemEnabled(bool)                     ← the hero toggle: kills ALL automation
thresholds: Map<SensorType, Threshold>  ← per-garden override of profile defaults
members: [{userId, email, fullName, addedAt}]   ← household members
createdAt, updatedAt
```

> **Two access levels, and picking the wrong one is a security bug.** A *member* may read
> the garden and operate it — press the pump, acknowledge an alert, mark fertiliser, hit
> the master switch. Only the *owner* may change what the garden **is**: hardware registry,
> rules, schedules, thresholds, membership, deletion. `GardenControl.requireAccess` and
> `requireOwner` are the two gates; every boundary calls exactly one of them.

**`Threshold`** (embedded value object)
```
min, max, warnLow, warnHigh, unit
// e.g. TEMPERATURE {min:18, max:32, warnLow:15, warnHigh:30, unit:"°C"}
//      SOIL_MOISTURE {min:35, max:70, warnLow:30, warnHigh:80, unit:"%"}
```

**`Sensor`**
```
id, gardenId, deviceCode("ESP32-A1"), channel("soil-1"),
type(TEMPERATURE|AIR_HUMIDITY|SOIL_MOISTURE|LIGHT|PH),
name, unit("°C"|"%"|"lux"|"pH"), status(ONLINE|OFFLINE|FAULTY|CALIBRATING),
lastValue, lastReadingAt, batteryLevel, firmwareVersion,
calibration{offset, scale}, samplingIntervalSec, enabled
```
> Compound unique index `(gardenId, deviceCode, channel)` — a physical probe registers once.

**`SensorReading`** — *time-series collection*
```
id, timestamp(timeField), meta{gardenId, sensorId, type}(metaField),
value(double), unit, quality(GOOD|SUSPECT|BAD)
```
> `granularity: minutes`, `expireAfterSeconds: 15552000` (180 days). Raw points age out;
> daily rollups survive in `sensor_daily_stats` if you later need multi-year history.

**`Actuator`**
```
id, gardenId, deviceCode, channel, type(WATER_PUMP|CURTAIN|FAN|GROW_LIGHT|VALVE),
name("Bơm nước"), state(ON|OFF|OPEN|CLOSED|ERROR), mode(AUTO|MANUAL),
lastChangedAt, lastChangedBy(SYSTEM|USER|SCHEDULE|RULE),
maxRuntimeMinutes(safety cap), cooldownMinutes, enabled
```

**`AutomationRule`**
```
id, gardenId, name("Quạt tản nhiệt"), enabled, priority,
condition{ sensorType, operator(GT|GTE|LT|LTE|BETWEEN|OUTSIDE),
           value, secondValue, sustainedForMinutes }
action{ actuatorId | actuatorType, command(TURN_ON|TURN_OFF|OPEN|CLOSE),
        durationMinutes, raiseAlert(bool), alertSeverity }
cooldownMinutes, activeFrom/activeTo (time-of-day window),
lastTriggeredAt, triggerCount
```
> The UI event `Quạt tản nhiệt tự động bật (nhiệt độ vượt 30°C)` is exactly
> `{TEMPERATURE, GT, 30, sustained 5m} → {FAN, TURN_ON}`.

**`IrrigationSchedule`**
```
id, gardenId, actuatorId, name, enabled,
daysOfWeek[MON..SUN], startTime("06:00"), durationMinutes(15),
skipIfSoilMoistureAbove(60), skipIfRainForecast(bool),
nextRunAt, lastRunAt, lastRunStatus(SUCCESS|SKIPPED|FAILED), lastSkipReason
```

**`Alert`**
```
id, gardenId, sensorId?, actuatorId?, ruleId?,
severity(INFO|WARNING|CRITICAL), status(OPEN|ACKNOWLEDGED|RESOLVED),
code("SOIL_MOISTURE_LOW"), title, message, triggerValue, thresholdValue,
read(bool), raisedAt, acknowledgedAt, resolvedAt
```

**`AutomationEvent`** — the timeline
```
id, gardenId, occurredAt, source(RULE|SCHEDULE|USER|SYSTEM|DEVICE),
category(ACTUATOR_CHANGE|ALERT|CHECK|ADVICE|FERTILIZER),
title("Quạt tản nhiệt tự động bật"), detail("nhiệt độ vượt 30°C"),
actuatorId?, ruleId?, sensorId?, tone(GREEN|TEAL|GRAY)
```
> `tone` is a presentation hint the frontend timeline already uses (`<i className="green"/>`).
> Keeping it server-side means the client stays dumb.

**`SoilAnalysis`**
```
id, gardenId, measuredAt, ph, source(SENSOR|MANUAL),
zone(STRONGLY_ACIDIC|SLIGHTLY_ACIDIC|NEUTRAL|ALKALINE),
zoneLabel("Đất chua nhẹ"),
recommendation{ title("Phân NPK 16-16-8 + Vôi bột"), rationale,
                dosage("200g vôi/m² + 50g NPK/m²"), frequency("1 lần/tháng"),
                alternatives["Phân hữu cơ vi sinh","Phân lân Super"] }
```

**`FertilizerApplication`**
```
id, gardenId, userId, appliedAt(date), fertilizerName, dosage, note,
soilAnalysisId?    // unique index (gardenId, appliedAt-day) → idempotent "mark today"
```

**`PlantProfile`** (reference data)
```
id, name("Rau ăn lá"), scientificName, category,
optimal: Map<SensorType, Threshold>, phZonePreference, notes
```

**`DeviceCommand`**
```
id, gardenId, actuatorId, deviceCode, command, payload,
status(PENDING|SENT|ACKED|FAILED|TIMEOUT), issuedBy, issuedAt, sentAt,
ackedAt, retryCount, errorMessage, correlationId
```

### 4.2 Index plan

```js
users:                 { email: 1 } unique
gardens:               { ownerId: 1 }
sensors:               { gardenId: 1, deviceCode: 1, channel: 1 } unique
                       { gardenId: 1, type: 1 }
sensor_readings:       timeseries(timeField=timestamp, metaField=meta, granularity=minutes)
                       TTL expireAfterSeconds = 15552000
actuators:             { gardenId: 1, type: 1 }
automation_rules:      { gardenId: 1, enabled: 1 }
irrigation_schedules:  { enabled: 1, nextRunAt: 1 }
alerts:                { gardenId: 1, read: 1, raisedAt: -1 }
automation_events:     { gardenId: 1, occurredAt: -1 }
soil_analyses:         { gardenId: 1, measuredAt: -1 }
fertilizer_applications:{ gardenId: 1, appliedAt: 1 } unique
device_commands:       { correlationId: 1 } unique, { status: 1, issuedAt: 1 }
```

---

## 5. CONTROL objects

| Control | Owns | Key operations |
|---|---|---|
| `AuthControl` | registration, login, session rotation, account self-service | `register`, `login`, `refresh`, `logout`, `updateProfile`, `changePassword` |
| `RefreshTokenControl` | rotating refresh tokens with reuse detection | `issue`, `rotate`, `revoke`, `revokeAllForUser` |
| `NotificationControl` | who actually gets pinged | `notifyAlert` — honours `notifyByPush`/`notifyByEmail`, and quiet hours for non-CRITICAL |
| `AutomationRuleControl` | rule CRUD + validation | `create`, `update`, `setEnabled`, `delete` |
| `DailyRollupControl` | history that outlives the TTL | `@Scheduled` 00:20 → one `SensorDailyStat` per sensor per day |
| `GardenControl` | garden CRUD, master switch, dashboard assembly | `getDashboard(gardenId)` composes sensors + actuators + alert count + latest soil |
| `SensorControl` | sensor registry & health | `register`, `list`, `markOffline` (scheduled sweep) |
| `TelemetryIngestControl` | **the hot path** | validate → calibrate → persist reading → update `Sensor.lastValue` → push WS → hand to `RuleEngineControl` → hand to `AlertControl` |
| `ReadingAnalyticsControl` | history & statistics | `series(sensorId, from, to, bucket)`, `summary` (current/min/max/avg + delta vs previous period) |
| `RuleEngineControl` | evaluate `AutomationRule`s | `evaluate(reading)` — checks enabled, garden master switch, time window, sustained duration, cooldown → emits command + `AutomationEvent` |
| `ActuatorControl` | actuator state machine | `command(actuatorId, cmd, source)` → persists `DeviceCommand` → MQTT publish → optimistic state → reconcile on ack; enforces `maxRuntimeMinutes` + `cooldownMinutes` |
| `IrrigationScheduleControl` | `@Scheduled` every minute | due schedules → skip checks (soil moisture) → `ActuatorControl.command` → auto-off after duration |
| `AlertControl` | raise / dedupe / acknowledge | `raise(...)` suppresses duplicates inside a cooldown window; `unreadCount`, `markRead`, `acknowledge` |
| `AutomationEventControl` | timeline writer/reader | `record(...)`, `timeline(gardenId, limit)` |
| `SoilAdvisoryControl` | pH → zone → fertiliser | `analyse(ph)` pure function + persistence; `markFertilizerApplied` |
| `ExportControl` | CSV/JSON export | `exportReadings(gardenId, type, from, to, format)` streams CSV |
| `DeviceCommandControl` | ack matching, timeout sweep | `onAck(correlationId)`, `@Scheduled` timeout of `SENT` older than 30s |

### 5.1 pH → advice algorithm (`SoilAdvisoryControl`)

Mirrors the reference scale already rendered in `app/sensor/ph/page.tsx`:

| pH range | zone | label | recommendation |
|---|---|---|---|
| `< 5.5` | `STRONGLY_ACIDIC` | Đất chua nhiều | Vôi dolomite 500g/m² + phân hữu cơ, 1 lần/tháng |
| `5.5 – 6.5` | `SLIGHTLY_ACIDIC` | Đất chua nhẹ | **NPK 16-16-8 + Vôi bột — 200g vôi/m² + 50g NPK/m², 1 lần/tháng** |
| `6.5 – 7.3` | `NEUTRAL` | Đất trung tính | NPK 20-20-15 duy trì, 80g/m², 1 lần/tháng |
| `> 7.3` | `ALKALINE` | Đất kiềm | Lưu huỳnh + phân hữu cơ hạ pH, 100g S/m² |

The `5.5–6.5 → NPK 16-16-8 + Vôi bột, 200g vôi/m² + 50g NPK/m²` row is the one the
current UI hardcodes for pH 6.2; the backend now derives it.

### 5.2 Rule evaluation sequence

```
Device ──MQTT telemetry──▶ MqttTelemetryBoundary
                              │ parse + auth by deviceCode
                              ▼
                        TelemetryIngestControl
                              │ 1. resolve Sensor, apply calibration
                              │ 2. save SensorReading           ──▶ sensor_readings
                              │ 3. update Sensor.lastValue      ──▶ sensors
                              │ 4. RealtimeBoundary.pushReading ──▶ browser (STOMP)
                              ├─▶ AlertControl.checkThresholds ──▶ alerts + WS badge
                              └─▶ RuleEngineControl.evaluate
                                        │ garden.systemEnabled? cooldown? sustained?
                                        ▼
                                  ActuatorControl.command(RULE)
                                        │
                                        ├─▶ device_commands (PENDING)
                                        ├─▶ MqttCommandBoundary.publish ──▶ device
                                        └─▶ AutomationEventControl.record
                                                  └──▶ "14:32 Quạt tản nhiệt tự động bật"
```

---

## 6. BOUNDARY objects

### 6.1 REST boundaries

Base path `/api/v1`. All responses wrapped in `ApiResponse<T>{success, data, error, timestamp}`.

| Boundary | Route | Endpoints |
|---|---|---|
| `AuthBoundary` | `/auth` | `POST /register`, `POST /login`, `POST /refresh`, `POST /logout`, `GET /me`, `PUT /me`, `PATCH /me/password`, `POST|DELETE /me/push-tokens` |
| `GardenBoundary` | `/gardens` | `GET`, `POST`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`, `GET /{id}/dashboard`, `PATCH /{id}/system` (master toggle), `PUT /{id}/thresholds`, `POST /{id}/members`, `DELETE /{id}/members/{userId}` |
| `PlantProfileBoundary` | `/plant-profiles` | `GET`, `GET /{id}` — so a client can discover the `plantProfileId` that `POST /gardens` accepts |
| `DeviceCommandBoundary` | `/gardens/{gid}/commands` | `GET` — the PENDING → SENT → ACKED/TIMEOUT trail |
| `SensorBoundary` | `/gardens/{gid}/sensors` | `GET`, `POST`, `GET /{sid}`, `PUT /{sid}`, `DELETE /{sid}` |
| `ReadingBoundary` | `/gardens/{gid}/readings` | `GET /latest`, `GET /series?type=&from=&to=&bucket=`, `GET /summary?type=&range=24H\|7D\|30D`, `POST /ingest` (HTTP fallback for devices) |
| `ActuatorBoundary` | `/gardens/{gid}/actuators` | `GET`, `POST`, `PUT /{aid}`, `POST /{aid}/command`, `PATCH /{aid}/mode` |
| `AutomationRuleBoundary` | `/gardens/{gid}/rules` | `GET`, `POST`, `PUT /{rid}`, `DELETE /{rid}`, `PATCH /{rid}/enabled` |
| `IrrigationScheduleBoundary` | `/gardens/{gid}/schedules` | `GET`, `POST`, `PUT /{sid}`, `DELETE /{sid}`, `POST /{sid}/run-now` |
| `AlertBoundary` | `/gardens/{gid}/alerts` | `GET?status=&unreadOnly=&page=&size=`, `GET /unread-count`, `PATCH /{aid}/read`, `PATCH /{aid}/acknowledge`, `POST /read-all` |
| `GardenBoundary` (timeline) | `/gardens/{gid}/events` | `GET?limit=&sensorId=` |
| `SoilBoundary` | `/gardens/{gid}/soil` | `GET /latest`, `GET /history`, `POST /analyze` (manual pH), `GET /ph-scale`, `POST /fertilizer` (mark applied), `GET /fertilizer/today`, `DELETE /fertilizer/today`, `GET /fertilizer/history` |
| `ExportBoundary` | `/gardens/{gid}/export` | `GET /readings.csv`, `GET /readings.json` (both `?type=&from=&to=`) |

> The timeline lives on `GardenBoundary` rather than its own class: it is a read of the
> garden aggregate with no state of its own, and a separate controller for one `GET` would
> add a file without adding a seam.

### 6.2 Device boundaries (MQTT)

| Topic | Direction | Payload |
|---|---|---|
| `greensense/{gardenId}/{deviceCode}/telemetry` | device → server | `{"channel":"soil-1","type":"SOIL_MOISTURE","value":24.3,"ts":169...}` (or array) |
| `greensense/{gardenId}/{deviceCode}/status` | device → server | `{"online":true,"battery":87,"fw":"1.2.0"}` |
| `greensense/{gardenId}/{deviceCode}/command` | server → device | `{"correlationId":"...","channel":"pump-1","command":"TURN_ON","durationMinutes":15}` |
| `greensense/{gardenId}/{deviceCode}/ack` | device → server | `{"correlationId":"...","status":"OK","state":"ON"}` |

### 6.3 Realtime boundary (STOMP over WebSocket)

Handshake `/ws`, app prefix `/app`, broker prefix `/topic`.

| Destination | Emitted when |
|---|---|
| `/topic/garden/{gid}/reading` | every accepted telemetry point |
| `/topic/garden/{gid}/actuator` | actuator state change |
| `/topic/garden/{gid}/alert` | alert raised (drives the bell badge) |
| `/topic/garden/{gid}/event` | timeline entry appended |

---

## 7. Security

- **Access token**: stateless JWT (`Authorization: Bearer`), HS256, 30 minutes. Held in
  browser memory only — never `localStorage`, where any XSS could lift it.
- **Refresh token**: opaque 64-byte random, stored **SHA-256 hashed**, delivered as an
  `HttpOnly` cookie scoped to `/api/v1/auth`. A database dump therefore yields no usable
  sessions, and page scripts cannot read the cookie at all.
- **Rotation with reuse detection**: every refresh mints a new token and revokes the
  presented one. Presenting an already-spent token means a copy escaped, so the entire
  token family for that user is revoked and the session dies. Changing a password does the
  same — a password changed *because it leaked* must not leave the thief's session alive.
- **401 vs 403 is not cosmetic.** Spring Security answers an anonymous request with 403 by
  default, which is indistinguishable from a genuine denial; a client then cannot tell
  "refresh and retry" from "stop". `RestAuthEntryPoints` splits them, and the frontend's
  silent-refresh retry depends on that split.
- **Two authorisation gates**: `requireAccess` (owner or member) for reads and operation,
  `requireOwner` for configuration. Enforced in `GardenControl`, called from every boundary.
- **Unknown ids answer 404, not 403** — a 403 would confirm the id exists and let an
  attacker enumerate other people's gardens.
- Devices authenticate to the **broker**, not the API (per-device MQTT username/password or client cert). The server trusts the topic's `deviceCode` only after resolving it to a registered `Sensor`/`Actuator` in that garden; unknown codes are dropped and logged.
- Passwords: BCrypt strength 10.
- CORS: origins from `greensense.cors.allowed-origins` (default `http://localhost:3000`).

---

## 8. Cross-cutting

- **Errors**: `GlobalExceptionHandler` maps `ResourceNotFoundException`→404, `BusinessRuleException`→409, `MethodArgumentNotValidException`→400 with field errors, `AccessDeniedException`→403, everything else→500 with a correlation id (never a stack trace to the client).
- **Scheduling**: `@EnableScheduling` drives `IrrigationScheduleControl.tick()` (1 min), `SensorControl.sweepOffline()` (5 min, marks sensors silent > 3× their sampling interval), `DeviceCommandControl.timeoutSweep()` (30 s), `ActuatorControl.enforceMaxRuntime()` (1 min).
- **Auditing**: `@EnableMongoAuditing` populates `createdAt`/`updatedAt` on `BaseDocument`.
- **Config profiles**: `dev` (embedded-friendly, seeder on, verbose logging), `prod` (seeder off, TLS MQTT).

---

## 9. Traceability — UI element → objects

| Frontend | Boundary | Control | Entity |
|---|---|---|---|
| Sensor grid (5 tiles) | `GET /gardens/{id}/dashboard` | `GardenControl` | `Sensor`, `SensorReading` |
| Hero `12 Sensors · 2 Pumps` | same | `GardenControl` | count of `Sensor` / `Actuator` |
| Hero on/off toggle | `PATCH /gardens/{id}/system` | `GardenControl` | `Garden.systemEnabled` |
| Range selector 24H/7D/30D | `GET /readings/series` | `ReadingAnalyticsControl` | `SensorReading` |
| `Thấp nhất / Cao nhất / ↑ Tăng 2°C` | `GET /readings/summary` | `ReadingAnalyticsControl` | `SensorReading` |
| `Ngưỡng cảnh báo 30°C` line | dashboard payload | `GardenControl` | `Garden.thresholds` |
| Pump/curtain/fan pills | dashboard payload | `ActuatorControl` | `Actuator.state` |
| `Lịch sử kích hoạt tự động` | `GET /gardens/{id}/events` | `AutomationEventControl` | `AutomationEvent` |
| Bell badge `2` | `GET /alerts/unread-count` | `AlertControl` | `Alert.read` |
| `Lịch tưới` tab | `GET /schedules` | `IrrigationScheduleControl` | `IrrigationSchedule` |
| pH hero + zone badge | `GET /soil/latest` | `SoilAdvisoryControl` | `SoilAnalysis` |
| Fertiliser recommendation card | same | `SoilAdvisoryControl` | `SoilAnalysis.recommendation` |
| `Đánh dấu đã bón phân hôm nay` | `POST /soil/fertilizer` | `SoilAdvisoryControl` | `FertilizerApplication` |
| `Xuất dữ liệu` | `GET /export/readings.csv` | `ExportControl` | `SensorReading` |

---

## 10. Running it

```bash
cd backend
docker run -d -p 27017:27017 --name greensense-mongo mongo:7
docker run -d -p 1883:1883 --name greensense-mqtt eclipse-mosquitto:2 \
  mosquitto -c /mosquitto-no-auth.conf
mvn spring-boot:run             # http://localhost:8080/swagger-ui.html
```

Seeded demo login: `demo@greensense.vn` / `Green@123` — one garden `Vườn Nhà` with 12
sensors, 2 pumps, a fan, a curtain, the 30 °C fan rule, a 06:00 irrigation schedule and
seven days of hourly readings, so the frontend has real data to bind to.

No broker to hand? Start with `--greensense.mqtt.enabled=false`; the API runs normally and
commands are logged instead of published.

---

## 11. Tests

`mvn test` runs against an **embedded MongoDB** (flapdoodle), so no local server is needed.

**51 tests, all passing.**

| Test | What it protects |
|---|---|
| `ApplicationContextSmokeTest` | the whole context boots; every control and repository resolves; each derived query name is validated against entity metadata (these only fail when the repository proxy is built, never at compile time) |
| `TelemetryPipelineTest` | the real ingestion path: a 31.5 °C reading raises an alert, fires the rule, flips the fan to `ON` with `lastChangedBy=RULE`, and writes the timeline line `Quạt tản nhiệt tự động bật / nhiệt độ vượt 30°C`; a 27 °C reading changes nothing; the master switch suppresses automation while still storing the reading; unregistered channels are dropped; a pH 6.2 reading yields `Đất chua nhẹ → NPK 16-16-8 + Vôi bột, 200g vôi/m² + 50g NPK/m²` |
| `AuthFlowTest` | the refresh cookie is HttpOnly and never in a body; rotation works; **replaying a spent token kills the whole family**; logout and password change revoke; anonymous is 401 not 403 |
| `GardenAccessControlTest` | the authorisation matrix — a stranger cannot distinguish an existing garden from a missing one; a member can read and hit the emergency stop but cannot register hardware, edit rules, change thresholds, invite people or delete the garden; revoking a member is immediate |
| `IrrigationScheduleControlTest` | `computeNextRun` across day filters and midnight; wet soil and the master switch skip a run; **`nextRunAt` always advances even on a skip** (otherwise a skipped schedule re-fires forever); "run now" overrides both guards |
| `MqttTelemetryBoundaryTest` | frame parsing — single and batched samples, status, ack success and failure; malformed JSON, missing fields, unknown topic shapes and absent headers are all swallowed rather than stalling the shared subscription |
| `DashboardContractTest` | pins the JSON field names `frontend/lib/api/types.ts` is written against. The two sides are separate builds; nothing else stops a rename here turning a value into `undefined` on a phone screen |

The first run downloads a mongod binary (~100 MB) and is slow; later runs use the cache.

---

## 12. Frontend integration

The Next.js app in [`frontend/`](../../frontend/) talks to this API through a layer modelled
on the same client the admin panel uses:

```
frontend/lib/auth/tokenMemory.ts     access token, module scope, never storage
frontend/lib/auth/refreshToken.ts    silent refresh, single-flight
frontend/lib/api/client.ts           fetch wrapper: Bearer + 401-retry + envelope unwrap
frontend/lib/api/<domain>/*Api.ts    one module per domain, plain exported functions
frontend/lib/api/realtime.ts         STOMP subscriptions for one garden
frontend/hooks/use*.ts               async data hooks: {data, isLoading, error, refetch}
```

Two details are load-bearing:

- **Single-flight refresh.** The refresh token rotates, so two concurrent 401s must not
  both call `/refresh` — the second would present an already-spent cookie and the backend
  would (correctly) treat it as theft and end the session. `refreshToken.ts` shares one
  in-flight promise across all callers.
- **Bootstrap order.** On a cold load there is no token in memory by design, so
  `SessionProvider` refreshes *first*, then fetches profile and gardens. Doing it the other
  way round means the first two requests always 401 and each triggers its own refresh —
  the same double-use again.

---

## 13. CI/CD

Mirrors the visualedu deploy shape: build → push to GHCR → SSH + `docker compose pull/up`
under a `flock`, with `concurrency.cancel-in-progress: false` so a release is never
cancelled halfway.

| Workflow | Trigger | Jobs |
|---|---|---|
| `deploy-backend.yml` | push to `main` touching `backend/**` | **test** → build-push → deploy |
| `deploy-frontend.yml` | push to `main` touching `frontend/**` | build-push → deploy |
| `pr-checks.yml` | pull request | backend `mvn verify`, frontend `tsc --noEmit` + `next build` |

Two deliberate departures from the reference workflows:

- **A test gate before the image build.** The Dockerfile builds with `-DskipTests` to stay
  fast, which means the `test` job is the only thing between a red test and production.
- **Wait on the container `HEALTHCHECK`, not `sleep 20`.** Spring Boot start-up varies with
  droplet load, and a sleep long enough today silently becomes too short later.
