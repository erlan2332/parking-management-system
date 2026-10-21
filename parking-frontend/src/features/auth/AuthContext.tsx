import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { getAccessToken, removeAccessToken, saveAccessToken } from '../../shared/lib/auth-storage'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState<string | null>(() => getAccessToken())

  const startSession = useCallback((nextToken: string) => {
    saveAccessToken(nextToken)
    setToken(nextToken)
  }, [])

  const logout = useCallback(() => {
    removeAccessToken()
    setToken(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    window.addEventListener('parking:unauthorized', logout)
    return () => window.removeEventListener('parking:unauthorized', logout)
  }, [logout])

  const value = useMemo(() => ({ token, isAuthenticated: Boolean(token), startSession, logout }), [token, startSession, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
