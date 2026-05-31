import axios from 'axios'

export const logout = (refreshToken) =>
  axios.post('/api/auth/logout', { refreshToken })
