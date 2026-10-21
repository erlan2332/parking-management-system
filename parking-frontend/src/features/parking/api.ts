import { queryOptions } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client'
import type { ParkingAvailabilityResponse } from '../../shared/api/types'

export const parkingKeys = {
  all: ['parking'] as const,
  availabilityRoot: ['parking', 'availability'] as const,
  availability: (startAt: string | null, endAt: string | null) => ['parking', 'availability', startAt, endAt] as const,
}

export const availabilityQuery = (startAt: string | null, endAt: string | null) => queryOptions({
  queryKey: parkingKeys.availability(startAt, endAt),
  queryFn: ({ signal }) => apiRequest<ParkingAvailabilityResponse>(
    `/api/parking/availability?startAt=${encodeURIComponent(startAt!)}&endAt=${encodeURIComponent(endAt!)}`,
    {},
    signal,
  ),
  enabled: Boolean(startAt && endAt),
})
