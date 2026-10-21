import { CarFront, CircleParking } from 'lucide-react'
import type { ParkingSpotStatus, SpotAvailabilityResponse } from '../../shared/api/types'
import { parkingStatusLabels } from '../../shared/lib/status'

const statusStyles: Record<ParkingSpotStatus, string> = {
  FREE: 'border-emerald-300 bg-emerald-50 text-emerald-900 hover:border-emerald-500 hover:bg-emerald-100',
  HELD: 'border-amber-300 bg-amber-50 text-amber-900',
  RESERVED: 'border-blue-300 bg-blue-50 text-blue-900',
  OCCUPIED: 'border-red-300 bg-red-50 text-red-900',
  UNAVAILABLE: 'border-slate-300 bg-slate-100 text-slate-500',
}

const compactStatusLabels: Record<ParkingSpotStatus, string> = {
  ...parkingStatusLabels,
  HELD: 'Удержание',
}

export function ParkingMap({ spots, onSelect, pendingSpotId }: { spots: SpotAvailabilityResponse[]; onSelect: (spot: SpotAvailabilityResponse) => void; pendingSpotId?: number }) {
  const zones = [
    { name: 'Зона A', spots: spots.filter((spot) => spot.code.startsWith('A')) },
    { name: 'Зона B', spots: spots.filter((spot) => spot.code.startsWith('B')) },
  ]

  return (
    <div className="rounded-2xl bg-slate-900 p-4 sm:p-6" aria-label="Схема парковки">
      {zones.map((zone, index) => (
        <div key={zone.name}>
          {index === 1 ? <div className="my-5 flex items-center gap-3 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><span className="h-px flex-1 bg-slate-700" /> Проезд <span className="h-px flex-1 bg-slate-700" /></div> : null}
          <div className="mb-3 flex items-center justify-between"><h3 className="font-semibold text-white">{zone.name}</h3><span className="text-xs text-slate-400">{zone.spots.length} мест</span></div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-5 xl:grid-cols-10">
            {zone.spots.map((spot) => (
              <button
                key={spot.id}
                type="button"
                disabled={!spot.available || pendingSpotId !== undefined}
                onClick={() => onSelect(spot)}
                aria-label={`${spot.code}: ${parkingStatusLabels[spot.status]}`}
                className={`relative h-24 rounded-xl border-2 p-3 text-left transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-blue-300 disabled:cursor-not-allowed ${statusStyles[spot.status]}`}
              >
                {pendingSpotId === spot.id ? <span className="absolute right-2 top-2 size-4 animate-spin rounded-full border-2 border-current border-r-transparent" /> : null}
                {spot.status === 'OCCUPIED' ? <CarFront className="size-5" aria-hidden="true" /> : <CircleParking className="size-5" aria-hidden="true" />}
                <strong className="mt-2 block text-base">{spot.code}</strong>
                <span className="mt-1 block whitespace-nowrap text-[11px] leading-4">{compactStatusLabels[spot.status]}</span>
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
