import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { LoginPage } from './LoginPage'
import { RequireAuth } from './RouteGuards'
import { AuthProvider } from './AuthContext'

function wrapper(children: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={client}><AuthProvider>{children}</AuthProvider></QueryClientProvider>
}

describe('authentication flow', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  it('redirects a visitor away from a protected route', async () => {
    render(wrapper(
      <MemoryRouter initialEntries={['/parking']}>
        <Routes>
          <Route element={<RequireAuth />}><Route path="/parking" element={<p>Закрытая парковка</p>} /></Route>
          <Route path="/login" element={<p>Страница входа</p>} />
        </Routes>
      </MemoryRouter>,
    ))
    expect(await screen.findByText('Страница входа')).toBeInTheDocument()
  })

  it('stores the JWT and opens the protected application after login', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      accessToken: 'test-jwt', tokenType: 'Bearer', expiresIn: 3600,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(wrapper(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/parking" element={<p>Парковка открыта</p>} />
        </Routes>
      </MemoryRouter>,
    ))
    await user.type(screen.getByLabelText('Email'), 'driver@example.com')
    await user.type(screen.getByLabelText('Пароль'), 'VerySecure123')
    await user.click(screen.getByRole('button', { name: /Войти/ }))

    expect(await screen.findByText('Парковка открыта')).toBeInTheDocument()
    expect(sessionStorage.getItem('parking.access-token')).toBe('test-jwt')
    expect(fetchMock).toHaveBeenCalledOnce()
  })
})
