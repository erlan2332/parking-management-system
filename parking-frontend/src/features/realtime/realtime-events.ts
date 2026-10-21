import type { QueryClient } from '@tanstack/react-query'
import type { ParkingAvailabilityChangedEvent } from '../../shared/api/types'
import { parkingKeys } from '../parking/api'
import { reservationKeys } from '../reservations/api'

function isParkingEvent(value: unknown): value is ParkingAvailabilityChangedEvent {
  return typeof value === 'object' && value !== null && (value as Record<string, unknown>).type === 'PARKING_AVAILABILITY_CHANGED'
}

export function handleParkingMessage(queryClient: QueryClient, body: string): boolean {
  try {
    const event: unknown = JSON.parse(body)
    if (!isParkingEvent(event)) return false
    queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
    queryClient.invalidateQueries({ queryKey: reservationKeys.all })
    return true
  } catch {
    return false
  }
}
