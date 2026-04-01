<template>
  <div class="skills-container">
    <Sidebar />
    <div class="skills-main">
      <div class="skills-content">
        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <span>加载中...</span>
        </div>

        <!-- 空状态 -->
        <div v-else-if="skills.length === 0" class="empty-state">
          <div class="empty-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>
          </div>
          <h3>暂无 Skills</h3>
        </div>

        <!-- Skills列表 -->
        <div v-else class="skills-list">
          <div
            v-for="skill in skills"
            :key="skill.name"
            class="skill-card"
            @click="showSkillDetail(skill)"
          >
            <div class="skill-header">
              <h3 class="skill-name">{{ skill.name }}</h3>
            </div>
            <p class="skill-description">{{ skill.description }}</p>
          </div>
        </div>

        <!-- Skill 详情对话框 -->
        <div v-if="detailDialogVisible" class="dialog-overlay" @click="detailDialogVisible = false">
          <div class="dialog-content" @click.stop>
            <div class="dialog-header">
              <h2>{{ currentSkill?.name }}</h2>
              <button class="close-btn" @click="detailDialogVisible = false">✕</button>
            </div>
            <div v-if="currentSkill" class="skill-detail">
              <div class="detail-section">
                <h4>技能简介</h4>
                <p class="description">{{ currentSkill.description }}</p>
              </div>

              <div v-if="skillDetail?.content" class="detail-section">
                <h4>详细说明</h4>
                <div class="markdown-content" v-html="renderedContent"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSkills, getSkillDetail } from '../api/skills'
import { useChatStore } from '../stores/chat'
import Sidebar from '../components/Sidebar.vue'

const chatStore = useChatStore()

// 简单的 Markdown 渲染函数（不依赖外部库）
// 这里会尽量把段落、列表和标题分开渲染，避免完整描述里出现过多空白
const renderMarkdown = (content) => {
  if (!content) return ''

  const normalized = content
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  const escapeHtml = (text) => {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
  }

  const formatInline = (text) => {
    return escapeHtml(text)
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/`(.+?)`/g, '<code>$1</code>')
  }

  const blocks = normalized.split(/\n\n+/)

  return blocks
    .map((block) => {
      const lines = block.split('\n').map((line) => line.trim()).filter(Boolean)
      if (lines.length === 0) return ''

      const firstLine = lines[0]

      if (/^#{1,3}\s+/.test(firstLine)) {
        const level = firstLine.match(/^#{1,3}/)?.[0].length || 1
        const title = firstLine.replace(/^#{1,3}\s+/, '')
        return `<h${level}>${formatInline(title)}</h${level}>`
      }

      if (lines.every((line) => /^[-*+]\s+/.test(line))) {
        const items = lines
          .map((line) => `<li>${formatInline(line.replace(/^[-*+]\s+/, ''))}</li>`)
          .join('')
        return `<ul>${items}</ul>`
      }

      if (lines.every((line) => /^\d+\.\s+/.test(line))) {
        const items = lines
          .map((line) => `<li>${formatInline(line.replace(/^\d+\.\s+/, ''))}</li>`)
          .join('')
        return `<ol>${items}</ol>`
      }

      return `<p>${formatInline(lines.join(' '))}</p>`
    })
    .filter(Boolean)
    .join('')
}

const skills = ref([])
const loading = ref(false)
const detailDialogVisible = ref(false)
const currentSkill = ref(null)
const skillDetail = ref(null)

const renderedContent = computed(() => {
  if (!skillDetail.value?.content) return ''
  return renderMarkdown(skillDetail.value.content)
})

const showMessage = (message, type = 'info') => {
  console.log(`[${type.toUpperCase()}] ${message}`)
  alert(message)
}

const loadSkills = async () => {
  loading.value = true
  try {
    const response = await getSkills()
    skills.value = response.data.skills || []
  } catch (error) {
    console.error('加载 Skills 失败:', error)
    showMessage('加载 Skills 失败', 'error')
  } finally {
    loading.value = false
  }
}

const showSkillDetail = async (skill) => {
  currentSkill.value = skill
  detailDialogVisible.value = true
  
  try {
    const response = await getSkillDetail(skill.name)
    if (response.data.success) {
      skillDetail.value = response.data
    }
  } catch (error) {
    console.error('加载 Skill 详情失败:', error)
    showMessage('加载详情失败', 'error')
  }
}

onMounted(() => {
  chatStore.initialize()
  loadSkills()
})
</script>

<style lang="scss" scoped>
.skills-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: var(--bg-primary);
  transition: background-color 0.3s ease;
}

.skills-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  min-width: 0;
}

.skills-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
  overflow: auto;
}

// ==================== 加载 & 空状态 ====================
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--text-secondary);

  .spinner {
    width: 32px;
    height: 32px;
    border: 3px solid var(--border-color);
    border-top-color: #90138B;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 0;
  color: var(--text-secondary);

  .empty-icon {
    color: var(--text-secondary);
    opacity: 0.4;
  }

  h3 {
    margin: 0;
    font-size: 16px;
    color: var(--text-primary);
  }
}

// ==================== Skills列表 ====================
.skills-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skill-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  background-color: var(--bg-secondary);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: rgba(144, 19, 139, 0.3);
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(144, 19, 139, 0.1);
  }
}

.skill-header {
  margin-bottom: 8px;
}

.skill-name {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.skill-description {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

// ==================== 弹窗样式 ====================
.dialog-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-content {
  width: 600px;
  max-width: 90vw;
  max-height: 85vh;
  background-color: var(--bg-primary);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: linear-gradient(180deg, rgba(144, 19, 139, 0.04), transparent);

  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--text-primary);
    letter-spacing: 0.2px;
  }

  .close-btn {
    width: 32px;
    height: 32px;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 20px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background-color 0.2s;

    &:hover {
      background-color: var(--bg-hover);
    }
  }
}

.skill-detail {
  padding: 20px 24px 24px;
  overflow-y: auto;
}

.detail-section {
  margin-bottom: 20px;
  padding: 6px 0 0;
  border: none;
  border-radius: 0;
  background: transparent;

  &:last-child {
    margin-bottom: 0;
  }

  h4 {
    font-size: 15px;
    font-weight: 800;
    color: var(--text-primary);
    margin: 0 0 12px 0;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    letter-spacing: 0.2px;

    &::before {
      content: '';
      width: 5px;
      height: 16px;
      border-radius: 999px;
      background: #90138B;
      flex-shrink: 0;
    }
  }

  .description {
    margin: 0;
    font-size: 14px;
    color: var(--text-secondary);
    line-height: 1.8;
    white-space: pre-wrap;
  }
}

.markdown-content {
  padding: 14px 0 0;
  background: linear-gradient(180deg, rgba(144, 19, 139, 0.02), rgba(0, 0, 0, 0.01));
  border: none;
  border-radius: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
  word-break: break-word;

  :deep(h1),
  :deep(h2),
  :deep(h3) {
    color: var(--text-primary);
    margin: 0 0 10px 0;
    font-weight: 600;
    line-height: 1.35;
  }

  :deep(h1) {
    font-size: 20px;
  }

  :deep(h2) {
    font-size: 18px;
  }

  :deep(h3) {
    font-size: 16px;
  }

  :deep(p) {
    margin: 0 0 12px 0;
    color: var(--text-secondary);
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }

  :deep(ul),
  :deep(ol) {
    margin: 0 0 12px 0;
    padding-left: 20px;
    color: var(--text-secondary);
  }

  :deep(li) {
    margin: 6px 0;
    line-height: 1.7;
  }

  :deep(strong) {
    font-weight: 600;
    color: var(--text-primary);
  }

  :deep(em) {
    font-style: italic;
  }

  :deep(code) {
    background-color: rgba(144, 19, 139, 0.08);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'SFMono-Regular', 'Consolas', monospace;
    font-size: 12px;
    color: #90138B;
  }
}

// ==================== 动画 ====================
@keyframes spin {
  to { transform: rotate(360deg); }
}

// ==================== 响应式 ====================
@media (max-width: 768px) {
  .skills-content {
    padding: 12px;
  }

  .dialog-content {
    width: 95vw;
  }

  .dialog-header,
  .skill-detail {
    padding-left: 16px;
    padding-right: 16px;
  }

  .detail-section {
    padding-top: 4px;
  }
}
</style>
