<template>
  <div class="modal-overlay">
    <div class="modal-container">
      <div class="modal-header">
        <h3>提交反馈</h3>
        <button class="close-btn" @click="handleClose">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <div class="modal-body">
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

        <div class="form-group">
          <label>标题 <span class="optional">(可选)</span></label>
          <input
            v-model="form.title"
            type="text"
            class="form-input"
            placeholder="简要说明问题"
            maxlength="100"
          />
        </div>

        <div class="form-group">
          <label>联系邮箱 <span class="optional">(可选)</span></label>
          <input
            v-model="form.contactEmail"
            type="email"
            class="form-input"
            placeholder="留下邮箱，方便我们回复"
            maxlength="100"
          />
        </div>

        <div class="form-group">
          <label>反馈内容 <span class="required">*</span></label>
          <textarea
            v-model="form.content"
            class="form-textarea"
            placeholder="请描述你不满意的地方或希望改进的内容..."
            rows="5"
            maxlength="2000"
          ></textarea>
          <div class="char-count">{{ form.content.length }}/2000</div>
        </div>

        <div v-if="messageContent" class="related-message">
          <label>关联的 AI 回复</label>
          <div class="message-preview">{{ messageContent }}</div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-cancel" @click="handleClose">取消</button>
        <button class="btn btn-submit" :disabled="!canSubmit || submitting" @click="handleSubmit">
          {{ submitting ? '提交中...' : '提交反馈' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { feedbackApi } from '../api/feedback'

const props = defineProps({
  messageContent: {
    type: String,
    default: '',
  },
  sessionId: {
    type: String,
    default: null,
  },
  initialType: {
    type: Number,
    default: 2,
  },
})

const emit = defineEmits(['close', 'success'])

const feedbackTypes = [
  { value: 1, label: 'BUG' },
  { value: 2, label: '建议' },
  { value: 3, label: '投诉' },
  { value: 0, label: '其他' },
]

const form = ref({
  type: props.initialType,
  title: '',
  content: '',
  contactEmail: '',
})

const submitting = ref(false)

const canSubmit = computed(() => form.value.content.trim().length > 0)

const handleClose = () => {
  emit('close')
}

const handleSubmit = async () => {
  if (!canSubmit.value || submitting.value) return

  submitting.value = true
  try {
    const data = {
      type: form.value.type,
      title: form.value.title.trim() || null,
      content: form.value.content.trim(),
      contactEmail: form.value.contactEmail.trim() || null,
      priority: 1,
      sessionId: props.sessionId || null,
    }

    const response = await feedbackApi.submit(data)

    if (response.data && response.data.code === 0) {
      emit('success')
      emit('close')
      alert('反馈提交成功，感谢你的反馈。')
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
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.5);
}

.modal-container {
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  border-radius: 12px;
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
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
  }
}

.close-btn {
  padding: 4px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;

  &:hover {
    background-color: var(--bg-hover);
    color: var(--text-primary);
  }
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.form-group {
  margin-bottom: 20px;

  label {
    display: block;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 500;
    color: var(--text-primary);
  }
}

.required {
  color: #dc3545;
}

.optional {
  color: var(--text-secondary);
  font-weight: 400;
}

.type-selector {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.type-btn {
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: #90138b;
    color: #90138b;
    background-color: var(--bg-hover);
  }

  &.active {
    background-color: #90138b;
    border-color: #90138b;
    color: #fff;
  }
}

.form-input,
.form-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: #90138b;
    box-shadow: 0 0 0 2px rgba(144, 19, 139, 0.1);
  }

  &::placeholder {
    color: var(--text-secondary);
    opacity: 0.6;
  }
}

.form-input {
  padding: 8px 12px;
}

.form-textarea {
  min-height: 100px;
  padding: 10px 12px;
  resize: vertical;
  font-family: inherit;
}

.char-count {
  margin-top: 4px;
  text-align: right;
  font-size: 12px;
  color: var(--text-secondary);
}

.related-message {
  padding: 12px;
  border-radius: 6px;
  background-color: var(--bg-secondary);

  label {
    margin-bottom: 6px;
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.message-preview {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color);
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);

  &:hover {
    background-color: var(--bg-hover);
    color: var(--text-primary);
  }
}

.btn-submit {
  background-color: #90138b;
  color: #fff;

  &:hover:not(:disabled) {
    background-color: #a01ba0;
    box-shadow: 0 2px 8px rgba(144, 19, 139, 0.2);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}
</style>
