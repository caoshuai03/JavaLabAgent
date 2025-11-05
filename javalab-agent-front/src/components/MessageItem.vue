<template>
  <div :class="['message-item', `message-${message.sender}`]">
    <div class="message-container">
      <div class="avatar">
        <span v-if="message.sender === 'user'">👤</span>
        <span v-else>🤖</span>
      </div>
      
      <div class="message-content">
        <div class="message-text" v-html="formatContent(message.content)"></div>
        
        <div v-if="message.sender === 'assistant'" class="message-actions">
          <button 
            @click="handleCopy" 
            class="action-button"
            title="复制"
          >
            📋
          </button>
        </div>
        
        <div class="message-time">{{ formatTime(message.timestamp) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useChatStore } from '../stores/chat'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const chatStore = useChatStore()

const formatContent = (content) => {
  if (!content) return ''
  
  // 只有助手消息使用 Markdown 渲染，用户消息保持原样
  if (props.message.sender === 'assistant') {
    return renderMarkdown(content)
  }
  
  // 用户消息：简单的文本格式化，将换行转换为 <br>
  return content
    .replace(/\n/g, '<br>')
    .replace(/  /g, '&nbsp;&nbsp;')
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  
  const date = new Date(timestamp)
  const now = new Date()
  
  // 如果是今天，只显示时间
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  
  // 否则显示日期和时间
  return date.toLocaleString('zh-CN', { 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.message.content)
    // 可以添加一个提示
    alert('已复制到剪贴板')
  } catch (err) {
    console.error('复制失败:', err)
    // 降级方案
    const textArea = document.createElement('textarea')
    textArea.value = props.message.content
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
    alert('已复制到剪贴板')
  }
}
</script>

<style lang="scss" scoped>
.message-item {
  padding: 20px 0;
  
  &.message-user {
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;
    
    .message-container {
      max-width: 768px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      gap: 16px;
      justify-content: flex-end;
      
      .avatar {
        order: 2;
      }
      
      .message-content {
        order: 1;
        align-items: flex-end;
        
        .message-text {
          background-color: var(--user-message-bg);
          color: var(--user-message-text);
        }
      }
    }
  }
  
  &.message-assistant {
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;
    
    .message-container {
      max-width: 768px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      gap: 16px;
      
      .message-content {
        align-items: flex-start;
        
        .message-text {
          background-color: var(--assistant-message-bg);
          color: var(--assistant-message-text);
        }
      }
    }
  }
}

.message-container {
  display: flex;
  gap: 16px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background-color: #10a37f; // 保持品牌色不变
}

.message-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.message-text {
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.75;
  font-size: 16px;
  word-wrap: break-word;
  white-space: pre-wrap;
  
  :deep(br) {
    display: block;
    content: '';
    margin-top: 0.5em;
  }
  
  // Markdown 样式
  :deep(p) {
    margin: 0.5em 0;
    
    &:first-child {
      margin-top: 0;
    }
    
    &:last-child {
      margin-bottom: 0;
    }
  }
  
  :deep(ul), :deep(ol) {
    margin: 0.5em 0;
    padding-left: 1.5em;
  }
  
  :deep(li) {
    margin: 0.25em 0;
  }
  
  :deep(blockquote) {
    margin: 0.5em 0;
    padding-left: 1em;
    border-left: 3px solid var(--border-color-hover);
    color: var(--text-secondary);
  }
  
  :deep(code) {
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    background-color: var(--bg-hover);
  }
  
  :deep(pre) {
    margin: 0.5em 0;
    padding: 12px;
    border-radius: 8px;
    overflow-x: auto;
    background-color: var(--bg-hover);
    
    code {
      padding: 0;
      background-color: transparent;
      font-size: 0.9em;
      line-height: 1.5;
    }
  }
  
  :deep(table) {
    border-collapse: collapse;
    margin: 0.5em 0;
    width: 100%;
    
    th, td {
      padding: 8px 12px;
      border: 1px solid var(--border-color);
    }
    
    th {
      background-color: var(--bg-hover);
      font-weight: 600;
    }
  }
  
  :deep(a) {
    color: #10a37f;
    text-decoration: none;
    
    &:hover {
      text-decoration: underline;
    }
  }
  
  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    margin: 0.8em 0 0.4em 0;
    font-weight: 600;
    
    &:first-child {
      margin-top: 0;
    }
  }
  
  :deep(h1) { font-size: 1.5em; }
  :deep(h2) { font-size: 1.3em; }
  :deep(h3) { font-size: 1.1em; }
  :deep(h4) { font-size: 1em; }
  :deep(h5) { font-size: 0.9em; }
  :deep(h6) { font-size: 0.8em; }
  
  :deep(hr) {
    margin: 1em 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }
}

.message-actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
  
  .message-item:hover & {
    opacity: 1;
  }
  
  .action-button {
    padding: 4px 8px;
    background: transparent;
    border: 1px solid var(--border-color-hover);
    border-radius: 4px;
    color: var(--text-secondary);
    cursor: pointer;
    font-size: 14px;
    transition: all 0.2s;
    
    &:hover {
      background-color: var(--border-color-hover);
      color: var(--text-primary);
    }
  }
}

.message-time {
  font-size: 12px;
  color: var(--text-secondary);
  padding: 0 4px;
}
</style>

