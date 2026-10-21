import { CheckCircle2, X } from 'lucide-react'
import { useEffect, useRef } from 'react'
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
  const confirmRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (countdown.expired) onExpired()
  }, [countdown.expired, onExpired])

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const previouslyFocused = document.activeElement as HTMLElement | null
    document.body.style.overflow = 'hidden'
    confirmRef.current?.focus()
    return () => {
      document.body.style.overflow = previousOverflow
      previouslyFocused?.focus()
    }
  }, [])

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/60 p-4 backdrop-blur-sm">
      <section
        className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:p-6"
        role="dialog"
        aria-modal="true"
        aria-labelledby="hold-title"
        aria-describedby="hold-description"
      >
        <div>
          <p className="text-sm font-semibold text-amber-700">Временное удержание</p>
          <h2 id="hold-title" className="mt-1 text-xl font-bold text-slate-950">Место {hold.spotCode} закреплено за вами</h2>
        </div>

        <p id="hold-description" className="mt-4 text-sm leading-6 text-slate-600">
          {vehicle?.plateNumber ?? `Автомобиль #${hold.vehicleId}`} · {formatDateTime(hold.startAt)} — {formatDateTime(hold.endAt)}
        </p>

        <div className="mt-5">
          <div className="mb-2 flex items-center justify-between text-xs font-semibold text-slate-500">
            <span>Время на подтверждение</span>
            <span>{countdown.label}</span>
          </div>
          <div
            className="h-2.5 overflow-hidden rounded-full bg-slate-100"
            role="progressbar"
            aria-label="Оставшееся время удержания"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(countdown.progressPercent)}
          >
            <div
              className={`h-full rounded-full transition-[width] duration-1000 ease-linear ${countdown.progressPercent <= 20 ? 'bg-red-500' : 'bg-amber-500'}`}
              style={{ width: `${countdown.progressPercent}%` }}
            />
          </div>
        </div>

        {countdown.expired ? <p className="mt-4 text-sm font-medium text-red-700">Время выбора истекло. Доступность обновляется с сервера.</p> : null}
        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={onRelease} loading={releasing} disabled={confirming}><X className="size-4" /> Отменить выбор</Button>
          <Button ref={confirmRef} onClick={onConfirm} loading={confirming} disabled={countdown.expired || releasing}><CheckCircle2 className="size-4" /> Подтвердить бронирование</Button>
        </div>
      </section>
    </div>
  )
}
