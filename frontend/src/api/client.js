import axios from 'axios'
import { useAuthStore } from '../store/authStore'

const client = axios.create({ baseURL: '/api' })

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let isRefreshing = false
let waitQueue = []

const drainQueue = (err, token) => {
  waitQueue.forEach((p) => (err ? p.reject(err) : p.resolve(token)))
  waitQueue = []
}

client.interceptors.response.use(
  (res) => res,
  async (err) => {
    const orig = err.config
    if (err.response?.status !== 401 || orig._retry) return Promise.reject(err)

    if (isRefreshing) {
      return new Promise((resolve, reject) => waitQueue.push({ resolve, reject }))
        .then((token) => {
          orig.headers.Authorization = `Bearer ${token}`
          return client(orig)
        })
    }

    orig._retry = true
    isRefreshing = true

    try {
      const { refreshToken, setTokens, clearAuth } = useAuthStore.getState()
      const { data } = await axios.post('/api/auth/refresh', { refreshToken })
      const { accessToken, refreshToken: newRefresh } = data.data

      setTokens(accessToken, newRefresh)
      drainQueue(null, accessToken)

      orig.headers.Authorization = `Bearer ${accessToken}`
      return client(orig)
    } catch (e) {
      drainQueue(e, null)
      useAuthStore.getState().clearAuth()
      window.location.href = '/login'
      return Promise.reject(e)
    } finally {
      isRefreshing = false
    }
  }
)

export default client
