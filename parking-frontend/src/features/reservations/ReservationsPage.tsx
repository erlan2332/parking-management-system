import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarX, CarFront, MapPin } from 'lucide-react'
import { useState } from 'react'
import { getErrorMessage } from '../../shared/api/client'
import type { ReservationResponse, ReservationStatus } from '../../shared/api/types'
import { formatDateTime } from '../../shared/lib/format'
import { reservationStatusLabels } from '../../shared/lib/status'
import { Button } from '../../shared/ui/Button'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { Badge, Card, PageHeader } from '../../shared/ui/Surface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { parkingKeys } from '../parking/api'
import { vehiclesQuery } from '../vehicles/api'
import { cancelReservation, reservationKeys, reservationsQuery } from './api'

function tone(status: ReservationStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  if (status === 'ACTIVE' || status === 'COMPLETED') return 'success'
  if (status === 'CHECKED_IN') return 'info'
  if (status === 'CANCELLED' || status === 'EXPIRED' || status === 'HOLD_EXPIRED') return 'danger'
  if (status === 'HELD') return 'warning'
  return 'neutral'
}

export function ReservationsPage() {
  const [selected, setSelected] = useState<ReservationResponse | null>(null)
  const queryClient = useQueryClient()
  const reservations = useQuery(reservationsQuery())
  const vehicles = useQuery(vehiclesQuery())
  const cancel = useMutation({
    mutationFn: (id: number) => cancelReservation(id),
    onSuccess: () => {
      setSelected(null)
      queryClient.invalidateQueries({ queryKey: reservationKeys.history })
      queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
    },
  })
  const vehicleName = (id: number) => vehicles.data?.find((vehicle) => vehicle.id === id)?.plateNumber ?? `Автомобиль #${id}`

  return (
    <div className="grid gap-6">
      <PageHeader title="Мои бронирования" />
      <Card>
        {reservations.isPending ? <LoadingState /> : null}
        {reservations.isError ? <ErrorState message={getErrorMessage(reservations.error, 'Не удалось загрузить бронирования.')} onRetry={() => reservations.refetch()} /> : null}
        {reservations.data?.length === 0 ? <EmptyState title="Бронирований пока нет" description="Выберите автомобиль, интервал и свободное место на странице парковки." /> : null}
        {reservations.data?.length ? (
          <ul className="grid gap-4 lg:grid-cols-2">
            {reservations.data.map((reservation) => (
              <li key={reservation.id} className="rounded-2xl border border-slate-200 p-5">
                <div className="flex items-start justify-between gap-3"><div><p className="text-sm text-slate-500">Бронирование #{reservation.id}</p><h2 className="mt-1 text-lg font-bold">Место {reservation.spotCode}</h2></div><Badge tone={tone(reservation.status)}>{reservationStatusLabels[reservation.status]}</Badge></div>
                <div className="mt-4 grid gap-2 text-sm text-slate-600">
                  <p className="flex items-center gap-2"><CarFront className="size-4" /> {vehicleName(reservation.vehicleId)}</p>
                  <p className="flex items-center gap-2"><MapPin className="size-4" /> {formatDateTime(reservation.startAt)} — {formatDateTime(reservation.endAt)}</p>
                </div>
                {reservation.status === 'ACTIVE' ? <Button variant="secondary" className="mt-5" onClick={() => setSelected(reservation)}><CalendarX className="size-4" /> Отменить бронь</Button> : null}
              </li>
            ))}
          </ul>
        ) : null}
        {cancel.isError ? <p className="mt-4 text-sm text-red-600" role="alert">{getErrorMessage(cancel.error, 'Не удалось отменить бронирование.')}</p> : null}
      </Card>
      <ConfirmDialog open={Boolean(selected)} title="Отменить бронирование?" description={`Место ${selected?.spotCode ?? ''} станет доступно другим водителям.`} confirmLabel="Отменить бронь" pending={cancel.isPending} onClose={() => setSelected(null)} onConfirm={() => selected && cancel.mutate(selected.id)} />
    </div>
  )
}
