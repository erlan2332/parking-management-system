import { queryOptions } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client'
import type { ParkingVisitResponse } from '../../shared/api/types'

export const visitKeys = { all: ['visits'] as const }

export const visitsQuery = () => queryOptions({
  queryKey: visitKeys.all,
  queryFn: ({ signal }) => apiRequest<ParkingVisitResponse[]>('/api/visits/my', {}, signal),
})
