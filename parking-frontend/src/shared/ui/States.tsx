import { AlertCircle, Inbox, LoaderCircle, RefreshCw } from 'lucide-react'
import { Button } from './Button'

export function LoadingState({ label = 'Загружаем данные…' }: { label?: string }) {
  return (
    <div className="state-panel" role="status">
      <LoaderCircle className="size-6 animate-spin text-blue-600" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="state-panel">
      <Inbox className="size-7 text-slate-400" aria-hidden="true" />
      <strong className="text-slate-900">{title}</strong>
      <span className="max-w-lg text-sm text-slate-500">{description}</span>
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state-panel" role="alert">
      <AlertCircle className="size-7 text-red-500" aria-hidden="true" />
      <span className="text-sm text-red-700">{message}</span>
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry}>
          <RefreshCw className="size-4" aria-hidden="true" /> Повторить
        </Button>
      ) : null}
    </div>
  )
}
