<template>
  <div class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <h2>修改密码</h2>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label for="currentPassword">当前密码</label>
          <input
            id="currentPassword"
            v-model="form.currentPassword"
            type="password"
            placeholder="输入当前使用的密码"
          />
        </div>
        <div class="form-group">
          <label for="newPassword">新密码</label>
          <input
            id="newPassword"
            v-model="form.newPassword"
            type="password"
            placeholder="设置新密码（不少于6位）"
          />
        </div>
        <div class="form-group">
          <label for="confirmNewPassword">确认新密码</label>
          <input
            id="confirmNewPassword"
            v-model="form.confirmNewPassword"
            type="password"
            placeholder="再次输入新密码以确认"
          />
        </div>
        <div class="form-actions">
          <button type="button" @click="handleClose">取消</button>
          <button type="submit" :disabled="changing">
            {{ changing ? '修改中...' : '确定修改' }}
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
  confirmNewPassword: '',
})

const handleClose = () => {
  emit('close')
  // 重置表单
  form.value = {
    currentPassword: '',
    newPassword: '',
    confirmNewPassword: '',
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
  padding: 1.5rem 2rem;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 440px;

  h2 {
    text-align: left;
    margin-bottom: 1.5rem;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
    padding-bottom: 12px;
    border-bottom: 1px solid var(--border-color);
  }

  .form-group {
    margin-bottom: 1.25rem;

    label {
      display: block;
      margin-bottom: 0.5rem;
      font-size: 13px;
      font-weight: 500;
      color: var(--text-primary);
    }

    input {
      width: 100%;
      padding: 8px 12px;
      background-color: var(--bg-primary);
      border: 1px solid var(--border-color);
      border-radius: 6px;
      font-size: 14px;
      color: var(--text-primary);
      box-sizing: border-box;
      transition: all 0.2s ease;

      &:focus {
        outline: none;
        border-color: #90138b;
        box-shadow: 0 0 0 2px rgba(144, 19, 139, 0.1);
      }

      &::placeholder {
        color: var(--text-secondary);
        opacity: 0.6;
      }
    }
  }

  .form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 2rem;

    button {
      padding: 8px 16px;
      border: none;
      border-radius: 6px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s ease;

      &:first-child {
        background-color: transparent;
        color: var(--text-secondary);
        border: 1px solid var(--border-color);

        &:hover {
          background-color: var(--bg-hover);
          color: var(--text-primary);
        }
      }

      &:last-child {
        background-color: #90138b;
        color: white;

        &:hover:not(:disabled) {
          background-color: #a01ba0;
          box-shadow: 0 2px 8px rgba(144, 19, 139, 0.2);
        }

        &:disabled {
          background-color: var(--border-color);
          opacity: 0.5;
          cursor: not-allowed;
        }
      }
    }
  }

  .error-message {
    color: #dc3545;
    text-align: center;
    margin-top: 1rem;
    padding: 0.75rem;
    border-radius: 6px;
    background-color: rgba(220, 53, 69, 0.05);
    border: 1px solid rgba(220, 53, 69, 0.1);
    font-size: 13px;
  }

  .success-message {
    color: #28a745;
    text-align: center;
    margin-top: 1rem;
    padding: 0.75rem;
    border-radius: 6px;
    background-color: rgba(40, 167, 69, 0.05);
    border: 1px solid rgba(40, 167, 69, 0.1);
    font-size: 13px;
  }
}
</style>
