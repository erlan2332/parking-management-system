import { useMutation } from '@tanstack/react-query'
import { LogIn } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../../shared/api/client'
import { Button } from '../../shared/ui/Button'
import { Field, Input } from '../../shared/ui/FormControls'
import { loginRequest } from './api'
import { AuthShell } from './AuthShell'
import { useAuth } from './auth-context'

interface LoginLocationState {
  registeredEmail?: string
  from?: string
}

export function LoginPage() {
  const location = useLocation()
  const state = location.state as LoginLocationState | null
  const [email, setEmail] = useState(state?.registeredEmail ?? '')
  const [password, setPassword] = useState('')
  const { startSession } = useAuth()
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: () => loginRequest(email.trim(), password),
    onSuccess: ({ accessToken }) => {
      startSession(accessToken)
      navigate(state?.from || '/parking', { replace: true })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <AuthShell title="Вход" description="Войдите, чтобы выбрать место, управлять бронированиями и поездками.">
      {state?.registeredEmail ? <p className="mb-5 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">Аккаунт создан. Теперь войдите.</p> : null}
      <form className="grid gap-5" onSubmit={submit}>
        <Field label="Email">
          <Input type="email" autoComplete="email" required maxLength={320} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="driver@example.com" />
        </Field>
        <Field label="Пароль">
          <Input type="password" autoComplete="current-password" required maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} />
        </Field>
        {mutation.isError ? <p className="text-sm text-red-600" role="alert">{getErrorMessage(mutation.error, 'Не удалось войти.')}</p> : null}
        <Button type="submit" className="w-full" loading={mutation.isPending}>
          <LogIn className="size-4" aria-hidden="true" /> Войти
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">Нет аккаунта? <Link className="font-semibold text-blue-700 hover:underline" to="/register">Зарегистрироваться</Link></p>
    </AuthShell>
  )
}
