#!/usr/bin/env node
/**
 * GreenSense fake IoT fleet.
 *
 * Emulates ESP32 nodes reporting to the backend, so the whole pipeline — ingestion,
 * thresholds, rules, actuator commands, acks, alerts, WebSocket push — can be exercised
 * without soldering anything.
 *
 * Two transports:
 *   mqtt   (default) publishes to the broker and SUBSCRIBES to the command topic, so the
 *          node answers with an ack. This is the only mode that exercises the full
 *          round trip: rule fires → command published → device acks → state reconciled.
 *   http   posts to /readings/ingest instead. No broker and no dependencies needed, but
 *          commands cannot be acked because there is nothing listening.
 *
 *   node simulate.mjs --help
 */

import { setTimeout as sleep } from "node:timers/promises"

// ── Configuration ────────────────────────────────────────────────────────────────

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.replace(/^--/, "").split("=")
    return [k, v ?? true]
  })
)

if (args.help) {
  console.log(`
GreenSense device simulator

  node simulate.mjs [options]

  --transport=mqtt|http   default mqtt
  --api=URL               backend base URL          (default http://localhost:8080)
  --broker=URL            MQTT broker               (default mqtt://localhost:1883)
  --email=, --password=   login used to resolve the garden and register hardware
  --garden=ID             skip discovery, use this garden
  --device=CODE           node identifier           (default ESP32-A1)
  --interval=SECONDS      publish cadence           (default 10)
  --speed=N               simulated hours per real minute (default 24 — a full day
                          every minute, so daily curves are visible immediately)
  --scenario=NAME         normal | drought | heatwave | faulty | storm  (default normal)
  --once                  publish a single round and exit
  --provision             register the sensors/actuators first, then run

Scenarios
  normal    a pleasant day; nothing should fire
  drought   soil dries past 30% — the watering rule should start the pump
  heatwave  temperature climbs past 30°C and holds — the fan rule should fire
  faulty    the soil probe "loses its cable" and reports a rail value, which must NOT
            start the pump (it is flagged SUSPECT and automation is skipped)
  storm     light collapses, humidity spikes — the curtain rule should open up
`)
  process.exit(0)
}

const CONFIG = {
  transport: args.transport ?? "mqtt",
  api: (args.api ?? "http://localhost:8080").replace(/\/$/, ""),
  broker: args.broker ?? "mqtt://localhost:1883",
  email: args.email ?? "demo@greensense.vn",
  password: args.password ?? "Green@123",
  gardenId: args.garden ?? null,
  deviceCode: args.device ?? "ESP32-A1",
  intervalSec: Number(args.interval ?? 10),
  speed: Number(args.speed ?? 24),
  scenario: args.scenario ?? "normal",
  once: Boolean(args.once),
  provision: Boolean(args.provision),
  topicRoot: "greensense",
}

const log = (...m) => console.log(`[${new Date().toISOString().slice(11, 19)}]`, ...m)

// ── The simulated plot ───────────────────────────────────────────────────────────
//
// Each channel has a baseline, a daily swing, and a noise band. Values are generated
// from a virtual clock so a whole day passes in a minute at --speed=24 — otherwise you
// would wait 24 real hours to see a diurnal curve.

const CHANNELS = [
  { channel: "temp-1", type: "TEMPERATURE",  unit: "°C",  base: 26,   swing: 5,    noise: 0.4, peakHour: 14 },
  { channel: "hum-1",  type: "AIR_HUMIDITY", unit: "%",   base: 68,   swing: -12,  noise: 2,   peakHour: 14 },
  { channel: "soil-1", type: "SOIL_MOISTURE",unit: "%",   base: 48,   swing: -6,   noise: 1,   peakHour: 14 },
  { channel: "lux-1",  type: "LIGHT",        unit: "lux", base: 9000, swing: 9000, noise: 400, peakHour: 12, night: true },
  { channel: "ph-1",   type: "PH",           unit: "pH",  base: 6.3,  swing: 0.15, noise: 0.05, peakHour: 12 },
]

const ACTUATORS = [
  { channel: "pump-1",    type: "WATER_PUMP", name: "Bơm nước luống 1-2", maxRuntimeMinutes: 20, cooldownMinutes: 1 },
  { channel: "fan-1",     type: "FAN",        name: "Quạt tản nhiệt",     maxRuntimeMinutes: 120, cooldownMinutes: 1 },
  { channel: "curtain-1", type: "CURTAIN",    name: "Rèm che nắng",       maxRuntimeMinutes: 600, cooldownMinutes: 1 },
]

/** Where the virtual clock is, in fractional hours [0,24). */
let virtualHour = 6
/** Drifts under the `drought` scenario so the soil dries out over successive rounds. */
let soilDrift = 0
let tempDrift = 0

function advanceClock() {
  // speed = simulated hours per real minute
  virtualHour = (virtualHour + (CONFIG.intervalSec / 60) * (CONFIG.speed / 60)) % 24
}

function round(n, dp = 2) {
  return Math.round(n * 10 ** dp) / 10 ** dp
}

/** Bell-ish daily curve peaking at the channel's peakHour. */
function dailyFactor(peakHour) {
  return Math.cos(((virtualHour - peakHour) / 24) * 2 * Math.PI)
}

function valueFor(spec) {
  const noise = (Math.random() - 0.5) * 2 * spec.noise

  if (spec.type === "LIGHT") {
    // Dark before 06:00 and after 18:00; a raw cosine would give negative lux at night.
    if (virtualHour < 6 || virtualHour > 18) return round(Math.max(0, 20 + noise))
    const daylight = Math.sin(((virtualHour - 6) / 12) * Math.PI)
    return round(Math.max(0, spec.base * daylight + noise))
  }

  let value = spec.base + spec.swing * dailyFactor(spec.peakHour) + noise

  if (spec.type === "SOIL_MOISTURE") value -= soilDrift
  if (spec.type === "TEMPERATURE") value += tempDrift

  return round(value)
}

/** Scenario overrides applied on top of the baseline model. */
function applyScenario(readings) {
  switch (CONFIG.scenario) {
    case "drought":
      // Dry out ~1.5 %/round so the 30 % rule threshold is crossed within ~15 rounds.
      soilDrift += 1.5
      break

    case "heatwave":
      // Climb toward ~34 °C and stay there; the fan rule needs the condition SUSTAINED,
      // so a single hot sample deliberately does nothing.
      tempDrift = Math.min(tempDrift + 0.8, 8)
      break

    case "faulty": {
      // The cable falls out: the probe reads exactly 0 %. This looks like "bone dry" and
      // would start the pump — the backend must flag it SUSPECT and skip automation.
      const soil = readings.find((r) => r.channel === "soil-1")
      if (soil) soil.value = 0
      break
    }

    case "storm": {
      const lux = readings.find((r) => r.channel === "lux-1")
      const hum = readings.find((r) => r.channel === "hum-1")
      if (lux) lux.value = round(Math.max(0, lux.value * 0.08))
      if (hum) hum.value = round(Math.min(99, hum.value + 25))
      break
    }
  }
  return readings
}

function buildReadings() {
  return applyScenario(
    CHANNELS.map((spec) => ({ channel: spec.channel, value: valueFor(spec), type: spec.type }))
  )
}

// ── Backend API (login + provisioning + HTTP transport) ──────────────────────────

let accessToken = null

async function api(path, { method = "GET", body } = {}) {
  const res = await fetch(`${CONFIG.api}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  const json = text ? JSON.parse(text) : {}
  if (!res.ok) {
    throw new Error(`${method} ${path} → ${res.status} ${JSON.stringify(json.error ?? json)}`)
  }
  return json.data
}

async function login() {
  const data = await api("/api/v1/auth/login", {
    method: "POST",
    body: { email: CONFIG.email, password: CONFIG.password },
  })
  accessToken = data.accessToken
  log(`Logged in as ${data.user.email}`)
}

async function resolveGarden() {
  if (CONFIG.gardenId) return CONFIG.gardenId
  const gardens = await api("/api/v1/gardens")
  if (!gardens.length) {
    throw new Error("No gardens on this account. Run backend/http/01-provisioning.http first, or pass --garden=ID.")
  }
  log(`Using garden "${gardens[0].name}" (${gardens[0].id})`)
  return gardens[0].id
}

/** Registers this node's hardware. Ignores "already exists" so it is safe to re-run. */
async function provision(gardenId) {
  for (const spec of CHANNELS) {
    try {
      await api(`/api/v1/gardens/${gardenId}/sensors`, {
        method: "POST",
        body: {
          deviceCode: CONFIG.deviceCode,
          channel: spec.channel,
          type: spec.type,
          samplingIntervalSec: Math.max(30, CONFIG.intervalSec),
        },
      })
      log(`  + sensor ${spec.channel} (${spec.type})`)
    } catch (e) {
      if (!String(e.message).includes("SENSOR_EXISTS")) throw e
    }
  }

  for (const spec of ACTUATORS) {
    try {
      await api(`/api/v1/gardens/${gardenId}/actuators`, { method: "POST", body: { deviceCode: CONFIG.deviceCode, ...spec } })
      log(`  + actuator ${spec.channel} (${spec.type})`)
    } catch (e) {
      if (!String(e.message).includes("ACTUATOR_EXISTS")) throw e
    }
  }
}

// ── Transports ───────────────────────────────────────────────────────────────────

async function runHttp(gardenId) {
  const publish = async () => {
    const readings = buildReadings()
    const accepted = await api(`/api/v1/gardens/${gardenId}/readings/ingest`, {
      method: "POST",
      body: {
        readings: readings.map((r) => ({
          deviceCode: CONFIG.deviceCode,
          channel: r.channel,
          value: r.value,
          timestamp: new Date().toISOString(),
        })),
      },
    })
    log(`t=${virtualHour.toFixed(1)}h  ${readings.map((r) => `${r.channel}=${r.value}`).join("  ")}  (${accepted} accepted)`)
    advanceClock()
  }

  await publish()
  if (CONFIG.once) return

  log(`Publishing every ${CONFIG.intervalSec}s over HTTP. Ctrl-C to stop.`)
  log("Note: commands cannot be acked in HTTP mode — nothing is subscribed. Use --transport=mqtt for the full round trip.")
  // eslint-disable-next-line no-constant-condition
  while (true) {
    await sleep(CONFIG.intervalSec * 1000)
    await publish().catch((e) => log("publish failed:", e.message))
  }
}

async function runMqtt(gardenId) {
  let mqtt
  try {
    mqtt = (await import("mqtt")).default
  } catch {
    console.error(`
The 'mqtt' package is not installed.

    cd tools/device-simulator && npm install

...or run without a broker:

    node simulate.mjs --transport=http
`)
    process.exit(1)
  }

  const client = await mqtt.connectAsync(CONFIG.broker, {
    clientId: `sim-${CONFIG.deviceCode}-${Math.random().toString(16).slice(2, 8)}`,
    clean: true,
  })
  log(`Connected to ${CONFIG.broker}`)

  const base = `${CONFIG.topicRoot}/${gardenId}/${CONFIG.deviceCode}`

  // Subscribing to the command topic is what makes this a real device rather than a data
  // firehose: the server's optimistic state is only confirmed once we ack.
  await client.subscribeAsync(`${base}/command`)
  client.on("message", async (topic, payload) => {
    if (!topic.endsWith("/command")) return
    let cmd
    try {
      cmd = JSON.parse(payload.toString())
    } catch {
      return log("Ignoring unparseable command frame")
    }

    log(`<< COMMAND ${cmd.command} on ${cmd.channel}${cmd.durationMinutes ? ` for ${cmd.durationMinutes}m` : ""}`)

    // Real relays take a moment, and the server's ack timeout is 30s — a short delay here
    // proves the happy path; raise it past 30000 to rehearse a TIMEOUT.
    await sleep(300)

    const state = { TURN_ON: "ON", TURN_OFF: "OFF", OPEN: "OPEN", CLOSE: "CLOSED" }[cmd.command] ?? "OFF"
    await client.publishAsync(
      `${base}/ack`,
      JSON.stringify({ correlationId: cmd.correlationId, status: "OK", state })
    )
    log(`>> ACK ${cmd.correlationId?.slice(0, 8)} → ${state}`)
  })

  // Retained status so the server knows the node's health as soon as it subscribes.
  await client.publishAsync(
    `${base}/status`,
    JSON.stringify({ online: true, battery: 87, fw: "1.2.0" }),
    { retain: true }
  )

  const publish = async () => {
    const readings = buildReadings()
    // One publish carrying every channel: cheaper on a battery-powered node than five.
    await client.publishAsync(
      `${base}/telemetry`,
      JSON.stringify({
        samples: readings.map((r) => ({ channel: r.channel, value: r.value, ts: new Date().toISOString() })),
      })
    )
    log(`t=${virtualHour.toFixed(1)}h  ${readings.map((r) => `${r.channel}=${r.value}`).join("  ")}`)
    advanceClock()
  }

  await publish()
  if (CONFIG.once) {
    await sleep(500)
    await client.endAsync()
    return
  }

  log(`Publishing every ${CONFIG.intervalSec}s. Scenario: ${CONFIG.scenario}. Ctrl-C to stop.`)

  const shutdown = async () => {
    log("Going offline...")
    // A real node's last will would cover an ungraceful exit; this is the polite version.
    await client.publishAsync(`${base}/status`, JSON.stringify({ online: false }), { retain: true })
    await client.endAsync()
    process.exit(0)
  }
  process.on("SIGINT", shutdown)
  process.on("SIGTERM", shutdown)

  // eslint-disable-next-line no-constant-condition
  while (true) {
    await sleep(CONFIG.intervalSec * 1000)
    await publish().catch((e) => log("publish failed:", e.message))
  }
}

// ── Entry point ──────────────────────────────────────────────────────────────────

async function main() {
  log(`GreenSense simulator — transport=${CONFIG.transport} scenario=${CONFIG.scenario} device=${CONFIG.deviceCode}`)

  await login()
  const gardenId = await resolveGarden()

  if (CONFIG.provision) {
    log("Provisioning hardware...")
    await provision(gardenId)
  }

  if (CONFIG.transport === "http") {
    await runHttp(gardenId)
  } else {
    await runMqtt(gardenId)
  }
}

main().catch((e) => {
  console.error("\nSimulator failed:", e.message)
  process.exit(1)
})
