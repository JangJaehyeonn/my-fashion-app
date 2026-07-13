import client from './client'

export const getOutfits = () => client.get('/outfits')

export const createOutfit = (data) => client.post('/outfits', data)

export const getCalendar = (year, month) =>
  client.get('/calendar', { params: { year, month } })

export const addCalendarEntry = (data) => client.post('/calendar', data)

export const deleteCalendarEntry = (id) => client.delete(`/calendar/${id}`)

export const getWeather = () => client.get('/weather')

export const recommendOutfits = (weather) =>
  client.post('/outfits/recommend', { temperature: weather.temperature, condition: weather.condition })
