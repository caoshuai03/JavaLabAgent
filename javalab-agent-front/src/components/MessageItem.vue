<template>
  <div :class="['message-item', `message-${message.sender}`]">
    <div class="message-container">
      <div class="message-content">
        <div v-if="message.sender === 'assistant' && toolCalls.length > 0" class="tool-calls-panel">
          <div class="tool-calls-list">
            <div v-for="(item, idx) in toolCalls" :key="`tool-${idx}`" class="tool-call-item">
              <div v-if="idx !== toolCalls.length - 1" class="tool-call-line"></div>

              <div class="tool-call-content">
                <div class="tool-icon-wrapper">
                  <span
                    class="tool-icon"
                    v-html="
                      getToolIconSvg(
                        item.type === 'skill'
                          ? 'skill:' + item.call.payload.skillName
                          : item.call.payload.toolName,
                      )
                    "
                  ></span>
                </div>

                <div class="tool-text">
                  <span class="tool-name">
                    <template v-if="item.type === 'skill'">
                      调用 Skill：{{ item.call.payload.skillName }}
                    </template>
                    <template
                      v-else-if="
                        item.type === 'tool' &&
                        item.call.payload.toolName &&
                        item.call.payload.toolName.includes('mcp:')
                      "
                    >
                      调用 MCP：{{ getToolDisplayName(item.call.payload.toolName) }}
                    </template>
                    <template v-else>
                      调用 Tool：{{ getToolDisplayName(item.call.payload.toolName) }}
                    </template>
                  </span>
                  <span v-if="getToolSummary(item)" class="tool-divider">|</span>
                  <span
                    v-if="getToolSummary(item)"
                    class="tool-summary"
                    v-tooltip="getToolSummary(item)"
                  >
                    {{ truncateText(getToolSummary(item)) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div
          ref="messageTextRef"
          class="message-text"
          v-html="formatContent(message.content)"
          @click="handleCodeBlockClick"
        ></div>

        <div class="message-footer">
          <div v-if="showMessageActions" class="message-actions">
            <button
              @click="toggleFeedback('up')"
              :class="['action-button', { active: feedbackState === 'up' }]"
              v-tooltip="feedbackState === 'up' ? '取消点赞' : '点赞'"
            >
              <svg
                class="thumb-icon"
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M7.75 10.25h2.1v8.5h-2.1a1.15 1.15 0 0 1-1.15-1.15V11.4c0-.64.51-1.15 1.15-1.15Z"></path>
                <path d="M9.85 10.6 12.1 4.75c.22-.58.88-.88 1.45-.63.89.38 1.4 1.38 1.22 2.33l-.55 2.8h3.54c1.16 0 1.97 1.11 1.63 2.21l-1.41 4.55a1.8 1.8 0 0 1-1.71 1.24H9.85"></path>
              </svg>
            </button>

            <button
              @click="toggleFeedback('down')"
              :class="['action-button', { active: feedbackState === 'down' }]"
              v-tooltip="feedbackState === 'down' ? '取消点踩' : '点踩并反馈'"
            >
              <svg
                class="thumb-icon"
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M16.25 13.75h-2.1v-8.5h2.1c.64 0 1.15.51 1.15 1.15v6.2c0 .64-.51 1.15-1.15 1.15Z"></path>
                <path d="M14.15 13.4 11.9 19.25c-.22.58-.88.88-1.45.63-.89-.38-1.4-1.38-1.22-2.33l.55-2.8H6.24c-1.16 0-1.97-1.11-1.63-2.21l1.41-4.55a1.8 1.8 0 0 1 1.71-1.24h6.42"></path>
              </svg>
            </button>

            <button
              @click="handleCopy"
              :class="['action-button', { copied: copied }]"
              v-tooltip="copied ? '已复制' : '复制'"
            >
              <svg
                v-if="copied"
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <svg
                v-else
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </button>
          </div>

          <FeedbackModal
            v-if="showFeedbackModal"
            :message-content="message.content"
            :session-id="chatStore.currentConversationId"
            :initial-type="3"
            @close="closeFeedbackModal"
            @success="handleFeedbackSuccess"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useChatStore } from '../stores/chat'
import { renderMarkdown } from '../utils/markdown'
import FeedbackModal from './FeedbackModal.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true,
  },
})

const chatStore = useChatStore()
const messageTextRef = ref(null)
const copied = ref(false)
const showFeedbackModal = ref(false)

const showMessageActions = computed(() => {
  return props.message.sender === 'assistant' && props.message.isComplete
})

const feedbackState = computed(() => props.message.feedbackState || null)

const truncateText = (text, maxLen = 20) => {
  if (!text) return ''
  if (text.length <= maxLen) return text
  return text.substring(0, maxLen) + '...'
}

const formatContent = (content) => {
  if (!content) return ''

  if (props.message.sender === 'assistant') {
    return renderMarkdown(content)
  }

  return content.replace(/\n/g, '<br>').replace(/ {2}/g, '&nbsp;&nbsp;')
}

const toolCalls = computed(() => {
  if (!props.message.toolEvents || !props.message.toolEvents.length) return []

  const list = []
  const activeCalls = new Map()

  props.message.toolEvents.forEach((event) => {
    if (event.eventType === 'skill_loaded') {
      const skills = event.payload?.skills || []
      skills.forEach((skill) => {
        list.push({
          type: 'skill',
          call: {
            eventType: 'skill_loaded',
            payload: {
              skillName: skill.name,
              description: skill.description,
              triggerKeywords: skill.triggerKeywords,
            },
            ts: event.ts,
          },
          result: { success: true },
        })
      })
      return
    }

    if (event.eventType === 'tool_call') {
      const round = event.payload?.round
      const callItem = {
        type: 'tool',
        call: event,
        result: null,
        round,
      }
      activeCalls.set(round, callItem)
      list.push(callItem)
      return
    }

    if (event.eventType === 'tool_result') {
      const round = event.payload?.round
      const callItem = activeCalls.get(round)
      if (callItem) {
        callItem.result = event
        activeCalls.delete(round)
      }
    }
  })

  return list
})

const getToolIconSvg = (toolName) => {
  if (toolName && toolName.startsWith('skill:')) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>`
  }
  if (toolName === 'knowledge_search' || toolName === 'web_search') {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>`
  }
  if (toolName === 'read_file' || toolName === 'write_file') {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>`
  }
  if (toolName === 'run_command') {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>`
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"></path></svg>`
}

const getToolDisplayName = (toolName) => {
  if (toolName && toolName.startsWith('skill:')) {
    return toolName.substring(6)
  }

  const builtinNames = {
    knowledge_search: '知识检索',
    web_search: '搜索网页',
    read_file: '读取',
    write_file: '写入',
    run_command: '运行命令',
    session_recall: '会话回顾',
    current_time: '获取时间',
    calculator: '计算器',
  }

  return builtinNames[toolName] || toolName || '调用工具'
}

const getToolSummary = (item) => {
  try {
    if (item.type === 'skill') {
      return item.call.payload.description || ''
    }

    if (item.call.payload.description) {
      return item.call.payload.description
    }

    const builtinDescriptions = {
      knowledge_search: '查询知识库',
      web_search: '搜索网页',
      read_file: '读取文件内容',
      write_file: '写入文件内容',
      run_command: '执行终端命令',
      session_recall: '回顾当前会话消息',
      current_time: '获取当前时间',
      calculator: '计算数学表达式',
    }

    return builtinDescriptions[item.call.payload.toolName] || ''
  } catch {
    return ''
  }
}

const openFeedbackModal = () => {
  showFeedbackModal.value = true
}

const closeFeedbackModal = () => {
  showFeedbackModal.value = false
}

const handleFeedbackSuccess = () => {
  chatStore.setMessageFeedbackState(props.message.id, 'down')
}

const toggleFeedback = (type) => {
  const currentState = feedbackState.value

  if (currentState === type) {
    chatStore.setMessageFeedbackState(props.message.id, null)
    closeFeedbackModal()
    return
  }

  chatStore.setMessageFeedbackState(props.message.id, type)

  if (type === 'down') {
    openFeedbackModal()
  } else {
    closeFeedbackModal()
  }
}

const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.message.content)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (error) {
    console.error('复制失败:', error)
    const textArea = document.createElement('textarea')
    textArea.value = props.message.content
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  }
}

const handleCodeBlockClick = async (event) => {
  const copyButton = event.target.closest('.code-block-copy')
  if (!copyButton) return

  const codeBlock = copyButton.closest('.code-block-wrapper')
  const codeElement = codeBlock?.querySelector('code')
  if (!codeElement) return

  const codeText = codeElement.textContent || codeElement.innerText
  try {
    await navigator.clipboard.writeText(codeText)
    copyButton.classList.add('copied')
    copyButton.setAttribute('data-tooltip', '已复制')

    if (copyButton._tooltipEl) {
      copyButton._tooltipEl.textContent = '已复制'
    }

    setTimeout(() => {
      copyButton.classList.remove('copied')
      copyButton.setAttribute('data-tooltip', '复制代码')
      if (copyButton._tooltipEl) {
        copyButton._tooltipEl.textContent = '复制代码'
      }
    }, 2000)
  } catch (error) {
    console.error('复制代码失败:', error)
  }
}

const addCopyButtons = () => {
  if (!messageTextRef.value) return

  const codeBlocks = messageTextRef.value.querySelectorAll('.code-block-wrapper')
  codeBlocks.forEach((block) => {
    if (block.querySelector('.code-block-copy')) return

    const copyButton = document.createElement('button')
    copyButton.className = 'code-block-copy'
    copyButton.innerHTML = `
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
      </svg>
    `
    copyButton.setAttribute('data-tooltip', '复制代码')

    copyButton.addEventListener('mouseenter', () => {
      const tooltipText = copyButton.getAttribute('data-tooltip')
      if (!tooltipText) return

      const tooltipTimer = setTimeout(() => {
        const tooltipEl = document.createElement('div')
        tooltipEl.className = 'global-tooltip'
        tooltipEl.textContent = tooltipText
        document.body.appendChild(tooltipEl)

        const rect = copyButton.getBoundingClientRect()
        const padding = 12
        const estimatedWidth = tooltipText.length * 13 + 28
        const estimatedHalfWidth = estimatedWidth / 2

        let x = rect.left + rect.width / 2
        x = Math.max(padding + estimatedHalfWidth, x)
        x = Math.min(window.innerWidth - padding - estimatedHalfWidth, x)

        tooltipEl.style.top = `${rect.top - 8}px`
        tooltipEl.style.left = `${x}px`

        requestAnimationFrame(() => {
          const tooltipRect = tooltipEl.getBoundingClientRect()
          if (tooltipRect.top < padding) {
            tooltipEl.style.top = `${rect.bottom + 8}px`
          }
        })

        copyButton._tooltipEl = tooltipEl
      }, 400)

      copyButton._tooltipTimer = tooltipTimer
    })

    copyButton.addEventListener('mouseleave', () => {
      if (copyButton._tooltipTimer) clearTimeout(copyButton._tooltipTimer)
      if (copyButton._tooltipEl) {
        copyButton._tooltipEl.remove()
        copyButton._tooltipEl = null
      }
    })

    const header = block.querySelector('.code-block-header')
    if (header) {
      header.appendChild(copyButton)
    }
  })
}

onMounted(() => {
  nextTick(() => {
    addCopyButtons()
  })
})

watch(
  () => props.message.content,
  () => {
    nextTick(() => {
      addCopyButtons()
    })
  },
  { flush: 'post' },
)
</script>

<style lang="scss" scoped>
.message-item {
  padding: 24px 0;

  &.message-user,
  &.message-assistant {
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;
  }

  &.message-user {
    .message-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      justify-content: flex-end;
      gap: 12px;
    }

    .message-content {
      align-items: flex-end;
    }

    .message-text {
      background-color: var(--user-message-bg);
      color: var(--user-message-text);
      white-space: pre-wrap;
    }
  }

  &.message-assistant {
    .message-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 0 24px;
      display: flex;
      gap: 12px;
    }

    .message-content {
      align-items: flex-start;
    }

    .message-text {
      background-color: var(--assistant-message-bg);
      color: var(--assistant-message-text);
    }
  }

  @media (max-width: 768px) {
    padding: 16px 0;

    &.message-user .message-container,
    &.message-assistant .message-container {
      padding: 0 16px;
    }
  }
}

.message-container {
  display: flex;
}

.message-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.message-text {
  padding: 10px 16px 8px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 15px;
  word-wrap: break-word;

  @media (max-width: 768px) {
    padding: 8px 12px 6px;
    font-size: 14px;
    border-radius: 10px;
  }

  :deep(> *:last-child) {
    margin-bottom: 0 !important;
  }

  :deep(p) {
    margin: 0.25em 0;
  }

  :deep(ul),
  :deep(ol) {
    margin: 0.5em 0;
    padding-left: 1.5em;
  }

  :deep(li) {
    margin: 0.25em 0;
  }

  :deep(blockquote) {
    margin: 1em 0;
    padding: 0.5em 1em;
    border-left: 4px solid #90138b;
    border-radius: 4px;
    background-color: rgba(144, 19, 139, 0.05);
    color: var(--text-secondary);
    font-style: italic;
  }

  :deep(code:not(pre code)) {
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Monaco, 'Courier New', monospace;
    font-size: 0.9em;
    background-color: rgba(175, 184, 193, 0.2);
    color: #d73a49;
  }

  :deep(.code-block-wrapper) {
    position: relative;
    margin: 1em 0;
    overflow: hidden;
    border: 1px solid rgba(0, 0, 0, 0.1);
    border-radius: 8px;
    background-color: #f6f8fa;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);

    .code-block-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 16px;
      background-color: rgba(0, 0, 0, 0.03);
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
    }

    .code-block-lang {
      font-size: 12px;
      color: #656d76;
      font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Monaco, monospace;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-weight: 500;
    }

    .code-block-copy {
      padding: 6px;
      background-color: transparent;
      border: none;
      border-radius: 6px;
      color: var(--text-tertiary);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;

      &:hover {
        background-color: var(--bg-hover, rgba(0, 0, 0, 0.05));
        color: var(--text-primary);
      }

      &.copied {
        color: #90138b;
      }
    }

    pre {
      margin: 0;
      padding: 16px;
      overflow-x: auto;
      background-color: transparent;
    }

    code {
      display: block;
      padding: 0;
      background-color: transparent;
      color: #24292f;
      font-size: 0.875em;
      line-height: 1.6;
      font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Monaco, 'Courier New', monospace;
    }
  }

  :deep(pre:not(.code-block-wrapper pre)) {
    margin: 1em 0;
    padding: 16px;
    overflow-x: auto;
    border: 1px solid rgba(0, 0, 0, 0.1);
    border-radius: 8px;
    background-color: #f6f8fa;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  }

  :deep(table) {
    width: 100%;
    margin: 1em 0;
    border-collapse: collapse;
    overflow: hidden;
    border: 1px solid var(--border-color);
    border-radius: 8px;

    th,
    td {
      padding: 12px 16px;
      border: 1px solid var(--border-color);
      text-align: left;
    }

    th {
      background-color: var(--bg-hover);
      color: var(--text-primary);
      font-weight: 600;
    }

    tr:nth-child(even) {
      background-color: var(--bg-secondary);
    }
  }

  :deep(a) {
    color: #90138b;
    text-decoration: none;
    border-bottom: 1px solid transparent;
    transition: border-color 0.2s ease;

    &:hover {
      border-bottom-color: #90138b;
    }
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 0.8em 0 0.4em;
    font-weight: 600;
  }

  :deep(hr) {
    margin: 1em 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }
}

.tool-calls-panel {
  max-width: 600px;
  margin: 0 0 16px 16px;
  overflow: hidden;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 12px;
  background: transparent;

  @media (max-width: 768px) {
    max-width: 100%;
    margin: 0 0 12px;
  }
}

.tool-calls-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 12px 16px 12px 11px;
}

.tool-call-item {
  position: relative;
}

.tool-call-line {
  position: absolute;
  left: 11.5px;
  top: 24px;
  bottom: -20px;
  width: 1px;
  border-left: 1px dashed var(--border-color, #d1d5db);
  z-index: 1;
}

.tool-call-content {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
}

.tool-icon-wrapper {
  width: 24px;
  height: 24px;
  margin-right: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #6b7280);
}

.tool-text {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  white-space: nowrap;
}

.tool-name {
  flex-shrink: 0;
  font-size: 14px;
  line-height: 1.4;
  color: var(--text-primary, #374151);
  font-weight: 500;
}

.tool-divider {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--border-color, #d1d5db);
}

.tool-summary {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
  transition: color 0.2s;

  &:hover {
    color: #90138b;
  }
}

.message-footer {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding-left: 0;
}

.message-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
}

.action-button {
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #667085;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover {
    color: #7c5a93;
    background: rgba(144, 19, 139, 0.05);
  }

  &.copied,
  &.active {
    color: #90138b;
    background: rgba(144, 19, 139, 0.08);
  }
}

.thumb-icon {
  width: 21px !important;
  height: 21px !important;
  transform: scale(1.08);
  transform-origin: center;
  overflow: visible;
}
</style>
