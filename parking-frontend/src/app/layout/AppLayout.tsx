import { useQuery } from '@tanstack/react-query'
import {
  BookOpenCheck, CarFront, DoorOpen, FileText, History, LogOut, Menu, ParkingSquare, UserRound, X,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../features/auth/auth-context'
import { profileQuery } from '../../features/profile/api'
import { Button } from '../../shared/ui/Button'

const navigation = [
  { to: '/parking', label: 'Парковка', icon: ParkingSquare },
  { to: '/reservations', label: 'Мои бронирования', icon: BookOpenCheck },
  { to: '/vehicles', label: 'Мои автомобили', icon: CarFront },
  { to: '/barrier', label: 'Шлагбаум', icon: DoorOpen },
  { to: '/visits', label: 'История парковки', icon: History },
  { to: '/invoices', label: 'Счета', icon: FileText },
  { to: '/profile', label: 'Профиль', icon: UserRound },
]

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <>
      <div className="flex h-20 items-center gap-3 border-b border-slate-800 px-5">
        <span className="grid size-10 place-items-center rounded-xl bg-blue-600 text-white"><CarFront className="size-5" /></span>
        <div><p className="font-bold text-white">Паркинг</p><p className="text-xs text-slate-400">Управление парковкой</p></div>
      </div>
      <nav className="grid gap-1 p-3" aria-label="Основная навигация">
        {navigation.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            className={({ isActive }) => `flex min-h-11 items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-400 ${isActive ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}
          >
            <Icon className="size-5 shrink-0" aria-hidden="true" /> {label}
          </NavLink>
        ))}
      </nav>
    </>
  )
}

export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { logout } = useAuth()
  const profile = useQuery(profileQuery())

  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-[248px_minmax(0,1fr)]">
      <aside className="hidden min-h-screen bg-slate-950 lg:block"><Sidebar /></aside>
      {menuOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button className="absolute inset-0 bg-slate-950/50" aria-label="Закрыть меню" onClick={() => setMenuOpen(false)} />
          <aside className="relative h-full w-[min(84vw,290px)] bg-slate-950 shadow-xl">
            <button className="absolute right-3 top-3 grid size-10 place-items-center rounded-xl text-slate-300 hover:bg-slate-800" onClick={() => setMenuOpen(false)} aria-label="Закрыть меню"><X /></button>
            <Sidebar onNavigate={() => setMenuOpen(false)} />
          </aside>
        </div>
      ) : null}
      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6 lg:px-8">
          <button className="grid size-11 place-items-center rounded-xl text-slate-700 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-blue-200 lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Открыть меню"><Menu /></button>
          <div className="ml-auto flex items-center gap-3">
            <div className="hidden text-right sm:block"><p className="max-w-56 truncate text-sm font-semibold text-slate-900">{profile.data?.email ?? 'Водитель'}</p><p className="text-xs text-slate-500">Личная сессия</p></div>
            <Button variant="ghost" onClick={logout} title="Выйти из аккаунта"><LogOut className="size-4" /><span className="hidden sm:inline">Выйти</span></Button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1500px] p-4 sm:p-6 lg:p-8"><Outlet /></main>
      </div>
    </div>
  )
}
