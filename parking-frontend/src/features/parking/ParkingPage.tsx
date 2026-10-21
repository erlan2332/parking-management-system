import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DateTime } from 'luxon'
import { CalendarClock, Info } from 'lucide-react'
import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../shared/api/client'
import type { ParkingSpotStatus, SpotAvailabilityResponse } from '../../shared/api/types'
import { initialParkingForm, PARKING_ZONE, toParkingOffsetDateTime } from '../../shared/lib/format'
import { parkingStatusLabels } from '../../shared/lib/status'
import { Field, Input, Select } from '../../shared/ui/FormControls'
import { Card, PageHeader } from '../../shared/ui/Surface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { activeHoldsQuery, confirmHold, createHold, releaseHold, reservationKeys } from '../reservations/api'
import { vehiclesQuery } from '../vehicles/api'
import { availabilityQuery, parkingKeys } from './api'
import { HoldPanel } from './HoldPanel'
import { ParkingMap } from './ParkingMap'

const legendTones: Record<ParkingSpotStatus, string> = {
  FREE: 'bg-emerald-500', HELD: 'bg-amber-500', RESERVED: 'bg-blue-500', OCCUPIED: 'bg-red-500', UNAVAILABLE: 'bg-slate-400',
}

export function ParkingPage() {
  const initial = useMemo(() => initialParkingForm(), [])
  const [pageOpenedAt] = useState(() => Date.now())
  const [date, setDate] = useState(initial.date)
  const [startTime, setStartTime] = useState(initial.startTime)
  const [endTime, setEndTime] = useState(initial.endTime)
  const [vehicleId, setVehicleId] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const vehicles = useQuery(vehiclesQuery())
  const holds = useQuery(activeHoldsQuery())
  const activeHold = holds.data?.[0]

  const restoredStart = activeHold ? DateTime.fromISO(activeHold.startAt, { setZone: true }).setZone(PARKING_ZONE) : null
  const restoredEnd = activeHold ? DateTime.fromISO(activeHold.endAt, { setZone: true }).setZone(PARKING_ZONE) : null
  const effectiveDate = restoredStart?.toISODate() ?? date
  const effectiveStartTime = restoredStart?.toFormat('HH:mm') ?? startTime
  const effectiveEndTime = restoredEnd?.toFormat('HH:mm') ?? endTime
  const selectedVehicleId = activeHold ? String(activeHold.vehicleId) : vehicleId || String(vehicles.data?.[0]?.id ?? '')
  const startAt = toParkingOffsetDateTime(effectiveDate, effectiveStartTime)
  const endAt = toParkingOffsetDateTime(effectiveDate, effectiveEndTime)
  const intervalError = !startAt || !endAt
    ? 'Заполните дату и время.'
    : Date.parse(endAt) <= Date.parse(startAt)
      ? 'Время окончания должно быть позже времени начала.'
      : Date.parse(startAt) <= pageOpenedAt
        ? 'Время начала должно быть в будущем.'
        : null
  const availability = useQuery(availabilityQuery(intervalError ? null : startAt, intervalError ? null : endAt))

  const refreshRelated = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
    queryClient.invalidateQueries({ queryKey: reservationKeys.holds })
    queryClient.invalidateQueries({ queryKey: reservationKeys.history })
  }, [queryClient])

  const holdMutation = useMutation({
    mutationFn: (spot: SpotAvailabilityResponse) => createHold({
      vehicleId: Number(selectedVehicleId), spotId: spot.id, startAt: startAt!, endAt: endAt!,
    }),
    onSuccess: (hold) => {
      setNotice(null)
      queryClient.setQueryData(reservationKeys.holds, [hold])
      queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
    },
  })
  const confirmMutation = useMutation({
    mutationFn: () => confirmHold(activeHold!.id),
    onSuccess: () => {
      setNotice('Бронирование подтверждено. Оно появилось в разделе «Мои бронирования».')
      queryClient.setQueryData(reservationKeys.holds, [])
      refreshRelated()
    },
  })
  const releaseMutation = useMutation({
    mutationFn: () => releaseHold(activeHold!.id),
    onSuccess: () => {
      setNotice('Выбор отменён. Место снова доступно для бронирования.')
      queryClient.setQueryData(reservationKeys.holds, [])
      refreshRelated()
    },
  })

  const handleExpired = useCallback(() => {
    setNotice('Время выбора истекло. Выберите место ещё раз.')
    refreshRelated()
  }, [refreshRelated])

  function selectSpot(spot: SpotAvailabilityResponse) {
    setNotice(null)
    if (!selectedVehicleId) return setNotice('Сначала выберите автомобиль.')
    if (intervalError) return setNotice(intervalError)
    holdMutation.mutate(spot)
  }

  const holdVehicle = vehicles.data?.find((vehicle) => vehicle.id === activeHold?.vehicleId)
  const minDate = DateTime.now().setZone(PARKING_ZONE).toISODate() ?? undefined

  return (
    <div className="grid gap-6">
      <PageHeader title="Выберите парковочное место" />
      <Card>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Field label="Автомобиль">
            <Select value={selectedVehicleId} onChange={(event) => setVehicleId(event.target.value)} disabled={Boolean(activeHold)}>
              <option value="">Выберите автомобиль</option>
              {vehicles.data?.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.plateNumber}</option>)}
            </Select>
          </Field>
          <Field label="Дата парковки"><Input type="date" min={minDate} value={effectiveDate} onChange={(event) => setDate(event.target.value)} disabled={Boolean(activeHold)} /></Field>
          <Field label="Начало"><Input type="time" value={effectiveStartTime} onChange={(event) => setStartTime(event.target.value)} disabled={Boolean(activeHold)} /></Field>
          <Field label="Окончание" error={intervalError ?? undefined}><Input type="time" value={effectiveEndTime} onChange={(event) => setEndTime(event.target.value)} disabled={Boolean(activeHold)} /></Field>
        </div>
        {!vehicles.isPending && vehicles.data?.length === 0 ? <p className="mt-4 rounded-xl bg-blue-50 p-4 text-sm text-blue-800">Сначала <Link to="/vehicles" className="font-semibold underline">добавьте автомобиль</Link>, чтобы забронировать место.</p> : null}
        {activeHold ? <p className="mt-4 flex items-center gap-2 text-sm text-amber-700"><Info className="size-4" /> Завершите текущее удержание, прежде чем менять автомобиль или время.</p> : null}
      </Card>

      {activeHold ? (
        <HoldPanel
          hold={activeHold}
          vehicle={holdVehicle}
          confirming={confirmMutation.isPending}
          releasing={releaseMutation.isPending}
          onConfirm={() => confirmMutation.mutate()}
          onRelease={() => releaseMutation.mutate()}
          onExpired={handleExpired}
        />
      ) : null}
      {notice ? <p className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800" role="status">{notice}</p> : null}
      {holdMutation.isError || confirmMutation.isError || releaseMutation.isError ? (
        <p className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
          {getErrorMessage(holdMutation.error ?? confirmMutation.error ?? releaseMutation.error, 'Не удалось выполнить действие с бронированием.')}
        </p>
      ) : null}

      <Card>
        <div className="mb-5 flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <h2 className="text-lg font-bold">Схема парковки</h2>
          <div className="flex flex-wrap gap-x-4 gap-y-2" aria-label="Легенда статусов">
            {(Object.keys(parkingStatusLabels) as ParkingSpotStatus[]).map((status) => <span key={status} className="flex items-center gap-2 text-xs text-slate-600"><span className={`size-2.5 rounded-full ${legendTones[status]}`} />{parkingStatusLabels[status]}</span>)}
          </div>
        </div>
        {intervalError ? <div className="state-panel"><CalendarClock className="size-7 text-blue-600" /><span>{intervalError}</span></div> : null}
        {!intervalError && availability.isPending ? <LoadingState label="Проверяем доступность мест…" /> : null}
        {!intervalError && availability.isError ? <ErrorState message={getErrorMessage(availability.error, 'Не удалось загрузить парковочные места.')} onRetry={() => availability.refetch()} /> : null}
        {availability.data ? <ParkingMap spots={availability.data.spots} onSelect={selectSpot} pendingSpotId={holdMutation.isPending ? holdMutation.variables?.id : undefined} /> : null}
      </Card>
    </div>
  )
}
