import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Mail, UserRound } from 'lucide-react'
import { getErrorMessage } from '../../shared/api/client'
import { formatDateTime } from '../../shared/lib/format'
import { Card, PageHeader } from '../../shared/ui/Surface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { profileQuery } from './api'

export function ProfilePage() {
  const profile = useQuery(profileQuery())
  return (
    <div className="grid gap-6">
      <PageHeader title="Профиль" />
      {profile.isPending ? <LoadingState /> : null}
      {profile.isError ? <ErrorState message={getErrorMessage(profile.error, 'Не удалось загрузить профиль.')} onRetry={() => profile.refetch()} /> : null}
      {profile.data ? (
        <Card className="max-w-2xl">
          <div className="flex items-center gap-4 border-b border-slate-100 pb-5">
            <span className="grid size-14 place-items-center rounded-2xl bg-blue-50 text-blue-700"><UserRound className="size-7" /></span>
            <div><p className="text-sm text-slate-500">Водитель</p><p className="font-semibold text-slate-950">Пользователь #{profile.data.id}</p></div>
          </div>
          <dl className="mt-5 grid gap-5 sm:grid-cols-2">
            <div><dt className="flex items-center gap-2 text-sm text-slate-500"><Mail className="size-4" /> Email</dt><dd className="mt-1 break-all font-medium">{profile.data.email}</dd></div>
            <div><dt className="flex items-center gap-2 text-sm text-slate-500"><CalendarDays className="size-4" /> Дата регистрации</dt><dd className="mt-1 font-medium">{formatDateTime(profile.data.createdAt)}</dd></div>
          </dl>
        </Card>
      ) : null}
    </div>
  )
}
