import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import BottomNav from './BottomNav'

export default function PrivateRoute() {
  const accessToken = useAuthStore((s) => s.accessToken)

  if (!accessToken) return <Navigate to="/login" replace />

  return (
    <>
      <Outlet />
      <BottomNav />
    </>
  )
}
