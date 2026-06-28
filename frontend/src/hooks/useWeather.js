import { useState, useEffect } from 'react'
import { getWeather } from '../api/outfit'

export function useWeather() {
  const [weather, setWeather] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    getWeather()
      .then((res) => setWeather(res.data.data))
      .catch((err) => setError(err))
      .finally(() => setLoading(false))
  }, [])

  return { weather, loading, error }
}
