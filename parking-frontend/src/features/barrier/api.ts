import { apiRequest } from '../../shared/api/client'
import type { BarrierEntryResponse, BarrierExitResponse } from '../../shared/api/types'

export function enterParking(plateNumber: string): Promise<BarrierEntryResponse> {
  return apiRequest('/api/barrier/entry', { method: 'POST', body: JSON.stringify({ plateNumber }) })
}

export function exitParking(plateNumber: string): Promise<BarrierExitResponse> {
  return apiRequest('/api/barrier/exit', { method: 'POST', body: JSON.stringify({ plateNumber }) })
}
