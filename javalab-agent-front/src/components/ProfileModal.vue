<template>
  <div class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <h2>个人资料</h2>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label for="name">姓名:</label>
          <input
            id="name"
            v-model="form.name"
            type="text"
            placeholder="请输入姓名"
          />
        </div>
        <div class="form-group">
          <label for="userName">用户名:</label>
          <input
            id="userName"
            v-model="form.userName"
            type="text"
            placeholder="请输入用户名"
          />
        </div>
        <div class="form-actions">
          <button type="button" @click="handleClose">取消</button>
          <button type="submit" :disabled="updating">
            {{ updating ? '更新中...' : '更新' }}
          </button>
        </div>
      </form>
      <div v-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </div>
      <div v-if="successMessage" class="success-message">
        {{ successMessage }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '../stores/user'
import apiClient from '../api'

const emit = defineEmits(['close'])

const userStore = useUserStore()
const updating = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const form = ref({
  id: null,
  name: '',
  userName: ''
})

onMounted(async () => {
  try {
    const response = await apiClient.get(`/v1/user/${userStore.userInfo.id}`)
    const userData = response.data.data
    
    form.value = {
      id: userData.id,
      name: userData.name || '',
      userName: userData.userName || ''
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    form.value = {
      id: userStore.userInfo?.id || null,
      name: userStore.userInfo?.name || '',
      userName: userStore.userInfo?.userName || ''
    }
  }
})

const handleClose = () => {
  emit('close')
}

const handleSubmit = async () => {
  updating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  
  try {
    const result = await userStore.updateUserInfo(form.value)
    
    if (result !== undefined) {
      successMessage.value = '用户信息更新成功！'
      setTimeout(() => {
        handleClose()
      }, 2000)
    } else {
      errorMessage.value = '更新失败，请稍后重试'
    }
  } catch (error) {
    errorMessage.value = error.message || '更新失败'
  } finally {
    updating.value = false
  }
}
</script>

<style lang="scss" scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: var(--bg-primary);
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 500px;
  
  h2 {
    text-align: center;
    margin-bottom: 1.5rem;
    color: var(--text-primary);
  }
  
  .form-group {
    margin-bottom: 1rem;
    
    label {
      display: block;
      margin-bottom: 0.5rem;
      font-weight: bold;
      color: var(--text-primary);
    }
    
    input {
      width: 100%;
      padding: 0.75rem;
      background-color: var(--input-bg);
      border: 1px solid var(--border-color);
      border-radius: 4px;
      font-size: 1rem;
      color: var(--input-text);
      box-sizing: border-box;
      
      &:focus {
        outline: none;
        border-color: #10a37f;
      }
      
      &::placeholder {
        color: var(--text-secondary);
      }
    }
  }
  
  .form-actions {
    display: flex;
    justify-content: space-between;
    margin-top: 1.5rem;
    
    button {
      padding: 0.75rem 1.5rem;
      border: none;
      border-radius: 4px;
      font-size: 1rem;
      cursor: pointer;
      transition: background-color 0.3s;
      
      &:first-child {
        background-color: var(--border-color-hover);
        color: var(--text-primary);
        
        &:hover {
          background-color: var(--bg-hover);
        }
      }
      
      &:last-child {
        background-color: #10a37f;
        color: white;
        
        &:hover:not(:disabled) {
          background-color: #0d8f6e;
        }
        
        &:disabled {
          background-color: var(--border-color-hover);
          cursor: not-allowed;
        }
      }
    }
  }
  
  .error-message {
    color: #f8d7da;
    text-align: center;
    margin-top: 1rem;
    padding: 0.75rem;
    border-radius: 4px;
    background-color: rgba(220, 53, 69, 0.2);
    border: 1px solid rgba(220, 53, 69, 0.5);
  }
  
  .success-message {
    color: #d4edda;
    text-align: center;
    margin-top: 1rem;
    padding: 0.75rem;
    border-radius: 4px;
    background-color: rgba(40, 167, 69, 0.2);
    border: 1px solid rgba(40, 167, 69, 0.5);
  }
}
</style>

