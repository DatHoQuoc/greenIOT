// lib/api/types.ts
// Wire types shared by every API module. These mirror the BE DTOs 1:1 — BE serialises
// camelCase, and the FE uses camelCase, so unlike the staff panel there is NO snake_case
// mapping layer here. If a field name diverges, fix it at the BE DTO rather than adding a
// translation step that later readers have to keep in their head.

export type SensorType = "TEMPERATURE" | "AIR_HUMIDITY" | "SOIL_MOISTURE" | "LIGHT" | "PH"
export type SensorSlug = "temperature" | "air-humidity" | "soil-moisture" | "light" | "ph"
export type SensorStatus = "ONLINE" | "OFFLINE" | "FAULTY" | "CALIBRATING"

export type ActuatorType = "WATER_PUMP" | "CURTAIN" | "FAN" | "GROW_LIGHT" | "VALVE"
export type ActuatorState = "ON" | "OFF" | "OPEN" | "CLOSED" | "ERROR"
export type ActuatorMode = "AUTO" | "MANUAL"
export type CommandType = "TURN_ON" | "TURN_OFF" | "OPEN" | "CLOSE"
export type CommandStatus = "PENDING" | "SENT" | "ACKED" | "FAILED" | "TIMEOUT"

export type AlertSeverity = "INFO" | "WARNING" | "CRITICAL"
export type AlertStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED"

export type TriggerSource = "SYSTEM" | "USER" | "RULE" | "SCHEDULE" | "DEVICE"
export type EventCategory = "ACTUATOR_CHANGE" | "ALERT" | "CHECK" | "ADVICE" | "FERTILIZER" | "SYSTEM"
export type EventTone = "GREEN" | "TEAL" | "GRAY" | "AMBER" | "RED"

export type SoilPhZone = "STRONGLY_ACIDIC" | "SLIGHTLY_ACIDIC" | "NEUTRAL" | "ALKALINE"
export type DayCode = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN"
export type RuleOperator = "GT" | "GTE" | "LT" | "LTE" | "BETWEEN" | "OUTSIDE"

/** The named windows the chart selector offers. `Tùy chỉnh` uses explicit from/to instead. */
export type RangeKey = "24H" | "7D" | "30D"

export interface Threshold {
  min: number | null
  max: number | null
  /** The dashed lines the charts draw ("Ngưỡng cảnh báo 30°C"). */
  warnLow: number | null
  warnHigh: number | null
  unit: string | null
}

export interface Member {
  userId: string
  email: string
  fullName: string | null
  addedAt: string
}

export interface Garden {
  id: string
  name: string
  description: string | null
  type: string
  areaSqm: number | null
  timezone: string
  location: { latitude: number | null; longitude: number | null; address: string | null } | null
  plantProfileId: string | null
  systemEnabled: boolean
  thresholds: Partial<Record<SensorType, Threshold>>
  members: Member[]
  /** Lets the UI hide owner-only controls rather than surface a 403 after the fact. */
  viewerIsOwner: boolean
}

export interface SensorTile {
  sensorId: string
  type: SensorType
  slug: SensorSlug
  label: string
  value: number | null
  unit: string | null
  status: SensorStatus
  lastReadingAt: string | null
  /** BE-computed against the garden threshold — FE must NOT re-derive it. */
  breached: boolean
  threshold: Threshold | null
}

export interface Sensor {
  id: string
  gardenId: string
  deviceCode: string
  channel: string
  type: SensorType
  slug: SensorSlug
  label: string
  name: string
  unit: string
  status: SensorStatus
  lastValue: number | null
  lastReadingAt: string | null
  batteryLevel: number | null
  firmwareVersion: string | null
  samplingIntervalSec: number
  enabled: boolean
}

export interface Actuator {
  id: string
  gardenId: string
  deviceCode: string
  channel: string
  type: ActuatorType
  typeLabel: string
  name: string
  state: ActuatorState
  stateLabel: string
  mode: ActuatorMode
  lastChangedAt: string | null
  lastChangedBy: TriggerSource | null
  autoOffAt: string | null
  enabled: boolean
}

export interface AutomationEvent {
  id: string
  occurredAt: string
  source: TriggerSource
  category: EventCategory
  title: string
  detail: string | null
  /** Presentation hint from BE so the client does not re-derive styling from copy. */
  tone: EventTone
  sensorId: string | null
  actuatorId: string | null
  ruleId: string | null
  scheduleId: string | null
}

export interface FertilizerRecommendation {
  title: string
  rationale: string
  dosage: string
  frequency: string
  alternatives: string[]
}

export interface SoilAnalysis {
  id: string
  gardenId: string
  measuredAt: string
  ph: number
  source: "SENSOR" | "MANUAL"
  zone: SoilPhZone
  /** Vietnamese badge copy, e.g. "Đất chua nhẹ". */
  zoneLabel: string
  recommendation: FertilizerRecommendation
}

export interface Alert {
  id: string
  gardenId: string
  sensorId: string | null
  actuatorId: string | null
  ruleId: string | null
  code: string
  severity: AlertSeverity
  status: AlertStatus
  title: string
  message: string
  triggerValue: number | null
  thresholdValue: number | null
  unit: string | null
  read: boolean
  raisedAt: string
  acknowledgedAt: string | null
  resolvedAt: string | null
}

export interface Dashboard {
  garden: Garden
  sensors: SensorTile[]
  actuators: Actuator[]
  latestSoil: SoilAnalysis | null
  unreadAlerts: number
  sensorCount: number
  pumpCount: number
  /** At least one sensor is currently reporting. */
  live: boolean
  recentEvents: AutomationEvent[]
}

export interface SeriesPoint {
  timestamp: string
  value: number
  min: number
  max: number
  samples: number
}

export interface Series {
  type: SensorType
  unit: string
  range: string
  from: string
  to: string
  bucketMinutes: number
  points: SeriesPoint[]
}

export interface Summary {
  type: SensorType
  unit: string
  range: string
  current: number | null
  min: number | null
  max: number | null
  average: number | null
  samples: number
  previousAverage: number | null
  /** Delta vs the immediately preceding window of equal length — the "↑ Tăng 2°C" line. */
  delta: number | null
  deltaPercent: number | null
  trend: "up" | "down" | "flat"
  threshold: Threshold | null
}

export interface IrrigationSchedule {
  id: string
  gardenId: string
  actuatorId: string
  name: string
  enabled: boolean
  daysOfWeek: DayCode[]
  startTime: string
  durationMinutes: number
  skipIfSoilMoistureAbove: number | null
  skipIfRainForecast: boolean
  nextRunAt: string | null
  lastRunAt: string | null
  lastRunStatus: "SUCCESS" | "SKIPPED" | "FAILED" | null
  lastSkipReason: string | null
}

export interface AutomationRule {
  id: string
  gardenId: string
  name: string
  enabled: boolean
  priority: number
  condition: {
    sensorType: SensorType
    operator: RuleOperator
    value: number
    secondValue: number | null
    sustainedForMinutes: number
  }
  action: {
    actuatorId: string | null
    actuatorType: ActuatorType | null
    command: CommandType
    durationMinutes: number | null
    raiseAlert: boolean
    alertSeverity: AlertSeverity
  }
  cooldownMinutes: number
  activeFrom: string | null
  activeTo: string | null
  lastTriggeredAt: string | null
  triggerCount: number
}

export interface DeviceCommand {
  id: string
  actuatorId: string
  deviceCode: string
  channel: string
  command: CommandType
  durationMinutes: number | null
  status: CommandStatus
  issuedBy: TriggerSource
  issuedAt: string
  sentAt: string | null
  ackedAt: string | null
  errorMessage: string | null
}

export interface User {
  id: string
  email: string
  fullName: string
  phone: string | null
  role: string
  notifyByPush: boolean
  notifyByEmail: boolean
  quietHoursStart: string | null
  quietHoursEnd: string | null
  pushTokens: string[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  last: boolean
}

// ── Slug helpers ────────────────────────────────────────────────────────────
// The app routes by slug (/sensor/soil-moisture) while BE speaks enums. One conversion
// table, used by the router and the API modules alike.

export const SLUG_BY_TYPE: Record<SensorType, SensorSlug> = {
  TEMPERATURE: "temperature",
  AIR_HUMIDITY: "air-humidity",
  SOIL_MOISTURE: "soil-moisture",
  LIGHT: "light",
  PH: "ph",
}

export const TYPE_BY_SLUG: Record<SensorSlug, SensorType> = {
  temperature: "TEMPERATURE",
  "air-humidity": "AIR_HUMIDITY",
  "soil-moisture": "SOIL_MOISTURE",
  light: "LIGHT",
  ph: "PH",
}

export function isSensorSlug(value: string): value is SensorSlug {
  return value in TYPE_BY_SLUG
}
