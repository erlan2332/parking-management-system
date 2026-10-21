import { createContext, useContext } from 'react'

export interface AuthContextValue {
  token: string | null
  isAuthenticated: boolean
  startSession: (token: string) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
