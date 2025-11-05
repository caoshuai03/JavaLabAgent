<template>
  <div :class="['user-profile', { collapsed: chatStore.sidebarCollapsed }]">
    <div class="profile-content">
      <div class="avatar">
        {{ userInitial }}
      </div>
      <transition name="fade">
        <div v-show="!chatStore.sidebarCollapsed" class="user-info">
          <div class="username">{{ displayName }}</div>
          <div class="menu">
            <button @click="showProfileModal" class="menu-item">个人资料</button>
            <button @click="showChangePasswordModal" class="menu-item">修改密码</button>
            <button @click="handleLogout" class="menu-item logout">登出</button>
          </div>
        </div>
      </transition>
      <transition name="fade">
        <div v-show="chatStore.sidebarCollapsed" class="menu-collapsed">
          <button @click="showProfileModal" class="menu-icon" title="个人资料">👤</button>
          <button @click="showChangePasswordModal" class="menu-icon" title="修改密码">🔒</button>
          <button @click="handleLogout" class="menu-icon" title="登出">🚪</button>
        </div>
      </transition>
    </div>
    
    <ProfileModal 
      v-if="showProfileModalFlag" 
      @close="closeProfileModal"
    />
    
    <PasswordModal 
      v-if="showChangePasswordModalFlag" 
      @close="closeChangePasswordModal"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useChatStore } from '../stores/chat'
import ProfileModal from './ProfileModal.vue'
import PasswordModal from './PasswordModal.vue'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const showProfileModalFlag = ref(false)
const showChangePasswordModalFlag = ref(false)

const displayName = computed(() => {
  return userStore.userInfo?.name || userStore.userInfo?.userName || '用户'
})

const userInitial = computed(() => {
  const name = displayName.value
  return name.charAt(0).toUpperCase()
})

const showProfileModal = () => {
  showProfileModalFlag.value = true
}

const closeProfileModal = () => {
  showProfileModalFlag.value = false
}

const showChangePasswordModal = () => {
  showChangePasswordModalFlag.value = true
}

const closeChangePasswordModal = () => {
  showChangePasswordModalFlag.value = false
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.user-profile {
  padding: 12px;
  border-top: 1px solid var(--border-color);
  transition: border-color 0.3s ease, padding 0.3s ease;
  
  &.collapsed {
    padding: 8px;
  }
  
  .profile-content {
    display: flex;
    align-items: center;
    gap: 12px;
    transition: align-items 0.3s ease, gap 0.3s ease;
    
    // 缩回状态下改为垂直布局
    .user-profile.collapsed & {
      flex-direction: column;
      align-items: center;
      gap: 8px;
    }
    
    .avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); // 保持品牌色不变
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-weight: bold;
      font-size: 14px;
      flex-shrink: 0;
      transition: width 0.3s ease, height 0.3s ease, font-size 0.3s ease;
      
      .user-profile.collapsed & {
        width: 40px;
        height: 40px;
        font-size: 16px;
      }
    }
    
    .user-info {
      flex: 1;
      min-width: 0;
      
      .username {
        color: var(--text-primary);
        font-size: 14px;
        font-weight: 500;
        margin-bottom: 4px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      
      .menu {
        display: flex;
        gap: 8px;
        
        .menu-item {
          padding: 4px 8px;
          background: transparent;
          border: none;
          color: var(--text-secondary);
          font-size: 12px;
          cursor: pointer;
          border-radius: 4px;
          transition: all 0.2s;
          
          &:hover {
            background-color: var(--bg-hover);
            color: var(--text-primary);
          }
          
          &.logout:hover {
            background-color: #dc3545; // 保持警告色不变
            color: white;
          }
        }
      }
    }
    
    .menu-collapsed {
      display: flex;
      flex-direction: column;
      gap: 4px;
      align-items: center;
      width: 100%;
      
      .menu-icon {
        width: 100%;
        padding: 8px;
        background: transparent;
        border: none;
        color: var(--text-secondary);
        font-size: 18px;
        cursor: pointer;
        border-radius: 4px;
        transition: all 0.2s;
        display: flex;
        align-items: center;
        justify-content: center;
        
        &:hover {
          background-color: var(--bg-hover);
          color: var(--text-primary);
        }
      }
    }
  }
}

// 过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

