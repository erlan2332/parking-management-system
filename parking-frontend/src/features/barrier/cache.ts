import type { QueryClient } from '@tanstack/react-query'
import { invoiceKeys } from '../invoices/api'
import { parkingKeys } from '../parking/api'
import { reservationKeys } from '../reservations/api'
import { visitKeys } from '../visits/api'

export function refreshAfterBarrierOperation(queryClient: QueryClient): void {
  queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
  queryClient.invalidateQueries({ queryKey: visitKeys.all })
  queryClient.invalidateQueries({ queryKey: invoiceKeys.all })
  queryClient.invalidateQueries({ queryKey: reservationKeys.all })
}
