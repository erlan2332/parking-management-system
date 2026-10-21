import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { handleParkingMessage } from './realtime-events'

describe('parking realtime events', () => {
  it('invalidates server state after a valid STOMP event', () => {
    const client = new QueryClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const handled = handleParkingMessage(client, JSON.stringify({
      type: 'PARKING_AVAILABILITY_CHANGED', reason: 'HOLD_CREATED', spotId: 1, spotCode: 'A1', occurredAt: '2026-10-21T10:00:00Z',
    }))
    expect(handled).toBe(true)
    expect(invalidate).toHaveBeenCalledTimes(2)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['parking', 'availability'] })
  })

  it('ignores malformed messages', () => {
    const client = new QueryClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    expect(handleParkingMessage(client, '{bad json')).toBe(false)
    expect(invalidate).not.toHaveBeenCalled()
  })
})
