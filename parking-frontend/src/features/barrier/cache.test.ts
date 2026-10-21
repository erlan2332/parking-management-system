import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { refreshAfterBarrierOperation } from './cache'

describe('barrier cache refresh', () => {
  it('refreshes availability, visits, invoices, and reservations after exit', () => {
    const client = new QueryClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    refreshAfterBarrierOperation(client)
    expect(invalidate).toHaveBeenCalledTimes(4)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['visits'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['invoices'] })
  })
})
