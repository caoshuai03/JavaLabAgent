<template>
  <div :class="['user-profile', { collapsed: chatStore.sidebarCollapsed }]">
    <div class="profile-content" v-click-outside="closeDropdown">
      <div class="profile-header" @click="toggleDropdown">
        <div class="avatar">
          <DefaultAvatarIcon :size="32" />
        </div>
        <transition name="fade">
          <div v-show="!chatStore.sidebarCollapsed" class="user-info">
            <div class="username">{{ displayName }}</div>
          </div>
        </transition>
      </div>

      <transition name="dropdown">
        <div v-show="showDropdown" :class="['dropdown-menu', { collapsed: chatStore.sidebarCollapsed }]">
          <button @click="handleMenuClick('profile')" class="menu-item">
            <UserIcon :size="18" />
            <span v-if="!chatStore.sidebarCollapsed">个人资料</span>
          </button>
          <button @click="handleMenuClick('password')" class="menu-item">
            <LockIcon :size="18" />
            <span v-if="!chatStore.sidebarCollapsed">修改密码</span>
          </button>
          <button @click="handleMenuClick('logout')" class="menu-item logout">
            <LogoutIcon :size="18" />
            <span v-if="!chatStore.sidebarCollapsed">退出登录</span>
          </button>
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
import UserIcon from './icons/UserIcon.vue'
import DefaultAvatarIcon from './icons/DefaultAvatarIcon.vue'
import LockIcon from './icons/LockIcon.vue'
import LogoutIcon from './icons/LogoutIcon.vue'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const showProfileModalFlag = ref(false)
const showChangePasswordModalFlag = ref(false)
const showDropdown = ref(false)

const displayName = computed(() => {
  return userStore.userInfo?.name || userStore.userInfo?.userName || '用户'
})

const userInitial = computed(() => {
  const name = displayName.value
  return name.charAt(0).toUpperCase()
})

const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value
}

const closeDropdown = () => {
  showDropdown.value = false
}

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

const modalActions = {
  profile: showProfileModal,
  password: showChangePasswordModal,
  logout: handleLogout
}

const handleMenuClick = (action) => {
  closeDropdown()
  modalActions[action]?.()
}

const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = (event) => {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value()
      }
    }
    document.addEventListener('click', el.clickOutsideEvent, true)
  },
  unmounted(el) {
    if (el.clickOutsideEvent) {
      document.removeEventListener('click', el.clickOutsideEvent, true)
    }
  }
}
</script>

<style lang="scss" scoped>
.user-profile {
  padding: 8px;
  transition: border-color 0.3s ease, padding 0.3s ease;

  &.collapsed {
    padding: 0;
    border-top: none;
  }

  .profile-content {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    position: relative;

    .user-profile.collapsed & {
      align-items: center;
      justify-content: center;
    }

      .profile-header {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;
      cursor: pointer;
      padding: 6px;
      border-radius: 8px;
      transition: background-color 0.2s ease;

      .user-profile.collapsed & {
        width: auto;
        padding: 0;
        justify-content: center;
        cursor: pointer;
      }

      &:hover {
        background-color: var(--bg-hover);
      }

      .avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        transition: transform 0.2s ease;
        overflow: hidden;

        .profile-header:hover & {
          transform: scale(1.05);
        }

        .user-profile.collapsed & {
          width: 32px;
          height: 32px;
        }
      }

      .user-info {
        flex: 1;
        min-width: 0;

        .username {
          color: var(--text-primary);
          font-size: 14px;
          font-weight: 500;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }

    .dropdown-menu {
      position: absolute;
      bottom: 100%;
      left: 0;
      right: 0;
      background: var(--bg-primary);
      // 去掉下拉菜单外边框线（参考设计稿/截图样式）
      border: none;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      padding: 4px;
      margin-bottom: 8px;
      z-index: 1000;
      min-width: 180px;
      overflow: hidden;

      &.collapsed {
        left: auto;
        right: 0;
        min-width: 160px;
      }

      .menu-item {
        width: 100%;
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px 12px;
        background: transparent;
        border: none;
        color: var(--text-primary);
        font-size: 14px;
        cursor: pointer;
        border-radius: 4px;
        transition: background-color 0.15s ease;
        text-align: left;

        svg {
          flex-shrink: 0;
          width: 18px;
          height: 18px;
        }

        &:not(:last-child) {
          // 去掉菜单项之间的分割线（参考设计稿/截图样式）
          border-bottom: none;
          margin-bottom: 0;
          padding-bottom: 10px;
        }

        &:hover {
          background-color: var(--bg-hover);
        }

        &.logout {
          color: #dc3545;

          &:hover {
            background-color: rgba(220, 53, 69, 0.1);
            color: #dc3545;
          }
        }
      }
    }

  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.dropdown-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
