<template>
  <div :class="['message-item', `message-${message.sender}`]">
    <div class="message-container">
      <div class="message-content">
        <div class="message-text" v-html="formatContent(message.content)" ref="messageTextRef" @click="handleCodeBlockClick"></div>

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
import { ref, onMounted, nextTick, watch } from 'vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const chatStore = useChatStore()
const messageTextRef = ref(null)

const formatContent = (content) => {
  if (!content) return ''

  // 只有助手消息使用 Markdown 渲染，用户消息保持原样
  if (props.message.sender === 'assistant') {
    return renderMarkdown(content)
  }

  // 用户消息：简单的文本格式化，将换行转换为 <br>
  return content
    .replace(/\n/g, '<br>')
    .replace(/ {2}/g, '&nbsp;&nbsp;')
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

// 处理代码块点击事件（复制代码）
const handleCodeBlockClick = async (event) => {
  const copyButton = event.target.closest('.code-block-copy')
  if (copyButton) {
    const codeBlock = copyButton.closest('.code-block-wrapper')
    if (codeBlock) {
      const codeElement = codeBlock.querySelector('code')
      if (codeElement) {
        const codeText = codeElement.textContent || codeElement.innerText
        try {
          await navigator.clipboard.writeText(codeText)
          // 显示复制成功提示
          const originalText = copyButton.textContent
          copyButton.textContent = '已复制'
          copyButton.classList.add('copied')
          setTimeout(() => {
            copyButton.textContent = originalText
            copyButton.classList.remove('copied')
          }, 2000)
        } catch (err) {
          console.error('复制代码失败:', err)
        }
      }
    }
  }
}

// 为代码块添加复制按钮
const addCopyButtons = () => {
  if (!messageTextRef.value) return

  const codeBlocks = messageTextRef.value.querySelectorAll('.code-block-wrapper')
  codeBlocks.forEach((block) => {
    // 如果已经有复制按钮，跳过
    if (block.querySelector('.code-block-copy')) return

    const copyButton = document.createElement('button')
    copyButton.className = 'code-block-copy'
    copyButton.textContent = '复制'
    copyButton.title = '复制代码'

    const header = block.querySelector('.code-block-header')
    if (header) {
      header.appendChild(copyButton)
    }
  })
}

// 监听消息内容变化，添加复制按钮
onMounted(() => {
  nextTick(() => {
    addCopyButtons()
  })
})

// 监听消息内容变化（流式更新时）
watch(() => props.message.content, () => {
  nextTick(() => {
    addCopyButtons()
  })
}, { flush: 'post' })
</script>

<style lang="scss" scoped>
.message-item {
  padding: 24px 0;

  &.message-user {
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;

    .message-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      gap: 12px;
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
          white-space: pre-wrap;
        }
      }
    }

    @media (max-width: 768px) {
      padding: 16px 0;

      .message-container {
        padding: 0 16px;
      }
    }
  }

  &.message-assistant {
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;

    .message-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      gap: 12px;

      .message-content {
        align-items: flex-start;

        .message-text {
          background-color: var(--assistant-message-bg);
          color: var(--assistant-message-text);
        }
      }
    }

    @media (max-width: 768px) {
      padding: 16px 0;

      .message-container {
        padding: 0 16px;
      }
    }
  }
}

.message-container {
  display: flex;
}

.message-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.message-text {
  padding: 16px 20px;
  border-radius: 12px;
  line-height: 1.75;
  font-size: 16px;
  word-wrap: break-word;
  white-space: normal;

  @media (max-width: 768px) {
    padding: 12px 16px;
    font-size: 15px;
    border-radius: 10px;
  }

  

  // Markdown 样式
  :deep(p) {
    margin: 0.25em 0;

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
    margin: 1em 0;
    padding: 0.5em 1em;
    padding-left: 1em;
    border-left: 4px solid #10a37f;
    background-color: rgba(16, 163, 127, 0.05);
    border-radius: 4px;
    color: var(--text-secondary);
    font-style: italic;
  }

  // 行内代码样式 - ChatGPT 风格
  :deep(code:not(pre code)) {
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    background-color: rgba(175, 184, 193, 0.2);
    color: #d73a49;
  }

  // 代码块样式 - ChatGPT 风格（浅色主题）
  :deep(.code-block-wrapper) {
    margin: 1em 0;
    border-radius: 8px;
    overflow: hidden;
    background-color: #f6f8fa;
    border: 1px solid rgba(0, 0, 0, 0.1);
    position: relative;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);

    .code-block-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 16px;
      background-color: rgba(0, 0, 0, 0.03);
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);

      .code-block-lang {
        font-size: 12px;
        color: #656d76;
        font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', monospace;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        font-weight: 500;
      }

      .code-block-copy {
        padding: 4px 12px;
        font-size: 12px;
        background-color: transparent;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 6px;
        color: #656d76;
        cursor: pointer;
        transition: all 0.2s ease;
        font-family: inherit;

        &:hover {
          background-color: rgba(0, 0, 0, 0.05);
          border-color: rgba(0, 0, 0, 0.25);
          color: #24292f;
        }

        &.copied {
          background-color: #10a37f;
          border-color: #10a37f;
          color: #fff;
        }
      }
    }

    pre {
      margin: 0;
      padding: 16px;
      border-radius: 0;
      overflow-x: auto;
      background-color: transparent;

      code {
        padding: 0;
        background-color: transparent;
        font-size: 0.875em;
        line-height: 1.6;
        color: #24292f;
        font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
        display: block;
      }
    }
  }

  // 兼容旧的 pre 标签（没有包装器的情况）
  :deep(pre:not(.code-block-wrapper pre)) {
    margin: 1em 0;
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
    background-color: #f6f8fa;
    border: 1px solid rgba(0, 0, 0, 0.1);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);

    code {
      padding: 0;
      background-color: transparent;
      font-size: 0.875em;
      line-height: 1.6;
      color: #24292f;
      font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
    }
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 1em 0;
    width: 100%;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    overflow: hidden;

    th, td {
      padding: 12px 16px;
      border: 1px solid var(--border-color);
      text-align: left;
    }

    th {
      background-color: var(--bg-hover);
      font-weight: 600;
      color: var(--text-primary);
    }

    tr:nth-child(even) {
      background-color: var(--bg-secondary);
    }

    tr:hover {
      background-color: var(--bg-hover);
    }
  }

  :deep(a) {
    color: #10a37f;
    text-decoration: none;
    border-bottom: 1px solid transparent;
    transition: border-color 0.2s ease;

    &:hover {
      border-bottom-color: #10a37f;
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
  margin-top: 4px;

  .message-item:hover & {
    opacity: 1;
  }

  .action-button {
    padding: 6px 10px;
    background: transparent;
    border: 1px solid var(--border-color);
    border-radius: 6px;
    color: var(--text-secondary);
    cursor: pointer;
    font-size: 13px;
    transition: all 0.2s;

    &:hover {
      background-color: var(--bg-hover);
      border-color: var(--border-color-hover);
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
