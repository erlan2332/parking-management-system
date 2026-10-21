import { useMutation } from '@tanstack/react-query'
import { UserPlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../../shared/api/client'
import { Button } from '../../shared/ui/Button'
import { Field, Input } from '../../shared/ui/FormControls'
import { registerRequest } from './api'
import { AuthShell } from './AuthShell'

function validatePassword(password: string): string | null {
  if (password.length < 10) return 'Минимум 10 символов.'
  if (new TextEncoder().encode(password).length > 72) return 'Пароль не должен превышать 72 байта в UTF-8.'
  return null
}

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const navigate = useNavigate()
  const mutation = useMutation({
    mutationFn: () => registerRequest(email.trim(), password),
    onSuccess: (user) => navigate('/login', { replace: true, state: { registeredEmail: user.email } }),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const passwordError = validatePassword(password)
    if (passwordError) return setFormError(passwordError)
    if (password !== confirmation) return setFormError('Пароли не совпадают.')
    setFormError(null)
    mutation.mutate()
  }

  return (
    <AuthShell title="Регистрация" description="Создайте аккаунт водителя. После регистрации потребуется войти.">
      <form className="grid gap-5" onSubmit={submit}>
        <Field label="Email"><Input type="email" autoComplete="email" required maxLength={320} value={email} onChange={(event) => setEmail(event.target.value)} /></Field>
        <Field label="Пароль" hint="От 10 символов, не более 72 байт."><Input type="password" autoComplete="new-password" required value={password} onChange={(event) => setPassword(event.target.value)} /></Field>
        <Field label="Повторите пароль"><Input type="password" autoComplete="new-password" required value={confirmation} onChange={(event) => setConfirmation(event.target.value)} /></Field>
        {formError || mutation.isError ? <p className="text-sm text-red-600" role="alert">{formError ?? getErrorMessage(mutation.error, 'Не удалось зарегистрироваться.')}</p> : null}
        <Button type="submit" className="w-full" loading={mutation.isPending}><UserPlus className="size-4" /> Создать аккаунт</Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">Уже зарегистрированы? <Link className="font-semibold text-blue-700 hover:underline" to="/login">Войти</Link></p>
    </AuthShell>
  )
}
