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
                <h4>描述</h4>
                <p class="description">{{ currentSkill.description }}</p>
              </div>

              <div v-if="skillDetail?.content" class="detail-section">
                <h4>完整描述</h4>
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
const renderMarkdown = (content) => {
  if (!content) return ''
  return content
    .replace(/^### (.*$)/gim, '<h3>$1</h3>')
    .replace(/^## (.*$)/gim, '<h2>$1</h2>')
    .replace(/^# (.*$)/gim, '<h1>$1</h1>')
    .replace(/\*\*(.*)\*\*/gim, '<strong>$1</strong>')
    .replace(/\*(.*)\*/gim, '<em>$1</em>')
    .replace(/\n/gim, '<br>')
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
  border-bottom: 1px solid var(--border-color);

  h2 {
    margin: 0;
    font-size: 17px;
    font-weight: 600;
    color: var(--text-primary);
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
  padding: 20px 24px;
  overflow-y: auto;
}

.detail-section {
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }

  h4 {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0 0 8px 0;
  }

  .description {
    margin: 0;
    font-size: 14px;
    color: var(--text-secondary);
    line-height: 1.6;
  }
}

.markdown-content {
  padding: 16px;
  background-color: var(--bg-tertiary);
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);

  :deep(h1),
  :deep(h2),
  :deep(h3) {
    color: var(--text-primary);
    margin-top: 16px;
    margin-bottom: 8px;
    font-weight: 600;
  }

  :deep(h1) {
    font-size: 18px;
  }

  :deep(h2) {
    font-size: 16px;
  }

  :deep(h3) {
    font-size: 14px;
  }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(strong) {
    font-weight: 600;
    color: var(--text-primary);
  }

  :deep(em) {
    font-style: italic;
  }

  :deep(code) {
    background-color: var(--bg-primary);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'SFMono-Regular', 'Consolas', monospace;
    font-size: 13px;
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
}
</style>
