import { createContext, useContext } from 'react'

export type RealtimeStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting'

export const RealtimeContext = createContext<RealtimeStatus>('disconnected')

export function useRealtimeStatus(): RealtimeStatus {
  return useContext(RealtimeContext)
}
