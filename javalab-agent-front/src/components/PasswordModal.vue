<template>
  <div class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <h2>修改密码</h2>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label for="currentPassword">当前密码:</label>
          <input
            id="currentPassword"
            v-model="form.currentPassword"
            type="password"
            placeholder="请输入当前密码"
          />
        </div>
        <div class="form-group">
          <label for="newPassword">新密码:</label>
          <input
            id="newPassword"
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码（至少6位）"
          />
        </div>
        <div class="form-group">
          <label for="confirmNewPassword">确认新密码:</label>
          <input
            id="confirmNewPassword"
            v-model="form.confirmNewPassword"
            type="password"
            placeholder="请再次输入新密码"
          />
        </div>
        <div class="form-actions">
          <button type="button" @click="handleClose">取消</button>
          <button type="submit" :disabled="changing">
            {{ changing ? '修改中...' : '修改密码' }}
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
import { ref } from 'vue'
import { useUserStore } from '../stores/user'

const emit = defineEmits(['close'])

const userStore = useUserStore()
const changing = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const form = ref({
  currentPassword: '',
  newPassword: '',
  confirmNewPassword: ''
})

const handleClose = () => {
  emit('close')
  // 重置表单
  form.value = {
    currentPassword: '',
    newPassword: '',
    confirmNewPassword: ''
  }
  errorMessage.value = ''
  successMessage.value = ''
}

const handleSubmit = async () => {
  changing.value = true
  errorMessage.value = ''
  successMessage.value = ''
  
  // 基本验证
  if (!form.value.currentPassword) {
    errorMessage.value = '请输入当前密码'
    changing.value = false
    return
  }
  
  if (!form.value.newPassword) {
    errorMessage.value = '请输入新密码'
    changing.value = false
    return
  }
  
  if (form.value.newPassword.length < 6) {
    errorMessage.value = '新密码长度不能少于6位'
    changing.value = false
    return
  }
  
  if (form.value.newPassword !== form.value.confirmNewPassword) {
    errorMessage.value = '两次输入的新密码不一致'
    changing.value = false
    return
  }
  
  try {
    const result = await userStore.changePassword(form.value)
    
    if (result !== undefined) {
      successMessage.value = '密码修改成功！'
      setTimeout(() => {
        handleClose()
      }, 2000)
    } else {
      errorMessage.value = '密码修改失败，请稍后重试'
    }
  } catch (error) {
    errorMessage.value = error.message || '密码修改失败'
  } finally {
    changing.value = false
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
    color: #dc3545; // 使用更深的红色，提高可读性
    text-align: center;
    margin-top: 1rem;
    padding: 0.75rem;
    border-radius: 4px;
    background-color: rgba(220, 53, 69, 0.1);
    border: 1px solid rgba(220, 53, 69, 0.3);
    font-weight: 500; // 增加字体粗细
  }
  
  .success-message {
    color: #28a745; // 使用更深的绿色，提高可读性
    text-align: center;
    margin-top: 1rem;
    padding: 0.75rem;
    border-radius: 4px;
    background-color: rgba(40, 167, 69, 0.1);
    border: 1px solid rgba(40, 167, 69, 0.3);
    font-weight: 500; // 增加字体粗细
  }
}
</style>

