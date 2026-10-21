import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useHoldCountdown } from './useHoldCountdown'

function Countdown({ deadline }: { deadline: string }) {
  const countdown = useHoldCountdown(deadline)
  return <span>{countdown.label}:{String(countdown.expired)}</span>
}

describe('useHoldCountdown', () => {
  afterEach(() => vi.useRealTimers())

  it('derives remaining time from the server deadline', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-21T10:00:00Z'))
    render(<Countdown deadline="2026-10-21T10:02:00Z" />)
    expect(screen.getByText('02:00:false')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(121_000))
    expect(screen.getByText('00:00:true')).toBeInTheDocument()
  })
})
