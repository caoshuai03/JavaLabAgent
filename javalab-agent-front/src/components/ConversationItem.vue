<template>
  <div
    :class="['conversation-item', { active: isActive }]"
    @click="handleClick"
    @mouseenter="showActions = true"
    @mouseleave="showActions = false"
  >
    <div class="content" @dblclick="handleDoubleClick">
      <div class="title">{{ conversation.title }}</div>
      <div class="time">{{ formatTime(conversation.updatedAt) }}</div>
    </div>
    
    <div v-if="showActions" class="actions" @click.stop>
      <button 
        @click="handleRename" 
        class="action-button rename"
        title="重命名"
      >
        <EditIcon :size="16" />
      </button>
      <button 
        @click="handleDelete" 
        class="action-button delete"
        title="删除"
      >
        <TrashIcon :size="16" />
      </button>
    </div>
    
    <input
      v-if="isEditing"
      v-model="editTitle"
      @blur="handleSave"
      @keyup.enter="handleSave"
      @keyup.esc="handleCancel"
      class="edit-input"
      @click.stop
      ref="editInputRef"
    />
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'
import EditIcon from './icons/EditIcon.vue'
import TrashIcon from './icons/TrashIcon.vue'

const props = defineProps({
  conversation: {
    type: Object,
    required: true
  },
  isActive: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'delete', 'rename'])

const chatStore = useChatStore()
const showActions = ref(false)
const isEditing = ref(false)
const editTitle = ref('')
const editInputRef = ref(null)

const handleClick = () => {
  if (!isEditing.value) {
    emit('select', props.conversation.id)
  }
}

const handleDoubleClick = () => {
  isEditing.value = true
  editTitle.value = props.conversation.title
  nextTick(() => {
    editInputRef.value?.focus()
    editInputRef.value?.select()
  })
}

const handleRename = () => {
  isEditing.value = true
  editTitle.value = props.conversation.title
  nextTick(() => {
    editInputRef.value?.focus()
    editInputRef.value?.select()
  })
}

const handleSave = () => {
  if (editTitle.value.trim()) {
    emit('rename', props.conversation.id, editTitle.value.trim())
  }
  isEditing.value = false
}

const handleCancel = () => {
  isEditing.value = false
  editTitle.value = ''
}

const handleDelete = () => {
  emit('delete', props.conversation.id)
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date
  
  // 小于1小时
  if (diff < 3600000) {
    const minutes = Math.floor(diff / 60000)
    return minutes < 1 ? '刚刚' : `${minutes}分钟前`
  }
  
  // 今天
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  
  // 昨天
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return '昨天'
  }
  
  // 一周内
  if (diff < 604800000) {
    const days = Math.floor(diff / 86400000)
    return `${days}天前`
  }
  
  // 更早
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<style lang="scss" scoped>
.conversation-item {
  position: relative;
  padding: 12px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: space-between;
  
  &:hover {
    background-color: var(--bg-hover);
  }
  
  &.active {
    background-color: var(--bg-active);
    
    .content {
      .title {
        color: var(--text-primary);
        font-weight: 500;
      }
      
      .time {
        color: var(--text-secondary);
        opacity: 0.9;
      }
    }
  }
  
  .content {
    flex: 1;
    min-width: 0;
    
    .title {
      color: var(--text-primary);
      font-size: 14px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      margin-bottom: 4px;
      transition: color 0.2s ease, font-weight 0.2s ease;
    }
    
    .time {
      color: var(--text-secondary);
      font-size: 12px;
      transition: color 0.2s ease, opacity 0.2s ease;
    }
  }
  
  .actions {
    display: flex;
    gap: 8px;
    opacity: 0.6;
    transition: all 0.2s;
    
    .conversation-item:hover & {
      opacity: 1;
    }
    
    .action-button {
      padding: 6px;
      min-width: 28px;
      min-height: 28px;
      background: transparent;
      border: none;
      color: var(--text-secondary);
      cursor: pointer;
      border-radius: 4px;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      
      svg {
        width: 16px;
        height: 16px;
      }
      
      &:hover {
        background-color: var(--border-color-hover);
        color: var(--text-primary);
        transform: scale(1.05);
      }
      
      &.rename {
        color: var(--text-primary);
        opacity: 0.8;
        
        &:hover {
          opacity: 1;
        }
      }
      
      &.delete {
        color: #dc3545;
        opacity: 0.8;
        
        &:hover {
          background-color: #dc3545;
          color: white;
          opacity: 1;
        }
      }
    }
  }
  
  .edit-input {
    position: absolute;
    left: 12px;
    right: 12px;
    top: 12px;
    bottom: 12px;
    background-color: var(--bg-hover);
    border: 1px solid var(--border-color-hover);
    border-radius: 4px;
    color: var(--text-primary);
    font-size: 14px;
    padding: 8px;
    outline: none;
    
    &:focus {
      border-color: #10a37f; // 保持品牌色不变
    }
  }
}
</style>
