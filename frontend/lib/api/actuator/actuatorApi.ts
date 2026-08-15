// lib/api/actuator/actuatorApi.ts
// Pump / curtain / fan control, and the command audit trail behind it.

import { client } from "@/lib/api/client"
import type { Actuator, ActuatorMode, CommandType, DeviceCommand } from "@/lib/api/types"

export function getActuators(gardenId: string): Promise<Actuator[]> {
  return client.get<Actuator[]>(`/api/v1/gardens/${gardenId}/actuators`)
}

export interface CommandAccepted {
  commandId: string
  correlationId: string
  /** SENT = the broker took it. ACKED, later, is the device confirming the relay moved. */
  status: string
  actuator: Actuator
}

/**
 * Drive a device by hand.
 *
 * Refusals come back as 409 with a machine code the UI can branch on:
 * `ALREADY_IN_STATE`, `ACTUATOR_COOLDOWN`, `ACTUATOR_DISABLED`, `ACTUATOR_MANUAL`.
 * These are expected outcomes, not crashes — show the message, do not retry.
 */
export function sendCommand(
  gardenId: string,
  actuatorId: string,
  command: CommandType,
  durationMinutes?: number
): Promise<CommandAccepted> {
  return client.post<CommandAccepted>(
    `/api/v1/gardens/${gardenId}/actuators/${actuatorId}/command`,
    { command, durationMinutes }
  )
}

/** MANUAL locks the device to human commands; rules stop being able to drive it. */
export function setMode(gardenId: string, actuatorId: string, mode: ActuatorMode): Promise<Actuator> {
  return client.patch<Actuator>(`/api/v1/gardens/${gardenId}/actuators/${actuatorId}/mode`, { mode })
}

/**
 * "Did the hardware actually hear me?"
 *
 * PENDING → SENT → ACKED is the happy path; TIMEOUT means the broker accepted it but no
 * device answered within 30s, which is how a dead node becomes visible instead of the UI
 * quietly showing a pump that never started.
 */
export function getRecentCommands(gardenId: string): Promise<DeviceCommand[]> {
  return client.get<DeviceCommand[]>(`/api/v1/gardens/${gardenId}/commands`)
}
