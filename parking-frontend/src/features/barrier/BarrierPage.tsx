import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownToLine, ArrowUpFromLine, CarFront, DoorOpen, MapPin, ReceiptText } from 'lucide-react'
import { useState } from 'react'
import { getErrorMessage } from '../../shared/api/client'
import type { BarrierEntryResponse, BarrierExitResponse } from '../../shared/api/types'
import { formatDateTime, formatDuration, formatMoney } from '../../shared/lib/format'
import { entryTypeLabels } from '../../shared/lib/status'
import { Button } from '../../shared/ui/Button'
import { Field, Select } from '../../shared/ui/FormControls'
import { Badge, Card, PageHeader } from '../../shared/ui/Surface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { invoiceKeys } from '../invoices/api'
import { parkingKeys } from '../parking/api'
import { reservationKeys } from '../reservations/api'
import { vehiclesQuery } from '../vehicles/api'
import { visitKeys, visitsQuery } from '../visits/api'
import { enterParking, exitParking } from './api'

export function BarrierPage() {
  const [vehicleId, setVehicleId] = useState('')
  const [result, setResult] = useState<BarrierEntryResponse | BarrierExitResponse | null>(null)
  const queryClient = useQueryClient()
  const vehicles = useQuery(vehiclesQuery())
  const visits = useQuery(visitsQuery())
  const selectedVehicleId = vehicleId || String(vehicles.data?.[0]?.id ?? '')
  const selectedVehicle = vehicles.data?.find((vehicle) => vehicle.id === Number(selectedVehicleId))
  const activeVisit = visits.data?.find((visit) => visit.plateNumber === selectedVehicle?.plateNumber && visit.status === 'ACTIVE')

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
    queryClient.invalidateQueries({ queryKey: visitKeys.all })
    queryClient.invalidateQueries({ queryKey: invoiceKeys.all })
    queryClient.invalidateQueries({ queryKey: reservationKeys.all })
  }
  const entry = useMutation({
    mutationFn: () => enterParking(selectedVehicle!.plateNumber),
    onSuccess: (data) => { setResult(data); refresh() },
  })
  const exit = useMutation({
    mutationFn: () => exitParking(selectedVehicle!.plateNumber),
    onSuccess: (data) => { setResult(data); refresh() },
  })
  const error = entry.error ?? exit.error
  const isExitResult = result !== null && 'invoiceNumber' in result

  return (
    <div className="grid gap-6">
      <PageHeader title="Шлагбаум" description="Выберите автомобиль и зафиксируйте реальный въезд или выезд. Время определяет сервер." />
      <div className="grid gap-6 xl:grid-cols-[380px_minmax(0,1fr)]">
        <Card className="h-fit">
          <div className="flex items-center gap-3"><span className="grid size-12 place-items-center rounded-2xl bg-blue-50 text-blue-700"><DoorOpen /></span><div><h2 className="font-bold">Симулятор проезда</h2><p className="text-sm text-slate-500">Без имитации оборудования</p></div></div>
          <div className="mt-6"><Field label="Автомобиль"><Select value={selectedVehicleId} onChange={(event) => { setVehicleId(event.target.value); setResult(null) }}><option value="">Выберите автомобиль</option>{vehicles.data?.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.plateNumber}</option>)}</Select></Field></div>
          {visits.isPending || vehicles.isPending ? <LoadingState label="Проверяем статус автомобиля…" /> : null}
          {selectedVehicle && !visits.isPending ? (
            <div className="mt-5 rounded-xl bg-slate-50 p-4">
              <div className="flex items-center justify-between gap-3"><span className="text-sm text-slate-500">Статус</span><Badge tone={activeVisit ? 'danger' : 'success'}>{activeVisit ? 'На парковке' : 'Вне парковки'}</Badge></div>
              {activeVisit ? <div className="mt-3 text-sm text-slate-700"><p className="font-semibold">Место {activeVisit.spotCode}</p><p className="mt-1 text-slate-500">Въезд: {formatDateTime(activeVisit.enteredAt)}</p></div> : null}
            </div>
          ) : null}
          {error ? <p className="mt-4 text-sm text-red-600" role="alert">{getErrorMessage(error, 'Шлагбаум не выполнил операцию.')}</p> : null}
          <div className="mt-5 grid grid-cols-2 gap-3">
            <Button disabled={!selectedVehicle || Boolean(activeVisit) || exit.isPending} loading={entry.isPending} onClick={() => entry.mutate()}><ArrowDownToLine className="size-4" /> Въехать</Button>
            <Button variant="secondary" disabled={!selectedVehicle || !activeVisit || entry.isPending} loading={exit.isPending} onClick={() => exit.mutate()}><ArrowUpFromLine className="size-4" /> Выехать</Button>
          </div>
        </Card>
        <Card>
          <h2 className="text-lg font-bold">Результат операции</h2>
          {!result ? <div className="state-panel"><CarFront className="size-8 text-slate-400" /><span>Выберите автомобиль и выполните въезд или выезд.</span></div> : (
            <div className="mt-5 grid gap-5">
              <div className="rounded-2xl bg-emerald-50 p-5 text-emerald-900"><p className="font-bold">Шлагбаум открыт</p><p className="mt-1 text-sm">Операция успешно сохранена сервером.</p></div>
              <dl className="grid gap-4 sm:grid-cols-2">
                <div><dt className="text-sm text-slate-500">Автомобиль</dt><dd className="mt-1 font-semibold">{result.plateNumber}</dd></div>
                <div><dt className="text-sm text-slate-500">Место</dt><dd className="mt-1 flex items-center gap-2 font-semibold"><MapPin className="size-4" /> {result.spotCode}</dd></div>
                <div><dt className="text-sm text-slate-500">Тип въезда</dt><dd className="mt-1 font-semibold">{entryTypeLabels[result.entryType]}</dd></div>
                <div><dt className="text-sm text-slate-500">Время въезда</dt><dd className="mt-1 font-semibold">{formatDateTime(result.enteredAt)}</dd></div>
                {isExitResult ? <><div><dt className="text-sm text-slate-500">Время выезда</dt><dd className="mt-1 font-semibold">{formatDateTime(result.exitedAt)}</dd></div><div><dt className="text-sm text-slate-500">Продолжительность</dt><dd className="mt-1 font-semibold">{formatDuration(result.durationSeconds)}</dd></div><div><dt className="text-sm text-slate-500">Счёт</dt><dd className="mt-1 flex items-center gap-2 font-semibold"><ReceiptText className="size-4" /> {result.invoiceNumber}</dd></div><div><dt className="text-sm text-slate-500">Итого</dt><dd className="mt-1 text-lg font-bold text-blue-700">{formatMoney(result.totalAmount, result.currency)}</dd></div></> : null}
              </dl>
            </div>
          )}
        </Card>
      </div>
      {visits.isError ? <ErrorState message={getErrorMessage(visits.error, 'Не удалось определить активный визит.')} onRetry={() => visits.refetch()} /> : null}
    </div>
  )
}
