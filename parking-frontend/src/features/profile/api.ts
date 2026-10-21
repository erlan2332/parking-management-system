import { queryOptions } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client'
import type { UserResponse } from '../../shared/api/types'

export const profileQuery = () => queryOptions({
  queryKey: ['profile'],
  queryFn: ({ signal }) => apiRequest<UserResponse>('/api/users/me', {}, signal),
})
