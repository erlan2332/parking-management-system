import { describe, expect, it } from 'vitest'
import { ApiError, getErrorMessage } from './client'

describe('API error messages', () => {
  it('explains a reservation conflict in Russian', () => {
    const message = getErrorMessage(new ApiError(409, 'RESERVATION_CONFLICT', 'internal'))
    expect(message).toContain('другой водитель')
  })
})
