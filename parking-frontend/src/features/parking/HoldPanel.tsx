import { CheckCircle2, Clock3, X } from 'lucide-react'
import { useEffect } from 'react'
import type { HoldResponse, VehicleResponse } from '../../shared/api/types'
import { formatDateTime } from '../../shared/lib/format'
import { Button } from '../../shared/ui/Button'
import { useHoldCountdown } from './useHoldCountdown'

export function HoldPanel({ hold, vehicle, confirming, releasing, onConfirm, onRelease, onExpired }: {
  hold: HoldResponse
  vehicle?: VehicleResponse
  confirming: boolean
  releasing: boolean
  onConfirm: () => void
  onRelease: () => void
  onExpired: () => void
}) {
  const countdown = useHoldCountdown(hold.holdExpiresAt)

  useEffect(() => {
    if (countdown.expired) onExpired()
  }, [countdown.expired, onExpired])

  return (
    <section className="rounded-2xl border border-amber-300 bg-amber-50 p-5" aria-live="polite">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-sm font-semibold text-amber-800">Временное удержание</p>
          <h2 className="mt-1 text-xl font-bold text-slate-950">Место {hold.spotCode} закреплено за вами</h2>
          <p className="mt-2 text-sm leading-6 text-slate-700">{vehicle?.plateNumber ?? `Автомобиль #${hold.vehicleId}`} · {formatDateTime(hold.startAt)} — {formatDateTime(hold.endAt)}</p>
        </div>
        <div className={`flex min-w-28 items-center justify-center gap-2 rounded-xl px-4 py-3 font-mono text-xl font-bold ${countdown.expired ? 'bg-red-100 text-red-700' : 'bg-white text-amber-800'}`}>
          <Clock3 className="size-5" /> {countdown.label}
        </div>
      </div>
      {countdown.expired ? <p className="mt-4 text-sm font-medium text-red-700">Время выбора истекло. Доступность обновляется с сервера.</p> : null}
      <div className="mt-5 flex flex-col gap-3 sm:flex-row">
        <Button onClick={onConfirm} loading={confirming} disabled={countdown.expired || releasing}><CheckCircle2 className="size-4" /> Подтвердить бронирование</Button>
        <Button variant="secondary" onClick={onRelease} loading={releasing} disabled={confirming}><X className="size-4" /> Отменить выбор</Button>
      </div>
    </section>
  )
}
