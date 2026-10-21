import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { SpotAvailabilityResponse } from '../../shared/api/types'
import { ParkingMap } from './ParkingMap'

const spots: SpotAvailabilityResponse[] = [
  { id: 1, code: 'A1', available: true, status: 'FREE' },
  { id: 2, code: 'A2', available: false, status: 'HELD' },
  { id: 3, code: 'B1', available: false, status: 'RESERVED' },
  { id: 4, code: 'B2', available: false, status: 'OCCUPIED' },
]

describe('ParkingMap', () => {
  it('shows textual statuses and allows selection only for a free spot', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(<ParkingMap spots={spots} onSelect={onSelect} />)

    expect(screen.getByRole('button', { name: 'A1: Свободно' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'A2: Временно удерживается' })).toBeDisabled()
    expect(screen.getByText('Удержание')).toBeInTheDocument()
    expect(screen.getByText('Забронировано')).toBeInTheDocument()
    expect(screen.getByText('Занято')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'A1: Свободно' }))
    expect(onSelect).toHaveBeenCalledWith(spots[0])
  })
})
