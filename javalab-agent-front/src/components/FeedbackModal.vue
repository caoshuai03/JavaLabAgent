<template>
  <!-- 反馈弹窗 -->
  <div class="modal-overlay">
    <div class="modal-container">
      <!-- 弹窗头部 -->
      <div class="modal-header">
        <h3>提交反馈</h3>
        <button class="close-btn" @click="handleClose">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <!-- 弹窗内容 -->
      <div class="modal-body">
        <!-- 反馈类型选择 -->
        <div class="form-group">
          <label>反馈类型</label>
          <div class="type-selector">
            <button
              v-for="item in feedbackTypes"
              :key="item.value"
              :class="['type-btn', { active: form.type === item.value }]"
              @click="form.type = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <!-- 反馈标题（可选） -->
        <div class="form-group">
          <label>标题 <span class="optional">(可选)</span></label>
          <input
            v-model="form.title"
            type="text"
            class="form-input"
            placeholder="请输入反馈标题"
            maxlength="100"
          />
        </div>

        <!-- 联系邮箱（可选） -->
        <div class="form-group">
          <label>联系邮箱 <span class="optional">(可选)</span></label>
          <input
            v-model="form.contactEmail"
            type="email"
            class="form-input"
            placeholder="请输入您的邮箱，方便我们回复您"
            maxlength="100"
          />
        </div>

        <!-- 反馈内容 -->
        <div class="form-group">
          <label>反馈内容 <span class="required">*</span></label>
          <textarea
            v-model="form.content"
            class="form-textarea"
            placeholder="请详细描述您的问题或建议..."
            rows="5"
            maxlength="2000"
          ></textarea>
          <div class="char-count">{{ form.content.length }}/2000</div>
        </div>

        <!-- 关联消息提示（如果有） -->
        <div v-if="messageContent" class="related-message">
          <label>关联的AI回复</label>
          <div class="message-preview">{{ messageContent }}</div>
        </div>
      </div>

      <!-- 弹窗底部 -->
      <div class="modal-footer">
        <button class="btn btn-cancel" @click="handleClose">取消</button>
        <button
          class="btn btn-submit"
          :disabled="!canSubmit || submitting"
          @click="handleSubmit"
        >
          {{ submitting ? '提交中...' : '提交反馈' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { feedbackApi } from '../api/feedback'

// Props
const props = defineProps({
  // 关联的消息内容（用于对话反馈场景）
  messageContent: {
    type: String,
    default: ''
  },
  // 关联的会话ID
  sessionId: {
    type: String,
    default: null
  }
})

// Emits
const emit = defineEmits(['close', 'success'])

// 反馈类型选项
const feedbackTypes = [
  { value: 1, label: 'BUG' },
  { value: 2, label: '建议' },
  { value: 3, label: '投诉' },
  { value: 0, label: '其它' }
]

// 表单数据
const form = ref({
  type: 2, // 默认选择"建议"
  title: '',
  content: '',
  contactEmail: ''
})

// 提交状态
const submitting = ref(false)

// 是否可以提交
const canSubmit = computed(() => {
  return form.value.content.trim().length > 0
})

// 关闭弹窗
const handleClose = () => {
  emit('close')
}

// 提交反馈
const handleSubmit = async () => {
  if (!canSubmit.value || submitting.value) return

  submitting.value = true
  try {
    const data = {
      type: form.value.type,
      title: form.value.title.trim() || null,
      content: form.value.content.trim(),
      contactEmail: form.value.contactEmail.trim() || null,
      priority: 1, // 默认中优先级
    }

    const response = await feedbackApi.submit(data)

    if (response.data && response.data.code === 0) {
      // 提交成功
      emit('success')
      emit('close')
      // 可以在这里添加成功提示
      alert('反馈提交成功，感谢您的反馈！')
    } else {
      alert(response.data?.message || '提交失败，请稍后重试')
    }
  } catch (error) {
    console.error('提交反馈失败:', error)
    alert('提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-container {
  background: var(--bg-primary);
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);

  h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .close-btn {
    background: transparent;
    border: none;
    padding: 4px;
    cursor: pointer;
    color: var(--text-secondary);
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s ease;

    &:hover {
      background-color: var(--bg-hover);
      color: var(--text-primary);
    }
  }
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: 20px;

  label {
    display: block;
    margin-bottom: 8px;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);

    .required {
      color: #dc3545;
    }

    .optional {
      color: var(--text-secondary);
      font-weight: 400;
    }
  }
}

.type-selector {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;

  .type-btn {
    padding: 8px 16px;
    border: 1px solid var(--border-color);
    border-radius: 20px;
    background: transparent;
    color: var(--text-secondary);
    font-size: 14px;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      border-color: #10a37f;
      color: #10a37f;
    }

    &.active {
      background-color: #10a37f;
      border-color: #10a37f;
      color: #fff;
    }
  }
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-primary);
  color: var(--text-primary);
  transition: border-color 0.2s ease;
  box-sizing: border-box;

  &:focus {
    outline: none;
    border-color: #10a37f;
  }

  &::placeholder {
    color: var(--text-secondary);
  }
}

.form-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-primary);
  color: var(--text-primary);
  resize: vertical;
  min-height: 120px;
  font-family: inherit;
  transition: border-color 0.2s ease;
  box-sizing: border-box;

  &:focus {
    outline: none;
    border-color: #10a37f;
  }

  &::placeholder {
    color: var(--text-secondary);
  }
}

.char-count {
  text-align: right;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.related-message {
  background-color: var(--bg-secondary);
  border-radius: 8px;
  padding: 12px;

  label {
    font-size: 12px;
    color: var(--text-secondary);
    margin-bottom: 8px;
  }

  .message-preview {
    font-size: 13px;
    color: var(--text-primary);
    max-height: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
  }
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color);
}

.btn {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;

  &.btn-cancel {
    background: transparent;
    color: var(--text-secondary);
    border: 1px solid var(--border-color);

    &:hover {
      background-color: var(--bg-hover);
      color: var(--text-primary);
    }
  }

  &.btn-submit {
    background-color: #10a37f;
    color: #fff;

    &:hover:not(:disabled) {
      background-color: #0d8a6a;
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }
}
</style>
