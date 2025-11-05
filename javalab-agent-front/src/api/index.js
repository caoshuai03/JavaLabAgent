import axios from 'axios'

// 在生产环境中，API 前缀是 /api
// 在开发环境中，我们使用 /api 但通过 Vite 代理到实际的后端
const getBaseURL = () => {
  if (import.meta.env.DEV) {
    // 开发环境 - 使用相对路径，让 Vite 处理代理
    return '/api'
  } else {
    // 生产环境 - Nginx 会处理 /api 前缀
    return '/api'
  }
}

const apiClient = axios.create({
  baseURL: getBaseURL(),
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      // 未授权，清除本地存储的token
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default apiClient

// 导出用户相关API
export const userApi = {
  // 登录API需要使用表单格式参数
  login: (data) => {
    const params = new URLSearchParams();
    params.append('userName', data.userName);
    params.append('password', data.password);
    return apiClient.post('/v1/user/login', params, {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });
  },
  logout: () => apiClient.post('/v1/user/logout'),
  register: (data) => apiClient.post('/v1/user/register', data),
  // 获取用户信息
  getUserInfo: (id) => apiClient.get(`/v1/user/${id}`)
}