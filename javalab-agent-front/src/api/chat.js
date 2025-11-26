import axios from 'axios';
import apiClient from './index'

export const sendMessage = (message) => {
  return apiClient.post('/api/v1/ai/rag', message)
}

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});
