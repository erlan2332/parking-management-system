import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CarFront, Plus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { getErrorMessage } from '../../shared/api/client'
import { Button } from '../../shared/ui/Button'
import { Field, Input } from '../../shared/ui/FormControls'
import { Card, PageHeader } from '../../shared/ui/Surface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { createVehicle, vehicleKeys, vehiclesQuery } from './api'

export function VehiclesPage() {
  const [plate, setPlate] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const vehicles = useQuery(vehiclesQuery())
  const addVehicle = useMutation({
    mutationFn: () => createVehicle(plate.trim()),
    onSuccess: (vehicle) => {
      setPlate('')
      setNotice(`Автомобиль ${vehicle.plateNumber} добавлен.`)
      queryClient.invalidateQueries({ queryKey: vehicleKeys.all })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    setNotice(null)
    addVehicle.mutate()
  }

  return (
    <div className="grid gap-6">
      <PageHeader title="Мои автомобили" description="Добавьте номер автомобиля, чтобы выбирать места и пользоваться шлагбаумом." />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card>
          <h2 className="text-lg font-bold">Зарегистрированные автомобили</h2>
          {vehicles.isPending ? <LoadingState /> : null}
          {vehicles.isError ? <ErrorState message={getErrorMessage(vehicles.error, 'Не удалось загрузить автомобили.')} onRetry={() => vehicles.refetch()} /> : null}
          {vehicles.data?.length === 0 ? <EmptyState title="Автомобилей пока нет" description="Добавьте автомобиль, чтобы забронировать парковочное место." /> : null}
          {vehicles.data?.length ? (
            <ul className="mt-5 grid gap-3 sm:grid-cols-2">
              {vehicles.data.map((vehicle) => (
                <li key={vehicle.id} className="flex items-center gap-3 rounded-xl border border-slate-200 p-4">
                  <span className="grid size-11 place-items-center rounded-xl bg-blue-50 text-blue-700"><CarFront className="size-5" /></span>
                  <div><p className="font-bold text-slate-950">{vehicle.plateNumber}</p><p className="text-xs text-slate-500">Автомобиль #{vehicle.id}</p></div>
                </li>
              ))}
            </ul>
          ) : null}
        </Card>
        <Card className="h-fit">
          <h2 className="text-lg font-bold">Добавить автомобиль</h2>
          <p className="mt-1 text-sm text-slate-500">Пробелы и дефисы будут нормализованы сервером.</p>
          <form className="mt-5 grid gap-4" onSubmit={submit}>
            <Field label="Государственный номер">
              <Input required maxLength={64} value={plate} onChange={(event) => setPlate(event.target.value)} placeholder="01 KG 123 ABC" autoComplete="off" />
            </Field>
            {addVehicle.isError ? <p className="text-sm text-red-600" role="alert">{getErrorMessage(addVehicle.error, 'Не удалось добавить автомобиль.')}</p> : null}
            {notice ? <p className="text-sm text-emerald-700" role="status">{notice}</p> : null}
            <Button type="submit" loading={addVehicle.isPending}><Plus className="size-4" /> Добавить</Button>
          </form>
        </Card>
      </div>
    </div>
  )
}
