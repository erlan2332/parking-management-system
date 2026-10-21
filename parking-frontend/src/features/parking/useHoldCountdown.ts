import { useEffect, useMemo, useState } from 'react'

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
    label: `${String(Math.floor(remainingSeconds / 60)).padStart(2, '0')}:${String(remainingSeconds % 60).padStart(2, '0')}`,
  }
}
