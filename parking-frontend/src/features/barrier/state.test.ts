import { describe, expect, it } from 'vitest'
import type { ParkingVisitResponse, VehicleResponse } from '../../shared/api/types'
import { findActiveVisit } from './state'

describe('barrier visit state', () => {
  it('matches the backend visit by normalized plate identity', () => {
    const vehicle: VehicleResponse = { id: 3, plateNumber: '01 DEMO 002', normalizedPlate: '01DEMO002' }
    const visit: ParkingVisitResponse = {
      id: 1,
      plateNumber: '01DEMO002',
      spotId: 3,
      spotCode: 'A3',
      entryType: 'WALK_IN',
      enteredAt: '2026-10-21T10:46:28Z',
      exitedAt: null,
      durationSeconds: null,
      status: 'ACTIVE',
    }

    expect(findActiveVisit([visit], vehicle)).toBe(visit)
  })
})
