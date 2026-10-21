import { queryOptions } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client'
import type { HoldResponse, ReservationResponse } from '../../shared/api/types'

export const reservationKeys = {
  all: ['reservations'] as const,
  holds: ['reservations', 'holds'] as const,
  history: ['reservations', 'history'] as const,
}

export const activeHoldsQuery = () => queryOptions({
  queryKey: reservationKeys.holds,
  queryFn: ({ signal }) => apiRequest<HoldResponse[]>('/api/reservations/holds/my', {}, signal),
})

export const reservationsQuery = () => queryOptions({
  queryKey: reservationKeys.history,
  queryFn: ({ signal }) => apiRequest<ReservationResponse[]>('/api/reservations/my', {}, signal),
})

export interface CreateHoldPayload {
  vehicleId: number
  spotId: number
  startAt: string
  endAt: string
}

export function createHold(payload: CreateHoldPayload): Promise<HoldResponse> {
  return apiRequest('/api/reservations/holds', { method: 'POST', body: JSON.stringify(payload) })
}

export function confirmHold(id: number): Promise<ReservationResponse> {
  return apiRequest(`/api/reservations/holds/${id}/confirm`, { method: 'POST' })
}

export function releaseHold(id: number): Promise<HoldResponse> {
  return apiRequest(`/api/reservations/holds/${id}/release`, { method: 'POST' })
}

export function cancelReservation(id: number): Promise<ReservationResponse> {
  return apiRequest(`/api/reservations/${id}/cancel`, { method: 'POST' })
}
