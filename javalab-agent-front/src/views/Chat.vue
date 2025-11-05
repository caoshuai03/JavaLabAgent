<template>
  <div class="chat-container">
    <div class="header">
      <div class="user-info">
        <span v-if="userStore.userInfo">欢迎, {{ userStore.userInfo.name || userStore.userInfo.userName }}!</span>
      </div>
      <div class="header-buttons">
        <button @click="showProfileModal" class="profile-button">个人资料</button>
        <button @click="showChangePasswordModal" class="password-button">修改密码</button>
        <button @click="handleLogout" class="logout-button">登出</button>
      </div>
    </div>
    <div class="messages-area">
      <div v-for="message in messages" :key="message.id" class="message">
        <span class="message-sender">{{ message.sender }}: </span>
        <span class="message-content">{{ message.content }}</span>
      </div>
    </div>
    <div class="input-area">
      <input v-model="newMessage" @keyup.enter="sendMessage" placeholder="Type a message..." />
      <button @click="sendMessage">Send</button>
      <button @click="stopResponse" class="stop-button">Stop</button>
    </div>

    <!-- 个人资料模态框 -->
    <div v-if="showProfileModalFlag" class="modal-overlay" @click="closeProfileModal">
      <div class="modal-content" @click.stop>
        <h2>个人资料</h2>
        <form @submit.prevent="updateProfile">
          <div class="form-group">
            <label for="name">姓名:</label>
            <input
              id="name"
              v-model="profileForm.name"
              type="text"
              placeholder="请输入姓名"
            />
          </div>
          <div class="form-group">
            <label for="userName">用户名:</label>
            <input
              id="userName"
              v-model="profileForm.userName"
              type="text"
              placeholder="请输入用户名"
            />
          </div>
          <div class="form-group">
            <label for="phone">手机号:</label>
            <input
              id="phone"
              v-model="profileForm.phone"
              type="text"
              placeholder="请输入手机号"
            />
          </div>
          <div class="form-actions">
            <button type="button" @click="closeProfileModal">取消</button>
            <button type="submit" :disabled="updatingProfile">
              {{ updatingProfile ? '更新中...' : '更新' }}
            </button>
          </div>
        </form>
        <div v-if="profileErrorMessage" class="error-message">
          {{ profileErrorMessage }}
        </div>
        <div v-if="profileSuccessMessage" class="success-message">
          {{ profileSuccessMessage }}
        </div>
      </div>
    </div>

    <!-- 修改密码模态框 -->
    <div v-if="showChangePasswordModalFlag" class="modal-overlay" @click="closeChangePasswordModal">
      <div class="modal-content" @click.stop>
        <h2>修改密码</h2>
        <form @submit.prevent="changePassword">
          <div class="form-group">
            <label for="currentPassword">当前密码:</label>
            <input
              id="currentPassword"
              v-model="changePasswordForm.currentPassword"
              type="password"
              placeholder="请输入当前密码"
            />
          </div>
          <div class="form-group">
            <label for="newPassword">新密码:</label>
            <input
              id="newPassword"
              v-model="changePasswordForm.newPassword"
              type="password"
              placeholder="请输入新密码（至少6位）"
            />
          </div>
          <div class="form-group">
            <label for="confirmNewPassword">确认新密码:</label>
            <input
              id="confirmNewPassword"
              v-model="changePasswordForm.confirmNewPassword"
              type="password"
              placeholder="请再次输入新密码"
            />
          </div>
          <div class="form-actions">
            <button type="button" @click="closeChangePasswordModal">取消</button>
            <button type="submit" :disabled="changingPassword">
              {{ changingPassword ? '修改中...' : '修改密码' }}
            </button>
          </div>
        </form>
        <div v-if="passwordErrorMessage" class="error-message">
          {{ passwordErrorMessage }}
        </div>
        <div v-if="passwordSuccessMessage" class="success-message">
          {{ passwordSuccessMessage }}
        </div>
      </div>
    </div>

    <!-- 更新结果提示 -->
    <div v-if="updateResultMessage" :class="['update-result', updateResultType]">
      {{ updateResultMessage }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import apiClient from '../api'

const router = useRouter()
const userStore = useUserStore()

const messages = ref([])
const newMessage = ref('')
let eventSource = null

// 个人资料相关
const showProfileModalFlag = ref(false)
const showChangePasswordModalFlag = ref(false)
const updatingProfile = ref(false)
const profileErrorMessage = ref('')
const profileSuccessMessage = ref('')
const profileForm = ref({
  id: null,
  name: '',
  userName: '',
  phone: ''
})

// 密码修改相关
const changePasswordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmNewPassword: ''
})
const changingPassword = ref(false)
const passwordErrorMessage = ref('')
const passwordSuccessMessage = ref('')

// 更新结果提示相关
const updateResultMessage = ref('')
const updateResultType = ref('')

onMounted(() => {
  // 移除了token验证逻辑，统一在路由守卫中处理
  // 如果路由守卫正常工作，这里可以确保用户已经通过验证
})

const getApiBaseUrl = () => {
  if (import.meta.env.DEV) {
    // 开发环境
    return ''
  } else {
    // 生产环境 - Nginx 部署
    return ''
  }
}

const sendMessage = () => {
  if (newMessage.value.trim() !== '') {
    const userMessage = { id: Date.now(), sender: 'User', content: newMessage.value };
    messages.value.push(userMessage);

    // 使用完整的API路径，让Nginx处理代理
    const baseUrl = getApiBaseUrl();
    eventSource = new EventSource(`${baseUrl}/api/v1/chat/stream?prompt=${encodeURIComponent(newMessage.value)}`);
    const botMessage = { id: Date.now() + 1, sender: 'Bot', content: '' };
    messages.value.push(botMessage);

    eventSource.onmessage = (event) => {
      const lastMessage = messages.value[messages.value.length - 1];
      // 检查是否是错误消息
      if (event.data.startsWith('[ERROR]')) {
        // 如果是错误消息，添加错误信息并关闭连接
        lastMessage.content += '\n错误：' + event.data.substring(7); // 移除 '[ERROR] ' 前缀
        eventSource.close();
        return;
      }
      lastMessage.content += event.data;
    };

    eventSource.onerror = (error) => {
      console.error('SSE连接错误:', error);
      eventSource.close();
    };

    newMessage.value = '';
  }
};

const stopResponse = () => {
  if (eventSource) {
    eventSource.close();
  }
};

const handleLogout = () => {
  userStore.logout();
  router.push('/login');
};

// 显示个人资料模态框
const showProfileModal = async () => {
  try {
    // 从后端获取最新的用户信息
    const response = await apiClient.get(`/v1/user/${userStore.userInfo.id}`)
    const userData = response.data.data

    // 初始化表单数据
    profileForm.value = {
      id: userData.id,
      name: userData.name || '',
      userName: userData.userName || '',
      phone: userData.phone || ''
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    // 如果获取失败，仍然使用store中的信息
    profileForm.value = {
      id: userStore.userInfo?.id || null,
      name: userStore.userInfo?.name || '',
      userName: userStore.userInfo?.userName || '',
      phone: userStore.userInfo?.phone || ''
    }
  }

  showProfileModalFlag.value = true
  // 清除之前的消息
  profileErrorMessage.value = ''
  profileSuccessMessage.value = ''
}

// 显示修改密码模态框
const showChangePasswordModal = () => {
  // 重置表单
  changePasswordForm.value = {
    currentPassword: '',
    newPassword: '',
    confirmNewPassword: ''
  }
  showChangePasswordModalFlag.value = true
  // 清除之前的消息
  passwordErrorMessage.value = ''
  passwordSuccessMessage.value = ''
}

// 关闭个人资料模态框
const closeProfileModal = () => {
  showProfileModalFlag.value = false
  profileErrorMessage.value = ''
  profileSuccessMessage.value = ''
}

// 关闭修改密码模态框
const closeChangePasswordModal = () => {
  showChangePasswordModalFlag.value = false
  passwordErrorMessage.value = ''
  passwordSuccessMessage.value = ''
}

// 显示更新结果提示
const showUpdateResult = (message, type) => {
  updateResultMessage.value = message
  updateResultType.value = type

  // 3秒后自动隐藏提示
  setTimeout(() => {
    updateResultMessage.value = ''
    updateResultType.value = ''
  }, 3000)
}

// 更新个人资料
const updateProfile = async () => {
  updatingProfile.value = true
  profileErrorMessage.value = ''
  profileSuccessMessage.value = ''

  try {
    const result = await userStore.updateUserInfo(profileForm.value)

    if (result !== undefined) {
      // 更新成功后显示成功消息
      profileSuccessMessage.value = '用户信息更新成功！'
      // 2秒后自动关闭模态框
      setTimeout(() => {
        closeProfileModal()
        showUpdateResult('用户信息更新成功！', 'success')
      }, 2000)
    } else {
      // 更新失败
      profileErrorMessage.value = '更新失败，请稍后重试'
      showUpdateResult('用户信息更新失败！', 'error')
    }
  } catch (error) {
    profileErrorMessage.value = error.message || '更新失败'
    showUpdateResult(error.message || '用户信息更新失败！', 'error')
  } finally {
    updatingProfile.value = false
  }
}

// 修改密码
const changePassword = async () => {
  changingPassword.value = true
  passwordErrorMessage.value = ''
  passwordSuccessMessage.value = ''

  // 基本验证
  if (!changePasswordForm.value.currentPassword) {
    passwordErrorMessage.value = '请输入当前密码'
    changingPassword.value = false
    return
  }

  if (!changePasswordForm.value.newPassword) {
    passwordErrorMessage.value = '请输入新密码'
    changingPassword.value = false
    return
  }

  if (changePasswordForm.value.newPassword.length < 6) {
    passwordErrorMessage.value = '新密码长度不能少于6位'
    changingPassword.value = false
    return
  }

  if (changePasswordForm.value.newPassword !== changePasswordForm.value.confirmNewPassword) {
    passwordErrorMessage.value = '两次输入的新密码不一致'
    changingPassword.value = false
    return
  }

  try {
    const result = await userStore.changePassword(changePasswordForm.value)

    if (result !== undefined) {
      // 更新成功后显示成功消息
      passwordSuccessMessage.value = '密码修改成功！'
      // 2秒后自动关闭模态框
      setTimeout(() => {
        closeChangePasswordModal()
        showUpdateResult('密码修改成功！', 'success')
      }, 2000)
    } else {
      // 更新失败
      passwordErrorMessage.value = '密码修改失败，请稍后重试'
      showUpdateResult('密码修改失败！', 'error')
    }
  } catch (error) {
    passwordErrorMessage.value = error.message || '密码修改失败'
    showUpdateResult(error.message || '密码修改失败！', 'error')
  } finally {
    changingPassword.value = false
  }
}
</script>

<style lang="scss" scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f0f2f5;
  padding: 1rem;
  box-sizing: border-box;

  .update-result {
    position: fixed;
    top: 20px;
    right: 20px;
    padding: 1rem;
    border-radius: 4px;
    color: white;
    font-weight: bold;
    z-index: 1000;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);

    &.success {
      background-color: #28a745;
    }

    &.error {
      background-color: #dc3545;
    }
  }
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding: 0.5rem;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);

  .user-info {
    font-weight: bold;
    color: #333;
  }

  .header-buttons {
    display: flex;
    gap: 0.5rem;

    .profile-button,
    .logout-button {
      padding: 0.5rem 1rem;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      transition: background-color 0.3s;

      &:hover {
        opacity: 0.9;
      }
    }

    .profile-button {
      background-color: #28a745;
      color: white;
    }

    .logout-button {
      background-color: #dc3545;
      color: white;
    }
  }
}

.messages-area {
  flex-grow: 1;
  overflow-y: auto;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
  background-color: #ffffff;
}

.message {
  margin-bottom: 1rem;
  display: flex;

  .message-sender {
    font-weight: bold;
    margin-right: 0.5rem;
    color: #333;
  }

  .message-content {
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.input-area {
  display: flex;
  align-items: center;

  input {
    flex-grow: 1;
    padding: 0.75rem;
    border: 1px solid #ccc;
    border-radius: 18px;
    font-size: 1rem;

    &:focus {
      outline: none;
      border-color: #007bff;
    }
  }

  button {
    margin-left: 0.5rem;
    padding: 0.75rem 1.5rem;
    border: none;
    background-color: #007bff;
    color: white;
    border-radius: 18px;
    cursor: pointer;
    font-size: 1rem;
    transition: background-color 0.3s;

    &:hover {
      background-color: #0056b3;
    }
  }

  .stop-button {
    background-color: #dc3545;

    &:hover {
      background-color: #c82333;
    }
  }
}

// 模态框样式
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
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 500px;

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
        background-color: #6c757d;
        color: white;

        &:hover {
          background-color: #5a6268;
        }
      }

      &:last-child {
        background-color: #007bff;
        color: white;

        &:hover:not(:disabled) {
          background-color: #0056b3;
        }

        &:disabled {
          background-color: #ccc;
          cursor: not-allowed;
        }
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

  .success-message {
    color: #155724;
    text-align: center;
    margin-top: 1rem;
    padding: 0.75rem;
    border-radius: 4px;
    background-color: #d4edda;
    border: 1px solid #c3e6cb;
  }
}
</style>
