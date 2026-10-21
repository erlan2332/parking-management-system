import { useEffect, useMemo, useState } from 'react'

const MAX_HOLD_SECONDS = 180

export function useHoldCountdown(holdExpiresAt: string) {
  const deadline = useMemo(() => Date.parse(holdExpiresAt), [holdExpiresAt])
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(interval)
  }, [deadline])

  const remainingSeconds = Math.max(0, Math.ceil((deadline - now) / 1000))
  return {
    remainingSeconds,
    expired: remainingSeconds === 0,
    progressPercent: Math.min(100, (remainingSeconds / MAX_HOLD_SECONDS) * 100),
    label: `${String(Math.floor(remainingSeconds / 60)).padStart(2, '0')}:${String(remainingSeconds % 60).padStart(2, '0')}`,
  }
}
