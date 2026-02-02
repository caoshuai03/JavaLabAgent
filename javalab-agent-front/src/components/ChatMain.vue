<template>
  <div class="chat-main" :class="{ 'is-empty': chatStore.messages.length === 0 }"
       @click="handleMainClick">
    <!-- 顶部模型选择区域 -->
    <div class="top-bar">
      <button
        v-if="showMobileMenuButton"
        @click.stop="toggleSidebar"
        class="mobile-menu-button"
        title="打开菜单"
      >
        ☰
      </button>

      <!-- 大模型选择下拉框 -->
      <div class="model-selector-container" v-click-outside="closeModelDropdown">
        <div
          class="model-selector-trigger"
          :class="{ 'disabled': chatStore.isStreaming }"
          @click="toggleModelDropdown"
        >
          <span class="model-label">{{ currentModelLabel }}</span>
          <ChevronDownIcon :size="16" class="dropdown-icon"
                           :class="{ 'is-open': showModelDropdown }"/>
        </div>

        <transition name="dropdown-fade">
          <div v-show="showModelDropdown" class="model-dropdown-menu">
            <div
              v-for="model in availableModels"
              :key="model.value"
              class="model-option"
              :class="{ 'active': chatStore.selectedModel === model.value }"
              @click="selectModel(model.value)"
            >
              {{ model.label }}
            </div>
          </div>
        </transition>
      </div>
    </div>

    <MessageList v-show="chatStore.messages.length > 0"/>

    <div v-if="chatStore.messages.length === 0" class="welcome-container">
      <div class="welcome-content">
        <h2>Java实验助手</h2>
        <p>我可以为您解答Java实验相关的问题，请把您的任务交给我吧~</p>
      </div>
    </div>

    <ChatInput/>

    <div class="footer-container">
      <p>AI生成内容仅供参考，不代表本平台立场。版权所有 © shuaicao01@163.com</p>
    </div>
  </div>
</template>

<script setup>
import {ref, computed, onMounted, onUnmounted} from 'vue'
import {useChatStore} from '../stores/chat'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import ChevronDownIcon from './icons/ChevronDownIcon.vue'

const chatStore = useChatStore()
const isMobile = ref(false)
const showModelDropdown = ref(false)

// 可用模型列表
const availableModels = ref([
  {label: 'Qwen3-8B', value: 'qwen3:8b'},
  {label: 'Ernie 4.5-300B', value: 'ernie-4.5-turbo-128k-preview'},
  {label: 'DeepSeek V3', value: 'deepseek-v3'},
  {label: 'DeepSeek R1', value: 'deepseek-r1'}
])

const showMobileMenuButton = computed(() => {
  return isMobile.value && chatStore.sidebarCollapsed
})

const currentModelLabel = computed(() => {
  const model = availableModels.value.find(m => m.value === chatStore.selectedModel)
  return model ? model.label : chatStore.selectedModel
})

const toggleSidebar = () => {
  chatStore.toggleSidebar()
}

const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (isMobile.value && !chatStore.sidebarCollapsed) {
    chatStore.sidebarCollapsed = true
  }
}

const handleMainClick = () => {
  if (isMobile.value && !chatStore.sidebarCollapsed) {
    chatStore.sidebarCollapsed = true
  }
}

const toggleModelDropdown = () => {
  if (!chatStore.isStreaming) {
    showModelDropdown.value = !showModelDropdown.value
  }
}

const closeModelDropdown = () => {
  showModelDropdown.value = false
}

const selectModel = (value) => {
  chatStore.selectedModel = value
  closeModelDropdown()
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

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style lang="scss" scoped>
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--bg-primary);
  overflow: hidden;
  min-width: 0;
  transition: background-color 0.3s ease;
  position: relative;

  &.is-empty {
    justify-content: center;

    .welcome-container {
      display: flex;
      justify-content: center;
      margin-bottom: 40px;

      .welcome-content {
        text-align: center;
        color: var(--text-secondary);

        h2 {
          color: var(--text-primary);
          font-size: 24px;
          margin-bottom: 16px;
          font-weight: 600;
        }

        p {
          font-size: 16px;
          line-height: 1.5;
        }
      }
    }

    :deep(.chat-input-container) {
      border-top: none;
      background-color: transparent;
    }
  }

  .footer-container {
    text-align: center;
    color: var(--text-tertiary, #999);
    font-size: 12px;
    padding: 8px 0 12px 0;
    width: 100%;
    flex-shrink: 0; // 防止被压缩

    p {
      margin: 0;
      opacity: 0.8;
    }
  }

  @media (max-width: 768px) {
    width: 100%;
    position: relative;
  }
}

.top-bar {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  background-color: transparent;
  flex-shrink: 0;
  position: absolute;
  top: 0;
  left: 0;
  z-index: 100;
  pointer-events: none;

  .mobile-menu-button, .model-selector-container {
    pointer-events: auto;
  }

  .mobile-menu-button {
    width: 32px;
    height: 32px;
    background-color: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    color: var(--text-primary);
    font-size: 18px;
    cursor: pointer;
    display: none;
    align-items: center;
    justify-content: center;
    transition: all 0.2s;
    flex-shrink: 0;
    margin-right: 8px;

    &:hover {
      background-color: var(--bg-hover);
    }

    @media (max-width: 768px) {
      display: flex;
    }
  }

  .model-selector-container {
    position: relative;
    display: flex;
    align-items: center;
  }

  .model-selector-trigger {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    border-radius: 8px;
    cursor: pointer;
    transition: background-color 0.2s ease;

    &:hover:not(.disabled) {
      background-color: rgba(0, 0, 0, 0.05);
    }

    &.disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .model-label {
      font-size: 18px;
      font-weight: 600;
      color: var(--text-primary);
    }

    .dropdown-icon {
      color: var(--text-secondary);
      transition: transform 0.2s ease;

      &.is-open {
        transform: rotate(180deg);
      }
    }
  }

  .model-dropdown-menu {
    position: absolute;
    top: 100%;
    left: 0;
    margin-top: 4px;
    background-color: var(--bg-primary);
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    padding: 4px;
    min-width: 160px;
    z-index: 1000;

    .model-option {
      padding: 8px 12px;
      border-radius: 6px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 14px;
      color: var(--text-primary);
      transition: background-color 0.2s ease;

      &:hover {
        background-color: var(--bg-hover);
      }

      &.active {
        background-color: var(--bg-hover); // 使用 hover 背景作为选中背景
        font-weight: 500;
      }
    }
  }

  // 动画
  .dropdown-fade-enter-active,
  .dropdown-fade-leave-active {
    transition: all 0.2s ease;
  }

  .dropdown-fade-enter-from,
  .dropdown-fade-leave-to {
    opacity: 0;
    transform: translateY(-5px);
  }

  @media (max-width: 768px) {
    position: relative;
    top: 0;
    left: 0;
    padding: 12px 16px;
    pointer-events: auto;

    .model-selector-trigger .model-label {
      font-size: 16px;
    }
  }
}
</style>
