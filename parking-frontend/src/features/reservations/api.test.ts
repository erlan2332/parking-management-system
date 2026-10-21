import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createHold } from './api'

describe('reservation API', () => {
  beforeEach(() => {
    sessionStorage.clear()
    sessionStorage.setItem('parking.access-token', 'jwt')
    vi.unstubAllGlobals()
  })

  it('creates a temporary hold with the real backend DTO shape', async () => {
    const response = { id: 5, spotId: 2, spotCode: 'A2', vehicleId: 3, startAt: '2026-10-22T04:00:00Z', endAt: '2026-10-22T05:00:00Z', status: 'HELD', holdExpiresAt: '2026-10-21T10:03:00Z' }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await createHold({ vehicleId: 3, spotId: 2, startAt: '2026-10-22T10:00:00+06:00', endAt: '2026-10-22T11:00:00+06:00' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ vehicleId: 3, spotId: 2, startAt: '2026-10-22T10:00:00+06:00', endAt: '2026-10-22T11:00:00+06:00' })
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer jwt')
  })
})
