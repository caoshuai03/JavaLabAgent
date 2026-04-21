<template>
  <div :class="['message-item', `message-${message.sender}`]">
    <div class="message-container">
      <div class="message-content">
        <div v-if="message.sender === 'assistant' && toolCalls.length > 0" class="tool-calls-panel">
          <div class="tool-calls-list">
            <div v-for="(item, idx) in toolCalls" :key="`tool-${idx}`" class="tool-call-item">
              <div v-if="idx !== toolCalls.length - 1" class="tool-call-line"></div>

              <div class="tool-call-content" :class="{ 'tool-timeout': item.call.eventType === 'tool_timeout' || item.call.eventType === 'global_timeout' }">
                <div class="tool-icon-wrapper">
                  <span
                    class="tool-icon"
                    v-html="
                      getToolIconSvg(
                        item.type === 'skill'
                          ? 'skill:' + item.call.payload.skillName
                          : item.call.payload.toolName,
                        item.call.eventType,
                      )
                    "
                  ></span>
                </div>
                <div class="tool-text">
                  <span class="tool-name">
                    <template v-if="item.call.eventType === 'global_timeout'">
                      ⚠️ 全局超时
                    </template>
                    <template v-else-if="item.call.eventType === 'tool_timeout'">
                      ⚠️ 工具超时：{{ getToolDisplayName(item.call.payload.toolName) }}
                    </template>
                    <template v-else-if="item.type === 'skill'">
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
                    :class="{ 'tool-summary-timeout': item.call.eventType === 'tool_timeout' || item.call.eventType === 'global_timeout' }"
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
        hidden: false,
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
      return
    }

    if (event.eventType === 'status' && event.payload?.stage === 'tool_done') {
      const round = event.payload?.round
      const callItem = activeCalls.get(round)
      if (callItem && event.payload?.success === false) {
        callItem.hidden = true
        activeCalls.delete(round)
      }
    }

    // 处理工具超时事件，展示为失败的工具调用
    if (event.eventType === 'status' && event.payload?.stage === 'tool_timeout') {
      const round = event.payload?.round
      const toolName = event.payload?.toolName || 'unknown'
      const timeoutSeconds = event.payload?.timeoutSeconds || 60
      const callItem = {
        type: 'tool',
        call: {
          eventType: 'tool_timeout',
          payload: {
            toolName,
            description: `工具执行超时（${timeoutSeconds}秒）`,
            timeoutSeconds,
          },
          ts: event.ts,
        },
        result: { success: false, error: 'timeout' },
        hidden: false,
        round,
      }
      // 如果该轮次已有工具调用，替换为超时状态
      const existing = activeCalls.get(round)
      if (existing) {
        existing.call = callItem.call
        existing.result = callItem.result
      } else {
        list.push(callItem)
      }
    }

    // 处理全局超时事件，展示为特殊的系统提示
    if (event.eventType === 'status' && event.payload?.stage === 'global_timeout') {
      const timeoutSeconds = event.payload?.timeoutSeconds || 180
      list.push({
        type: 'system',
        call: {
          eventType: 'global_timeout',
          payload: {
            description: `全局执行超时（${timeoutSeconds}秒），Agent 基于已有信息作答`,
            timeoutSeconds,
          },
          ts: event.ts,
        },
        result: { success: false, error: 'global_timeout' },
        hidden: false,
      })
    }
  })

  return list.filter((item) => !item.hidden)
})

const getToolIconSvg = (toolName, eventType) => {
  // 超时事件使用警告图标
  if (eventType === 'tool_timeout' || eventType === 'global_timeout') {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`
  }
  if (toolName && toolName.startsWith('skill:')) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>`
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"></path></svg>`
}

const getToolDisplayName = (toolName) => {
  if (toolName && toolName.startsWith('skill:')) {
    return toolName.substring(6)
  }

  return toolName || 'tool'
}

const getToolSummary = (item) => {
  try {
    if (item.type === 'skill') {
      return item.call.payload.description || ''
    }
    if (item.call.payload.description) {
      return item.call.payload.description
    }

    return ''
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
    background-color: var(--bg-primary);
    transition: background-color 0.3s ease;

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

    .message-container {
      padding: 0 16px !important;
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
  white-space: normal;

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

    &:first-child {
      margin-top: 0;
    }

    &:last-child {
      margin-bottom: 0;
    }
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
    background-color: rgba(144, 19, 139, 0.05);
    border-radius: 4px;
    color: var(--text-secondary);
    font-style: italic;
  }

  :deep(code:not(pre code)) {
    padding: 2px 6px;
    border-radius: 4px;
    font-family:
      'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    background-color: rgba(175, 184, 193, 0.2);
    color: #d73a49;
  }

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
        padding: 6px;
        background-color: transparent;
        border: none;
        color: var(--text-tertiary);
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.2s;
        border-radius: 6px;

        svg {
          width: 16px;
          height: 16px;
        }

        &:hover {
          color: var(--text-primary);
          background-color: var(--bg-hover, rgba(0, 0, 0, 0.05));
        }

        &.copied {
          color: #90138b;
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
        font-family:
          'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New',
          monospace;
        display: block;
      }
    }
  }

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
      font-family:
        'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
    }
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 1em 0;
    width: 100%;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    overflow: hidden;

    th,
    td {
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
    margin: 0.8em 0 0.4em 0;
    font-weight: 600;

    &:first-child {
      margin-top: 0;
    }
  }

  :deep(hr) {
    margin: 1em 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }
}

.tool-calls-panel {
  margin: 0 0 16px 16px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 12px;
  background: transparent;
  overflow: hidden;
  max-width: 600px;

  @media (max-width: 768px) {
    margin: 0 0 12px 0;
    max-width: 100%;
  }
}

.tool-calls-list {
  padding: 12px 16px 12px 11px;
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  display: flex;
  align-items: center;
  position: relative;
  z-index: 2;
  cursor: default;
}

.tool-icon-wrapper {
  width: 24px;
  height: 24px;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 8px;
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
  font-size: 14px;
  color: var(--text-primary, #374151);
  font-weight: 500;
  flex-shrink: 0;
  line-height: 1.4;
}

.tool-divider {
  color: var(--border-color, #d1d5db);
  font-size: 12px;
  flex-shrink: 0;
}

.tool-summary {
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;
  transition: color 0.2s;

  &:hover {
    color: #90138b;
  }
}

// 超时事件样式
.tool-timeout {
  .tool-icon {
    color: #f59e0b;
  }
}

.tool-summary-timeout {
  color: #f59e0b !important;
  font-weight: 500;
}

.message-footer {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding-left: 16px;

  @media (max-width: 768px) {
    padding-left: 12px;
  }
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
  color: var(--text-secondary);
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
    color: var(--text-primary);
    background-color: var(--bg-hover);
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
}
</style>
