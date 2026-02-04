<template>
  <div
    :class="['conversation-item', { active: isActive }]"
    @click="handleClick"
    @mouseenter="showActions = true"
    @mouseleave="showActions = false"
  >
    <!-- 批量选择复选框 -->
    <div v-if="isSelectionMode" class="checkbox-wrapper" @click.stop="handleToggleSelect">
      <input 
        type="checkbox" 
        :checked="isSelected" 
        class="custom-checkbox"
      />
    </div>

    <div class="content" @dblclick="handleDoubleClick">
      <div class="title">{{ conversation.title }}</div>
    </div>
    
    <div v-if="showActions && !isSelectionMode" class="actions" @click.stop>
      <button 
        @click="handleDelete" 
        class="action-button delete"
        title="删除"
      >
        <TrashIcon :size="16" />
      </button>
    </div>
    
    <input
      v-if="isRenaming"
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
import TrashIcon from './icons/TrashIcon.vue'

const props = defineProps({
  conversation: {
    type: Object,
    required: true
  },
  isActive: {
    type: Boolean,
    default: false
  },
  isSelectionMode: {
    type: Boolean,
    default: false
  },
  isSelected: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'delete', 'rename', 'toggleSelect'])

const chatStore = useChatStore()
const showActions = ref(false)
const isRenaming = ref(false)
const editTitle = ref('')
const editInputRef = ref(null)

const handleClick = () => {
  if (props.isSelectionMode) {
    emit('toggleSelect', props.conversation.id)
  } else if (!isRenaming.value) {
    emit('select', props.conversation.id)
  }
}

const handleToggleSelect = () => {
  emit('toggleSelect', props.conversation.id)
}

const handleDoubleClick = () => {
  if (props.isSelectionMode) return
  isRenaming.value = true
  editTitle.value = props.conversation.title
  nextTick(() => {
    editInputRef.value?.focus()
    editInputRef.value?.select()
  })
}

const handleRename = () => {
  isRenaming.value = true
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
  isRenaming.value = false
}

const handleCancel = () => {
  isRenaming.value = false
  editTitle.value = ''
}

const handleDelete = () => {
  emit('delete', props.conversation.id)
}
</script>

<style lang="scss" scoped>
.conversation-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 6px;
  margin-bottom: 2px;
  position: relative;
  transition: background-color 0.2s;
  height: 44px;
  
  &:hover {
    background-color: var(--bg-hover);
    
    .actions {
      opacity: 1;
    }
  }
  
  &.active {
    background-color: var(--bg-active);
    
    .title {
      font-weight: 500;
    }
  }

  .checkbox-wrapper {
    display: flex;
    align-items: center;
    margin-right: 8px;
    
    .custom-checkbox {
      width: 16px;
      height: 16px;
      cursor: pointer;
    }
  }
  
  .content {
    flex: 1;
    min-width: 0;
    margin-right: 24px; // 为操作按钮留出空间
    
    .title {
      font-size: 14px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: var(--text-primary);
    }
  }
  
  .actions {
    position: absolute;
    right: 8px;
    display: flex;
    align-items: center;
    opacity: 0; // 默认隐藏，hover时显示
    transition: opacity 0.2s;
    background: linear-gradient(to right, transparent, var(--bg-hover) 20%);
    padding-left: 10px;
    
    .action-button {
      background: none;
      border: none;
      cursor: pointer;
      color: var(--text-secondary);
      padding: 4px;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      
      &:hover {
        background-color: rgba(0, 0, 0, 0.05);
        color: var(--danger-color);
      }
    }
  }
  
  .edit-input {
    position: absolute;
    left: 4px;
    right: 4px;
    top: 4px;
    bottom: 4px;
    padding: 0 8px;
    border: 1px solid var(--primary-color);
    border-radius: 4px;
    outline: none;
    font-size: 14px;
    background-color: var(--bg-primary);
    color: var(--text-primary);
    z-index: 2;
  }
}
</style>
