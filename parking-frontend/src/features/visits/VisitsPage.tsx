import { useQuery } from '@tanstack/react-query'
import { CarFront, ExternalLink, MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../shared/api/client'
import { formatDateTime, formatDuration } from '../../shared/lib/format'
import { entryTypeLabels, visitStatusLabels } from '../../shared/lib/status'
import { Badge, Card, PageHeader } from '../../shared/ui/Surface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { invoicesQuery } from '../invoices/api'
import { visitsQuery } from './api'

export function VisitsPage() {
  const visits = useQuery(visitsQuery())
  const invoices = useQuery(invoicesQuery())
  const invoiceFor = (visitId: number) => invoices.data?.find((invoice) => invoice.visitId === visitId)

  return (
    <div className="grid gap-6">
      <PageHeader title="История парковки" />
      <Card className="overflow-hidden p-0">
        {visits.isPending ? <LoadingState /> : null}
        {visits.isError ? <ErrorState message={getErrorMessage(visits.error, 'Не удалось загрузить историю парковки.')} onRetry={() => visits.refetch()} /> : null}
        {visits.data?.length === 0 ? <EmptyState title="История пока пуста" description="После первого въезда здесь появится парковочный визит." /> : null}
        {visits.data?.length ? (
          <>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[820px] text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-5 py-4">Автомобиль</th><th className="px-5 py-4">Место</th><th className="px-5 py-4">Въезд</th><th className="px-5 py-4">Выезд</th><th className="px-5 py-4">Тип</th><th className="px-5 py-4">Статус</th><th className="px-5 py-4">Счёт</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {visits.data.map((visit) => {
                    const invoice = invoiceFor(visit.id)
                    return <tr key={visit.id} className="hover:bg-slate-50"><td className="px-5 py-4 font-semibold">{visit.plateNumber}</td><td className="px-5 py-4">{visit.spotCode}</td><td className="px-5 py-4 text-slate-600">{formatDateTime(visit.enteredAt)}</td><td className="px-5 py-4 text-slate-600">{visit.exitedAt ? formatDateTime(visit.exitedAt) : 'Сейчас на парковке'}<span className="block text-xs">{formatDuration(visit.durationSeconds)}</span></td><td className="px-5 py-4">{entryTypeLabels[visit.entryType]}</td><td className="px-5 py-4"><Badge tone={visit.status === 'ACTIVE' ? 'info' : 'success'}>{visitStatusLabels[visit.status]}</Badge></td><td className="px-5 py-4">{invoice ? <Link className="inline-flex items-center gap-1 font-semibold text-blue-700 hover:underline" to={`/invoices?invoice=${invoice.id}`}>{invoice.invoiceNumber}<ExternalLink className="size-3" /></Link> : '—'}</td></tr>
                  })}
                </tbody>
              </table>
            </div>
            <ul className="grid gap-3 p-4 md:hidden">
              {visits.data.map((visit) => {
                const invoice = invoiceFor(visit.id)
                return <li key={visit.id} className="rounded-xl border border-slate-200 p-4"><div className="flex items-start justify-between gap-3"><p className="flex items-center gap-2 font-bold"><CarFront className="size-4" /> {visit.plateNumber}</p><Badge tone={visit.status === 'ACTIVE' ? 'info' : 'success'}>{visitStatusLabels[visit.status]}</Badge></div><p className="mt-3 flex items-center gap-2 text-sm text-slate-600"><MapPin className="size-4" /> Место {visit.spotCode} · {entryTypeLabels[visit.entryType]}</p><p className="mt-2 text-sm text-slate-600">Въезд: {formatDateTime(visit.enteredAt)}</p><p className="mt-1 text-sm text-slate-600">Выезд: {visit.exitedAt ? formatDateTime(visit.exitedAt) : 'сейчас на парковке'}</p><p className="mt-1 text-sm font-medium">{formatDuration(visit.durationSeconds)}</p>{invoice ? <Link className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-blue-700" to={`/invoices?invoice=${invoice.id}`}>Открыть счёт <ExternalLink className="size-3" /></Link> : null}</li>
              })}
            </ul>
          </>
        ) : null}
      </Card>
    </div>
  )
}
