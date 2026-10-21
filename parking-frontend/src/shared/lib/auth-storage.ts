const TOKEN_KEY = 'parking.access-token'

export function getAccessToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function saveAccessToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function removeAccessToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}
