import { queryOptions } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client'
import type { VehicleResponse } from '../../shared/api/types'

export const vehicleKeys = { all: ['vehicles'] as const }

export const vehiclesQuery = () => queryOptions({
  queryKey: vehicleKeys.all,
  queryFn: ({ signal }) => apiRequest<VehicleResponse[]>('/api/vehicles', {}, signal),
})

export function createVehicle(plateNumber: string): Promise<VehicleResponse> {
  return apiRequest('/api/vehicles', { method: 'POST', body: JSON.stringify({ plateNumber }) })
}
