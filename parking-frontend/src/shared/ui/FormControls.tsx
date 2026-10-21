import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'

interface FieldProps {
  label: string
  error?: string
  hint?: string
  children: ReactNode
}

export function Field({ label, error, hint, children }: FieldProps) {
  return (
    <label className="grid gap-2 text-sm font-medium text-slate-800">
      <span>{label}</span>
      {children}
      {error ? <span className="text-sm font-normal text-red-600">{error}</span> : null}
      {!error && hint ? <span className="text-xs font-normal text-slate-500">{hint}</span> : null}
    </label>
  )
}

export function Input({ className = '', ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`form-control ${className}`} {...props} />
}

export function Select({ className = '', children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={`form-control ${className}`} {...props}>
      {children}
    </select>
  )
}
