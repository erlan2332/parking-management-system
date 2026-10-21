import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { HoldResponse, VehicleResponse } from '../../shared/api/types'
import { HoldPanel } from './HoldPanel'

const hold: HoldResponse = {
  id: 7,
  spotId: 2,
  spotCode: 'A2',
  vehicleId: 3,
  startAt: '2026-10-21T11:30:00Z',
  endAt: '2026-10-21T12:30:00Z',
  status: 'HELD',
  holdExpiresAt: '2026-10-21T10:02:00Z',
}

const vehicle: VehicleResponse = {
  id: 3,
  plateNumber: '01 DEMO 002',
  normalizedPlate: '01DEMO002',
}

describe('HoldPanel', () => {
  afterEach(() => vi.useRealTimers())

  it('shows a modal countdown and shrinking server-deadline progress', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-21T10:00:00Z'))
    const onConfirm = vi.fn()

    render(
      <HoldPanel
        hold={hold}
        vehicle={vehicle}
        confirming={false}
        releasing={false}
        onConfirm={onConfirm}
        onRelease={vi.fn()}
        onExpired={vi.fn()}
      />,
    )

    expect(screen.getByRole('dialog', { name: 'Место A2 закреплено за вами' })).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Оставшееся время удержания' })).toHaveAttribute('aria-valuenow', '67')
    expect(screen.getAllByText('02:00')).toHaveLength(2)

    fireEvent.click(screen.getByRole('button', { name: /Подтвердить бронирование/ }))
    expect(onConfirm).toHaveBeenCalledOnce()
  })
})
