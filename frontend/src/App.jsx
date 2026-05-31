import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import PrivateRoute from './components/PrivateRoute'
import Login from './pages/Login'
import OAuth2Callback from './pages/OAuth2Callback'
import Wardrobe from './pages/Wardrobe'
import Recommend from './pages/Recommend'
import Calendar from './pages/Calendar'
import MyPage from './pages/MyPage'

export default function App() {
  const accessToken = useAuthStore((s) => s.accessToken)

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={accessToken ? <Navigate to="/wardrobe" replace /> : <Login />} />
        <Route path="/oauth2/callback" element={<OAuth2Callback />} />

        <Route element={<PrivateRoute />}>
          <Route path="/wardrobe" element={<Wardrobe />} />
          <Route path="/recommend" element={<Recommend />} />
          <Route path="/calendar" element={<Calendar />} />
          <Route path="/mypage" element={<MyPage />} />
        </Route>

        <Route path="*" element={<Navigate to={accessToken ? '/wardrobe' : '/login'} replace />} />
      </Routes>
    </BrowserRouter>
  )
}
