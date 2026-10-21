import { CarFront, CheckCircle2, Clock3, ShieldCheck } from 'lucide-react'
import type { ReactNode } from 'react'

export function AuthShell({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <main className="grid min-h-screen bg-slate-50 lg:grid-cols-[1.05fr_0.95fr]">
      <section className="hidden bg-slate-950 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="flex items-center gap-3 text-lg font-bold">
          <span className="grid size-11 place-items-center rounded-2xl bg-blue-600"><CarFront className="size-6" /></span>
          Паркинг
        </div>
        <div className="max-w-xl">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-300">Парковка без лишних действий</p>
          <h2 className="mt-5 text-4xl font-bold leading-tight">Выберите место заранее и управляйте поездкой в одном окне.</h2>
          <ul className="mt-10 grid gap-5 text-slate-300">
            <li className="flex gap-3"><CheckCircle2 className="mt-0.5 size-5 text-emerald-400" /> Актуальная схема парковки</li>
            <li className="flex gap-3"><Clock3 className="mt-0.5 size-5 text-blue-300" /> Трёхминутное удержание места</li>
            <li className="flex gap-3"><ShieldCheck className="mt-0.5 size-5 text-violet-300" /> Защищённый вход и личные данные</li>
          </ul>
        </div>
        <p className="text-sm text-slate-500">Часовой пояс парковки: Asia/Bishkek</p>
      </section>
      <section className="flex items-center justify-center p-5 sm:p-10">
        <div className="w-full max-w-md">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <span className="grid size-10 place-items-center rounded-xl bg-blue-600 text-white"><CarFront className="size-5" /></span>
            <span className="font-bold">Паркинг</span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950">{title}</h1>
          <p className="mt-2 text-sm leading-6 text-slate-600">{description}</p>
          <div className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">{children}</div>
        </div>
      </section>
    </main>
  )
}
