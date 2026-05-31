import client from './client'

export const getClothes = () => client.get('/clothes')

export const uploadClothes = (file) => {
  const form = new FormData()
  form.append('image', file)
  return client.post('/clothes', form)
}

export const updateClothes = (id, data) => client.put(`/clothes/${id}`, data)

export const deleteClothes = (id) => client.delete(`/clothes/${id}`)
