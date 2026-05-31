import { useEffect } from 'react'
import { useAuthStore } from '../store/authStore'
import client from '../api/client'

export function useAuth() {
  const { user, accessToken, setUser, clearAuth } = useAuthStore()

  useEffect(() => {
    if (accessToken && !user) {
      client.get('/users/me')
        .then((res) => setUser(res.data.data))
        .catch(() => clearAuth())
    }
  }, [accessToken, user, setUser, clearAuth])

  return { user, isLoggedIn: !!accessToken }
}
