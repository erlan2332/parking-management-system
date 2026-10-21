import { apiRequest } from '../../shared/api/client'
import type { AuthResponse, RegisteredUserResponse } from '../../shared/api/types'

export function loginRequest(email: string, password: string): Promise<AuthResponse> {
  return apiRequest('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function registerRequest(email: string, password: string): Promise<RegisteredUserResponse> {
  return apiRequest('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}
