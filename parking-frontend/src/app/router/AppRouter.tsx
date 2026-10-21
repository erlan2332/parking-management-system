import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '../../features/auth/LoginPage'
import { PublicOnly, RequireAuth } from '../../features/auth/RouteGuards'
import { RegisterPage } from '../../features/auth/RegisterPage'
import { BarrierPage } from '../../features/barrier/BarrierPage'
import { InvoicesPage } from '../../features/invoices/InvoicesPage'
import { ProfilePage } from '../../features/profile/ProfilePage'
import { ParkingPage } from '../../features/parking/ParkingPage'
import { ReservationsPage } from '../../features/reservations/ReservationsPage'
import { VehiclesPage } from '../../features/vehicles/VehiclesPage'
import { VisitsPage } from '../../features/visits/VisitsPage'
import { AppLayout } from '../layout/AppLayout'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/parking" element={<ParkingPage />} />
          <Route path="/reservations" element={<ReservationsPage />} />
          <Route path="/vehicles" element={<VehiclesPage />} />
          <Route path="/barrier" element={<BarrierPage />} />
          <Route path="/visits" element={<VisitsPage />} />
          <Route path="/invoices" element={<InvoicesPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/parking" replace />} />
      <Route path="*" element={<Navigate to="/parking" replace />} />
    </Routes>
  )
}
