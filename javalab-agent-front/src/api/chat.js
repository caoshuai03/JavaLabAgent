import axios from 'axios';
import apiClient from './index'

export const sendMessage = (message) => {
  return apiClient.post('/api/v1/chat/sse', message)
}

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});
