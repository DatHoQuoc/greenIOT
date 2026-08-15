# Driving the backend over MQTT by hand

The `.http` files use the HTTP ingest fallback because a REST client cannot speak MQTT.
Real hardware uses the broker, and only the broker path exercises the **command round
trip** — rule fires → server publishes a command → device acks → server reconciles state.

Everything below assumes `mosquitto-clients` (`apt install mosquitto-clients`, or
`docker exec -it greensense-mqtt sh`).

```bash
GARDEN=<gardenId from GET /api/v1/gardens>
DEVICE=ESP32-A1
ROOT=greensense/$GARDEN/$DEVICE
```

> Prefer something that runs itself? `tools/device-simulator` does all of this on a loop,
> with realistic daily curves and named fault scenarios.

---

## 1. Watch what the server sends your device

Leave this open in a second terminal. Nothing arrives until a rule fires or someone
presses a button in the app.

```bash
mosquitto_sub -h localhost -t "$ROOT/command" -v
```

## 2. Announce the node

Retained, so the server sees the node's health the moment it subscribes.

```bash
mosquitto_pub -h localhost -t "$ROOT/status" -r \
  -m '{"online":true,"battery":87,"fw":"1.2.0"}'
```

## 3. Publish one reading

```bash
mosquitto_pub -h localhost -t "$ROOT/telemetry" \
  -m '{"channel":"temp-1","value":27.4,"ts":"2026-08-12T07:00:00Z"}'
```

## 4. Publish every channel in one frame

What a battery-powered node actually does — one radio wake-up instead of five.

```bash
mosquitto_pub -h localhost -t "$ROOT/telemetry" -m '{
  "samples":[
    {"channel":"temp-1","value":28.4},
    {"channel":"hum-1","value":65.0},
    {"channel":"soil-1","value":41.2},
    {"channel":"lux-1","value":8400},
    {"channel":"ph-1","value":6.2}
  ]
}'
```

---

## 5. Trip the watering rule, then ack the command

Soil below 30 % fires the emergency-watering rule.

```bash
mosquitto_pub -h localhost -t "$ROOT/telemetry" \
  -m '{"channel":"soil-1","value":24.0}'
```

Your `mosquitto_sub` terminal now shows something like:

```json
{"correlationId":"7c1f...","channel":"pump-1","command":"TURN_ON","durationMinutes":10,"issuedAt":"..."}
```

Answer as the relay would. **Copy the real `correlationId`** — an ack that does not match
a live command is dropped:

```bash
mosquitto_pub -h localhost -t "$ROOT/ack" \
  -m '{"correlationId":"7c1f...","status":"OK","state":"ON"}'
```

`GET /api/v1/gardens/$GARDEN/commands` should now show that command as `ACKED`.

### The failure worth rehearsing

Ignore the command instead of acking it. After 30 seconds the sweep marks it `TIMEOUT`,
which is how a dead node becomes visible rather than silently leaving the UI showing a
pump that never started.

Or report that the relay refused:

```bash
mosquitto_pub -h localhost -t "$ROOT/ack" \
  -m '{"correlationId":"7c1f...","status":"ERROR","state":"OFF","error":"relay stuck"}'
```

The server trusts the device's reported state over its own optimistic guess and pushes
the correction to the app over WebSocket.

---

## 6. Fault scenarios

### Disconnected probe (the dangerous one)

A cable that falls out reads exactly 0 %, which looks like "bone dry" and would start the
pump. The value sits on the rail, so it is flagged `SUSPECT`: stored, alerted on, and
**excluded from automation**.

```bash
mosquitto_pub -h localhost -t "$ROOT/telemetry" -m '{"channel":"soil-1","value":0}'
```

### Physically impossible value

Flagged `BAD` — stored for diagnosis, drives nothing.

```bash
mosquitto_pub -h localhost -t "$ROOT/telemetry" -m '{"channel":"temp-1","value":-273.15}'
```

### Unregistered hardware

Dropped and logged. Hardware is never auto-provisioned from traffic; otherwise anyone who
can reach the broker could invent sensors in your garden.

```bash
mosquitto_pub -h localhost -t "greensense/$GARDEN/ESP32-GHOST/telemetry" \
  -m '{"channel":"temp-9","value":25.0}'
```

### Malformed frame

One bad publish must not stall the shared subscription for every other node.

```bash
mosquitto_pub -h localhost -t "$ROOT/telemetry" -m '{not json'
```

### Node goes offline

```bash
mosquitto_pub -h localhost -t "$ROOT/status" -r -m '{"online":false}'
```

Real firmware should register this as an MQTT **last will** so an ungraceful power cut
produces the same result. Even without it, the offline sweep flips a sensor to `OFFLINE`
after it misses three publish intervals.

---

## 7. Topic reference

| Topic | Direction | Payload |
|---|---|---|
| `greensense/{gardenId}/{deviceCode}/telemetry` | device → server | `{"channel","value","ts"}` or `{"samples":[…]}` |
| `greensense/{gardenId}/{deviceCode}/status` | device → server | `{"online","battery","fw"}` |
| `greensense/{gardenId}/{deviceCode}/command` | server → device | `{"correlationId","channel","command","durationMinutes"}` |
| `greensense/{gardenId}/{deviceCode}/ack` | device → server | `{"correlationId","status","state","error"}` |

`command` is one of `TURN_ON`, `TURN_OFF`, `OPEN`, `CLOSE`.

## 8. A note on device credentials

Devices authenticate to the **broker**, not to the API. The dev `mosquitto.conf` allows
anonymous access for convenience — on any public host that means anyone can publish fake
telemetry and drive your pumps. Set `allow_anonymous false`, add a `password_file`, and
give each node topic ACLs scoped to its own garden before exposing port 1883.
