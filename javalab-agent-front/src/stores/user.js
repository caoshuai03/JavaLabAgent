import { defineStore } from 'pinia'
import { ref } from 'vue'
import apiClient, { userApi } from '../api'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || null)
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo')) || null)

  // 验证token有效性
  const validateToken = async () => {
    if (!token.value || !userInfo.value) return false

    try {
      // 尝试获取用户信息来验证token有效性
      const response = await apiClient.get('/v1/user/validate?id=' + userInfo.value.id)
      return true
    } catch (error) {
      // 如果返回401，则token无效
      if (error.response?.status === 401) {
        logout() // 清除无效的token
        return false
      }
      // 其他错误可能不是认证问题，返回true
      return true
    }
  }

  const login = async (loginData) => {
    try {
      const response = await userApi.login(loginData)
      const { data } = response.data

      token.value = data.token
      userInfo.value = {
        id: data.id,
        userName: data.userName,
        name: data.name,
        role: data.role || 0  // 用户角色：0-普通用户，1-管理员
      }

      // 保存token和用户信息到localStorage
      localStorage.setItem('token', data.token)
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))

      // 设置默认的Authorization头
      apiClient.defaults.headers.common['Authorization'] = `Bearer ${data.token}`

      return data
    } catch (error) {
      // 更好地处理后端返回的错误信息
      if (error.response && error.response.data) {
        const { code, message } = error.response.data
        if (code === 40000) {
          // 参数错误，根据具体消息提供准确提示
          switch (message) {
            case '密码错误':
              throw new Error('密码错误')
            case '账号不存在':
              throw new Error('账号不存在')
            case '账号被锁定':
              throw new Error('账号已被锁定')
            default:
              throw new Error(message || '登录失败')
          }
        } else if (message) {
          throw new Error(message)
        }
      }
      throw new Error('登录失败，请检查用户名和密码')
    }
  }

  const register = async (registerData) => {
    try {
      const response = await userApi.register(registerData)
      return response.data
    } catch (error) {
      // 更好地处理后端返回的错误信息
      if (error.response && error.response.data) {
        const { code, message } = error.response.data
        if (code === 40000 && message) {
          // 参数错误
          throw new Error(message)
        } else if (message) {
          throw new Error(message)
        }
      }
      throw new Error('注册失败，请稍后重试')
    }
  }

  const logout = () => {
    token.value = null
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    delete apiClient.defaults.headers.common['Authorization']
  }

  const isLoggedIn = () => {
    return !!token.value
  }

  // 更新用户信息
  const updateUserInfo = async (userData) => {
    try {
      const response = await apiClient.put('/v1/user/update/info', userData)
      const { code, data, message } = response.data

      if (code === 0) {
        // 更新成功，更新本地存储的用户信息
        userInfo.value = {
          ...userInfo.value,
          ...userData
        }
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
        return data
      } else {
        throw new Error(message || '更新失败')
      }
    } catch (error) {
      if (error.response && error.response.data) {
        const { code, message } = error.response.data
        if (code === 50002 && message) {
          // 更新错误
          throw new Error(message)
        } else if (message) {
          throw new Error(message)
        }
      }
      throw new Error('更新失败，请稍后重试')
    }
  }

  // 修改密码
  const changePassword = async (passwordData) => {
    try {
      const response = await apiClient.post('/v1/user/updatePassword', {
        // id由后端从Token中获取，不再需要前端传递
        oldPassword: passwordData.currentPassword,
        newPassword: passwordData.newPassword,
        confirmPassword: passwordData.confirmNewPassword
      })
      const { code, data, message } = response.data

      if (code === 0) {
        return data
      } else if (code === 40000 && message) {
        // 参数错误
        throw new Error(message)
      } else {
        throw new Error(message || '密码修改失败')
      }
    } catch (error) {
      if (error.response && error.response.data) {
        const { code, message } = error.response.data
        if ((code === 40000 || code === 50002) && message) {
          // 根据后端返回的ResultUtils.error格式处理错误
          throw new Error(message)
        }
      }
      throw new Error('密码修改失败，请稍后重试')
    }
  }

  // 判断当前用户是否为管理员
  const isAdmin = () => {
    return userInfo.value?.role === 1
  }

  return {
    token,
    userInfo,
    login,
    register,
    logout,
    isLoggedIn,
    validateToken,
    updateUserInfo,
    changePassword,
    isAdmin
  }
})
