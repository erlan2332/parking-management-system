import { queryOptions } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client'
import type { InvoiceResponse, InvoiceSummaryResponse } from '../../shared/api/types'

export const invoiceKeys = {
  all: ['invoices'] as const,
  detail: (id: number | null) => ['invoices', 'detail', id] as const,
}

export const invoicesQuery = () => queryOptions({
  queryKey: invoiceKeys.all,
  queryFn: ({ signal }) => apiRequest<InvoiceSummaryResponse[]>('/api/invoices/my', {}, signal),
})

export const invoiceQuery = (id: number | null) => queryOptions({
  queryKey: invoiceKeys.detail(id),
  queryFn: ({ signal }) => apiRequest<InvoiceResponse>(`/api/invoices/${id}`, {}, signal),
  enabled: id !== null,
})
