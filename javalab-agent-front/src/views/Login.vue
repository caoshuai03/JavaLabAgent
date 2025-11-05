<template>
  <div class="login-container">
    <div class="login-form">
      <h2 v-if="!isRegister">用户登录</h2>
      <h2 v-else>用户注册</h2>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label for="username">用户名:</label>
          <input 
            id="username" 
            v-model="form.userName" 
            type="text" 
            required 
            placeholder="请输入用户名"
          />
        </div>
        <div class="form-group">
          <label for="password">密码:</label>
          <input 
            id="password" 
            v-model="form.password" 
            type="password" 
            required 
            placeholder="请输入密码"
          />
        </div>
        <div class="form-group" v-if="isRegister">
          <label for="confirmPassword">确认密码:</label>
          <input 
            id="confirmPassword" 
            v-model="form.confirmPassword" 
            type="password" 
            required 
            placeholder="请再次输入密码"
          />
        </div>
        <div class="form-group">
          <button type="submit" :disabled="loading">
            {{ loading ? (isRegister ? '注册中...' : '登录中...') : (isRegister ? '注册' : '登录') }}
          </button>
        </div>
        <div class="form-group">
          <button type="button" class="toggle-button" @click="toggleMode">
            {{ isRegister ? '已有账号？立即登录' : '没有账号？立即注册' }}
          </button>
        </div>
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const isRegister = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const form = ref({
  userName: '',
  password: '',
  confirmPassword: ''
})

const toggleMode = () => {
  isRegister.value = !isRegister.value
  errorMessage.value = ''
  // 切换模式时重置表单
  form.value = {
    userName: '',
    password: '',
    confirmPassword: ''
  }
}

const handleSubmit = async () => {
  if (isRegister.value) {
    await handleRegister()
  } else {
    await handleLogin()
  }
}

const handleLogin = async () => {
  loading.value = true
  errorMessage.value = ''
  
  try {
    await userStore.login({
      userName: form.value.userName,
      password: form.value.password
    })
    router.push('/')
  } catch (error) {
    // 根据后端返回的错误信息显示具体错误
    if (error.message) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '登录失败，请检查用户名和密码'
    }
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  loading.value = true
  errorMessage.value = ''
  
  // 检查密码确认
  if (form.value.password !== form.value.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    loading.value = false
    return
  }
  
  try {
    // 注册时传递用户输入的用户名和密码
    const registerResult = await userStore.register({
      userName: form.value.userName,
      password: form.value.password
    })
    
    // 检查注册是否成功
    if (registerResult && registerResult.code !== 0) {
      // 注册失败，显示错误信息
      errorMessage.value = registerResult.message || '注册失败'
      loading.value = false
      return
    }
    
    // 注册成功后使用用户输入的密码直接登录
    await handleLogin()
  } catch (error) {
    // 根据后端返回的错误信息显示具体错误
    if (error.message) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '注册失败，请稍后重试'
    }
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f0f2f5;
  
  .login-form {
    background: white;
    padding: 2rem;
    border-radius: 8px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
    width: 100%;
    max-width: 400px;
    
    h2 {
      text-align: center;
      margin-bottom: 1.5rem;
      color: #333;
    }
    
    .form-group {
      margin-bottom: 1rem;
      
      label {
        display: block;
        margin-bottom: 0.5rem;
        font-weight: bold;
        color: #555;
      }
      
      input {
        width: 100%;
        padding: 0.75rem;
        border: 1px solid #ddd;
        border-radius: 4px;
        font-size: 1rem;
        box-sizing: border-box;
        
        &:focus {
          outline: none;
          border-color: #007bff;
        }
      }
      
      button {
        width: 100%;
        padding: 0.75rem;
        background-color: #007bff;
        color: white;
        border: none;
        border-radius: 4px;
        font-size: 1rem;
        cursor: pointer;
        transition: background-color 0.3s;
        
        &:hover:not(:disabled) {
          background-color: #0056b3;
        }
        
        &:disabled {
          background-color: #ccc;
          cursor: not-allowed;
        }
      }
      
      .toggle-button {
        background-color: #28a745;
        
        &:hover:not(:disabled) {
          background-color: #218838;
        }
      }
    }
    
    .error-message {
      color: #721c24;
      text-align: center;
      margin-top: 1rem;
      padding: 0.75rem;
      border-radius: 4px;
      background-color: #f8d7da;
      border: 1px solid #f5c6cb;
    }
  }
}
</style>