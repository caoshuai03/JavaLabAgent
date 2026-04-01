<template>
  <div class="skills-management">
    <div class="page-header">
      <h1>Skills 管理</h1>
      <p class="subtitle">查看和管理可用的专业技能</p>
    </div>

    <div class="toolbar">
      <input
        v-model="searchKeyword"
        type="text"
        placeholder="🔍 搜索 Skill..."
        class="search-input"
      />
      <div class="toolbar-actions">
        <button
          class="btn-primary"
          @click="handleRefresh"
          :disabled="refreshing"
        >
          {{ refreshing ? '刷新中...' : '🔄 刷新' }}
        </button>
      </div>
    </div>

    <div class="skills-content" :class="{ 'is-loading': loading }">
      <div v-if="!loading && filteredSkills.length === 0" class="empty-state">
        <p>暂无 Skills</p>
        <button class="btn-primary" @click="handleRefresh">刷新列表</button>
      </div>

      <div v-else class="skills-grid">
        <div
          v-for="skill in filteredSkills"
          :key="skill.name"
          class="skill-card"
          @click="showSkillDetail(skill)"
        >
          <div class="skill-header">
            <h3>{{ skill.name }}</h3>
            <span class="tag" v-if="skill.version">v{{ skill.version }}</span>
          </div>
          <p class="skill-description">{{ skill.description }}</p>
          <div class="skill-meta">
            <div class="skill-keywords">
              <span
                v-for="keyword in skill.triggerKeywords?.slice(0, 3) || []"
                :key="keyword"
                class="tag tag-keyword"
              >
                {{ keyword }}
              </span>
              <span v-if="skill.triggerKeywords?.length > 3" class="more-keywords">
                +{{ skill.triggerKeywords.length - 3 }}
              </span>
            </div>
            <div class="skill-info">
              <span v-if="skill.author">
                👤 {{ skill.author }}
              </span>
              <span v-if="skill.hasResources" class="resources-badge">
                📁 {{ skill.resourceCount }} 资源
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="stats-footer">
      <span>共 {{ skills.length }} 个 Skills</span>
      <span v-if="lastScanTime">最后扫描: {{ formatTime(lastScanTime) }}</span>
    </div>

    <!-- Skill 详情对话框 -->
    <div v-if="detailDialogVisible" class="dialog-overlay" @click="detailDialogVisible = false">
      <div class="dialog-content" @click.stop>
        <div class="dialog-header">
          <h2>{{ currentSkill?.name }}</h2>
          <button class="close-btn" @click="detailDialogVisible = false">✕</button>
        </div>
        <div v-if="currentSkill" class="skill-detail">
          <div class="detail-header">
            <p class="description">{{ currentSkill.description }}</p>
            <div class="meta-row">
              <span class="tag" v-if="currentSkill.version">v{{ currentSkill.version }}</span>
              <span v-if="currentSkill.author">作者: {{ currentSkill.author }}</span>
              <span v-if="currentSkill.license">许可证: {{ currentSkill.license }}</span>
            </div>
          </div>

          <div v-if="currentSkill.triggerKeywords?.length" class="keywords-section">
            <h4>触发关键词</h4>
            <span
              v-for="keyword in currentSkill.triggerKeywords"
              :key="keyword"
              class="tag tag-success"
            >
              {{ keyword }}
            </span>
          </div>

          <div v-if="skillDetail" class="content-section">
            <h4>Skill 内容</h4>
            <div class="markdown-content" v-html="renderedContent"></div>
          </div>

          <div v-if="skillDetail?.resources?.length" class="resources-section">
            <h4>相关资源</h4>
            <ul>
              <li v-for="resource in skillDetail.resources" :key="resource">
                {{ resource }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSkills, getSkillDetail, refreshSkills } from '../api/skills'

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
const refreshing = ref(false)
const searchKeyword = ref('')
const lastScanTime = ref(null)
const detailDialogVisible = ref(false)
const currentSkill = ref(null)
const skillDetail = ref(null)

const filteredSkills = computed(() => {
  if (!searchKeyword.value) return skills.value
  const keyword = searchKeyword.value.toLowerCase()
  return skills.value.filter(skill => 
    skill.name.toLowerCase().includes(keyword) ||
    skill.description.toLowerCase().includes(keyword) ||
    skill.triggerKeywords?.some(k => k.toLowerCase().includes(keyword))
  )
})

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
    lastScanTime.value = response.data.lastScanTime
  } catch (error) {
    console.error('加载 Skills 失败:', error)
    showMessage('加载 Skills 失败', 'error')
  } finally {
    loading.value = false
  }
}

const handleRefresh = async () => {
  refreshing.value = true
  try {
    await refreshSkills()
    await loadSkills()
    showMessage('刷新成功', 'success')
  } catch (error) {
    console.error('刷新失败:', error)
    showMessage('刷新失败', 'error')
  } finally {
    refreshing.value = false
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

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString('zh-CN')
}

onMounted(() => {
  loadSkills()
})
</script>

<style scoped>
.skills-management {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.subtitle {
  color: #666;
  font-size: 14px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
}

.skills-content {
  min-height: 400px;
  position: relative;
}

.skills-content.is-loading::after {
  content: '加载中...';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 18px;
  color: #999;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #999;
}

.empty-state p {
  margin-bottom: 20px;
  font-size: 16px;
}

.search-input {
  width: 300px;
  padding: 10px 15px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.search-input:focus {
  border-color: #90138B;
}

.btn-primary {
  padding: 10px 20px;
  background: #90138B;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.3s;
}

.btn-primary:hover {
  background: #7a1077;
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.tag {
  display: inline-block;
  padding: 4px 12px;
  background: #f4f4f5;
  color: #606266;
  border-radius: 4px;
  font-size: 12px;
  margin-right: 8px;
  margin-bottom: 8px;
}

.tag-keyword {
  background: #ecf5ff;
  color: #409eff;
}

.tag-success {
  background: #f0f9ff;
  color: #67c23a;
}

.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-content {
  background: white;
  border-radius: 8px;
  width: 60%;
  max-width: 900px;
  max-height: 85vh;
  overflow-y: auto;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #eee;
}

.dialog-header h2 {
  margin: 0;
  font-size: 20px;
  color: #90138B;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #999;
  padding: 0;
  width: 30px;
  height: 30px;
  line-height: 30px;
  text-align: center;
  border-radius: 4px;
  transition: background 0.3s;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #333;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
}

.skill-card {
  cursor: pointer;
  transition: all 0.3s;
}

.skill-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(144, 19, 139, 0.15) !important;
}

.skill-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.skill-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #90138B;
  margin: 0;
}

.skill-description {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 16px;
  min-height: 40px;
}

.skill-meta {
  border-top: 1px solid #eee;
  padding-top: 12px;
}

.skill-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  align-items: center;
}

.more-keywords {
  font-size: 12px;
  color: #999;
}

.skill-info {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #999;
}

.skill-info span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.resources-badge {
  color: #90138B;
}

.stats-footer {
  margin-top: 24px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #666;
}

.skill-detail {
  max-height: 70vh;
  overflow-y: auto;
}

.detail-header {
  margin-bottom: 20px;
}

.detail-header .description {
  font-size: 16px;
  color: #666;
  margin-bottom: 12px;
}

.meta-row {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 14px;
  color: #999;
}

.keywords-section,
.content-section,
.resources-section {
  margin-top: 24px;
}

.keywords-section h4,
.content-section h4,
.resources-section h4 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333;
}

.keywords-section .el-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.markdown-content {
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
  line-height: 1.8;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  color: #333;
  margin-top: 16px;
  margin-bottom: 8px;
}

.markdown-content :deep(code) {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
}

.markdown-content :deep(pre) {
  background: #2d2d2d;
  color: #f8f8f2;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.resources-section ul {
  list-style: none;
  padding: 0;
}

.resources-section li {
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 8px;
  font-family: monospace;
  font-size: 13px;
}
</style>
