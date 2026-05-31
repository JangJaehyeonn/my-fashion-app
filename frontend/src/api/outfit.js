import axios from 'axios'
import client from './client'

export const getOutfits = () => client.get('/outfits')

export const createOutfit = (data) => client.post('/outfits', data)

export const deleteOutfit = (id) => client.delete(`/outfits/${id}`)

export const getCalendar = (year, month) =>
  client.get('/calendar', { params: { year, month } })

export const addCalendarEntry = (data) => client.post('/calendar', data)

export const deleteCalendarEntry = (id) => client.delete(`/calendar/${id}`)

// 개발용 직접 호출 — 프로덕션에서는 Spring Boot 프록시 엔드포인트로 교체
export const getWeather = (nx = 60, ny = 127) =>
  axios.get('/ai/weather', { params: { nx, ny } })

export const recommendOutfits = (weather, clothes) =>
  axios.post('/ai/outfits/recommend', { weather, clothes })
