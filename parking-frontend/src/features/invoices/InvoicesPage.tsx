import { useQuery } from '@tanstack/react-query'
import { Clock3, FileText, ReceiptText, X } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../../shared/api/client'
import { formatDateTime, formatDuration, formatMoney } from '../../shared/lib/format'
import { Button } from '../../shared/ui/Button'
import { Badge, Card, PageHeader } from '../../shared/ui/Surface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { invoiceQuery, invoicesQuery } from './api'

export function InvoicesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawId = searchParams.get('invoice')
  const selectedId = rawId && /^\d+$/.test(rawId) ? Number(rawId) : null
  const invoices = useQuery(invoicesQuery())
  const detail = useQuery(invoiceQuery(selectedId))

  return (
    <div className="grid gap-6">
      <PageHeader title="Счета" description="Исторические начисления сервера. Стоимость здесь не пересчитывается." />
      <div className={`grid min-w-0 gap-6 ${selectedId ? 'xl:grid-cols-[minmax(0,1fr)_460px]' : ''}`}>
        <Card className="min-w-0">
          {invoices.isPending ? <LoadingState /> : null}
          {invoices.isError ? <ErrorState message={getErrorMessage(invoices.error, 'Не удалось загрузить счета.')} onRetry={() => invoices.refetch()} /> : null}
          {invoices.data?.length === 0 ? <EmptyState title="Счетов пока нет" description="Счёт создаётся автоматически после выезда с парковки." /> : null}
          {invoices.data?.length ? <ul className="grid gap-3">{invoices.data.map((invoice) => <li key={invoice.id} className="min-w-0"><button type="button" onClick={() => setSearchParams({ invoice: String(invoice.id) })} className={`flex w-full min-w-0 flex-col items-stretch gap-3 rounded-xl border p-4 text-left transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-blue-200 sm:flex-row sm:items-center sm:gap-4 ${selectedId === invoice.id ? 'border-blue-400 bg-blue-50' : 'border-slate-200 hover:bg-slate-50'}`}><span className="hidden size-11 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-700 sm:grid"><ReceiptText className="size-5" /></span><span className="min-w-0 flex-1"><span className="block font-bold text-slate-950">{invoice.invoiceNumber}</span><span className="mt-1 block truncate text-sm text-slate-500">{invoice.vehiclePlate} · Место {invoice.spotCode} · {formatDateTime(invoice.issuedAt)}</span></span><span className="flex items-center justify-between gap-3 sm:block sm:text-right"><span className="block font-bold text-blue-700">{formatMoney(invoice.totalAmount, invoice.currency)}</span><Badge tone="info">Выставлен</Badge></span></button></li>)}</ul> : null}
        </Card>
        {selectedId ? (
          <Card className="h-fit min-w-0 xl:sticky xl:top-24">
            <div className="flex items-start justify-between gap-3"><div><p className="text-sm text-slate-500">Детализация</p><h2 className="mt-1 text-lg font-bold">{detail.data?.invoiceNumber ?? 'Счёт'}</h2></div><Button variant="ghost" aria-label="Закрыть детали" onClick={() => setSearchParams({})}><X className="size-5" /></Button></div>
            {detail.isPending ? <LoadingState label="Загружаем счёт…" /> : null}
            {detail.isError ? <ErrorState message={getErrorMessage(detail.error, 'Не удалось загрузить счёт.')} onRetry={() => detail.refetch()} /> : null}
            {detail.data ? (
              <div className="mt-5">
                <div className="rounded-xl bg-blue-50 p-4"><p className="text-sm text-blue-700">Итоговая сумма</p><p className="mt-1 text-2xl font-bold text-blue-900">{formatMoney(detail.data.totalAmount, detail.data.currency)}</p></div>
                <dl className="mt-5 grid gap-4 text-sm">
                  <div className="flex justify-between gap-4"><dt className="text-slate-500">Автомобиль / место</dt><dd className="text-right font-semibold">{detail.data.vehiclePlate} / {detail.data.spotCode}</dd></div>
                  <div className="flex justify-between gap-4"><dt className="text-slate-500">Въезд</dt><dd className="text-right font-semibold">{formatDateTime(detail.data.enteredAt)}</dd></div>
                  <div className="flex justify-between gap-4"><dt className="text-slate-500">Выезд</dt><dd className="text-right font-semibold">{formatDateTime(detail.data.exitedAt)}</dd></div>
                  <div className="flex justify-between gap-4"><dt className="flex items-center gap-1 text-slate-500"><Clock3 className="size-4" /> Всего</dt><dd className="font-semibold">{formatDuration(detail.data.totalDurationSeconds)}</dd></div>
                </dl>
                <div className="mt-5 min-w-0 overflow-hidden rounded-xl border border-slate-200"><div className="grid grid-cols-[minmax(0,1fr)_auto_auto] gap-2 bg-slate-50 px-3 py-3 text-[11px] font-semibold uppercase text-slate-500 sm:gap-3 sm:px-4 sm:text-xs"><span>Тариф</span><span>Время</span><span>Сумма</span></div><div className="grid grid-cols-[minmax(0,1fr)_auto_auto] gap-2 border-t border-slate-100 px-3 py-3 text-xs sm:gap-3 sm:px-4 sm:text-sm"><span>Дневной · {formatMoney(detail.data.dayRatePerMinute)}/мин</span><span>{formatDuration(detail.data.dayDurationSeconds)}</span><strong>{formatMoney(detail.data.dayCost)}</strong></div><div className="grid grid-cols-[minmax(0,1fr)_auto_auto] gap-2 border-t border-slate-100 px-3 py-3 text-xs sm:gap-3 sm:px-4 sm:text-sm"><span>Ночной · {formatMoney(detail.data.nightRatePerMinute)}/мин</span><span>{formatDuration(detail.data.nightDurationSeconds)}</span><strong>{formatMoney(detail.data.nightCost)}</strong></div></div>
                <p className="mt-5 flex gap-2 text-xs leading-5 text-slate-500"><FileText className="size-4 shrink-0" /> Тарифы и суммы сохранены Backend в момент выезда. Оплата в текущей версии не предусмотрена.</p>
              </div>
            ) : null}
          </Card>
        ) : null}
      </div>
    </div>
  )
}
