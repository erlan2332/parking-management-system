import { Client } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/auth-context'
import { parkingKeys } from '../parking/api'
import { handleParkingMessage } from './realtime-events'
import { RealtimeContext, type RealtimeStatus } from './realtime-context'

const WS_URL = import.meta.env.VITE_WS_URL?.trim() || 'ws://localhost:8080/ws/parking'

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  const queryClient = useQueryClient()
  const [connectionStatus, setConnectionStatus] = useState<RealtimeStatus>('connecting')
  const status: RealtimeStatus = token ? connectionStatus : 'disconnected'

  useEffect(() => {
    if (!token) return

    let connectedOnce = false
    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 4_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => undefined,
    })
    client.onConnect = () => {
      connectedOnce = true
      setConnectionStatus('connected')
      queryClient.invalidateQueries({ queryKey: parkingKeys.availabilityRoot })
      client.subscribe('/topic/parking/changes', (message) => handleParkingMessage(queryClient, message.body))
    }
    client.onWebSocketClose = () => setConnectionStatus(connectedOnce ? 'reconnecting' : 'connecting')
    client.onWebSocketError = () => setConnectionStatus('reconnecting')
    client.onStompError = () => setConnectionStatus('reconnecting')
    client.activate()

    return () => {
      void client.deactivate()
    }
  }, [queryClient, token])

  return <RealtimeContext.Provider value={status}>{children}</RealtimeContext.Provider>
}
