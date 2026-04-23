<template>
  <div ref="loginContainerRef" class="login-container">
    <div class="mouse-glow-layer"></div>
    <div
      v-for="particle in trailParticles"
      :key="particle.id"
      class="mouse-trail-particle"
      :style="{
        transform: `translate(${particle.x - particle.size / 2}px, ${particle.y - particle.size / 2}px)`,
        width: `${particle.size}px`,
        height: `${particle.size}px`,
        opacity: particle.opacity,
      }"
    ></div>
    <div class="mouse-orb"></div>
    <div class="login-decoration login-decoration-left"></div>
    <div class="login-decoration login-decoration-right"></div>
    <div class="login-shell">
      <!-- 左侧品牌区 -->
      <section class="hero-panel">
        <div class="hero-brand">
          <img class="hero-logo" :src="logoImage" alt="JavaLab Agent Logo" />
        </div>
        <div class="hero-copy">
          <p class="hero-kicker">WELCOME</p>
          <h1 class="hero-title">
            <span>欢迎来到</span>
            <span>JavaLab Agent</span>
          </h1>
          <p class="hero-subtitle">
            一个好用的 Harness Agent。
          </p>
          <ul class="hero-features">
            <li>MCP 与 Function Calling 协同，让 Agent 更智能</li>
            <li>集成 Skills，扩展 Agent 能力</li>
            <li>支持多模型切换，自动匹配最优模型</li>
            <li>流式输出，实时响应，用户体验更好</li>
          </ul>
        </div>
      </section>

      <!-- 右侧登录区 -->
      <section class="auth-panel">
        <div class="login-form">
          <button
            v-if="isRegister"
            type="button"
            class="back-arrow"
            aria-label="返回登录"
            @click="backToLogin"
          >
            <span class="back-arrow-icon">‹</span>
          </button>

          <div class="auth-head">
            <h2>{{ pageTitle }}</h2>
            <p>{{ pageSubtitle }}</p>
          </div>

          <form @submit.prevent="handleSubmit">
            <div class="form-group">
              <label for="username">用户名</label>
              <input
                id="username"
                v-model="form.userName"
                type="text"
                required
                placeholder="请输入用户名"
              />
            </div>
            <div class="form-group">
              <label for="password">密码</label>
              <input
                id="password"
                v-model="form.password"
                type="password"
                required
                :placeholder="isRegister ? '请输入密码（至少6位）' : '请输入密码'"
              />
            </div>
            <div class="form-group" v-if="isRegister">
              <label for="confirmPassword">确认密码</label>
              <input
                id="confirmPassword"
                v-model="form.confirmPassword"
                type="password"
                required
                placeholder="请再次输入密码"
              />
            </div>
            <div v-if="errorMessage" class="error-message">
              {{ errorMessage }}
            </div>
            <div class="form-group">
              <button type="submit" class="primary-button" :disabled="loading">
                {{ loading ? (isRegister ? '注册中...' : '登录中...') : isRegister ? '注册' : '登录' }}
              </button>
            </div>
            <div class="switch-row">
              <span>{{ isRegister ? '已经有账号了？' : '还没有账号？' }}</span>
              <button
                type="button"
                class="toggle-button"
                @click="isRegister ? backToLogin() : goRegister()"
              >
                {{ isRegister ? '返回登录' : '去注册' }}
              </button>
            </div>
          </form>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import logoImage from '../assets/logo.png'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const loginContainerRef = ref(null)
const isRegister = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const trailParticles = ref(
  Array.from({ length: 18 }, (_, index) => ({
    id: index,
    x: 0,
    y: 0,
    driftX: 0,
    driftY: 0,
    size: Math.max(1.4, 4.6 - index * 0.18),
    opacity: 0,
    hold: 0,
    fade: 0,
    maxFade: 0,
    maxOpacity: 0,
  })),
)

let glowAnimationFrame = 0
let currentGlowX = 0
let currentGlowY = 0
let targetGlowX = 0
let targetGlowY = 0
let velocityX = 0
let velocityY = 0
let particleCursor = 0
let emitAccumulator = 0

// 根据当前模式生成标题文案
const pageTitle = computed(() => (isRegister.value ? '创建你的账号' : '欢迎回来'))
const pageSubtitle = computed(() => (isRegister.value ? '请完善信息完成注册。' : '请登录后继续。'))

const updateGlowStyles = () => {
  // 同步主光点，并让尾迹先停留再散开变暗，形成更自然的残影
  if (!loginContainerRef.value) {
    return
  }

  const container = loginContainerRef.value
  const glowOpacity = Number.parseFloat(container.style.getPropertyValue('--glow-opacity') || '0.64')

  container.style.setProperty('--mouse-x', `${currentGlowX}px`)
  container.style.setProperty('--mouse-y', `${currentGlowY}px`)
  container.style.setProperty('--trail-x', `${currentGlowX}px`)
  container.style.setProperty('--trail-y', `${currentGlowY}px`)

  trailParticles.value = trailParticles.value.map((particle) => {
    if (particle.hold > 0) {
      // 停留阶段保持位置与亮度，只缓慢减少停留时间
      const nextHold = Math.max(0, particle.hold - 1)
      return {
        ...particle,
        hold: nextHold,
        opacity: Number((particle.maxOpacity * glowOpacity).toFixed(3)),
      }
    }

    if (particle.fade > 0) {
      // 散开阶段轻微漂移，并按照缓出曲线逐渐变暗
      const nextFade = Math.max(0, particle.fade - 1)
      const fadeProgress = particle.maxFade > 0 ? nextFade / particle.maxFade : 0
      return {
        ...particle,
        x: particle.x + particle.driftX,
        y: particle.y + particle.driftY,
        driftX: particle.driftX * 0.96,
        driftY: particle.driftY * 0.96,
        fade: nextFade,
        opacity: Number((Math.pow(fadeProgress, 1.45) * particle.maxOpacity * glowOpacity).toFixed(3)),
      }
    }

    return {
      ...particle,
      opacity: 0,
    }
  })
}

const emitTrailParticle = () => {
  // 在鼠标经过位置投放残影粒子，先停留，再轻微散开并变暗
  if (!trailParticles.value.length) {
    return
  }

  const speed = Math.min(1, (Math.abs(velocityX) + Math.abs(velocityY)) / 18)
  const speedWeight = 0.6 + speed * 0.8
  const movementDistance = Math.abs(velocityX) + Math.abs(velocityY)
  emitAccumulator += movementDistance

  if (emitAccumulator < 2.8) {
    return
  }

  emitAccumulator = 0
  const availableIndex = trailParticles.value.findIndex((particle) => particle.hold <= 0 && particle.fade <= 0)
  const replaceIndex =
    availableIndex !== -1
      ? availableIndex
      : trailParticles.value.reduce((bestIndex, particle, index, particles) => {
          const currentLife = particle.hold + particle.fade
          const bestLife = particles[bestIndex].hold + particles[bestIndex].fade
          return currentLife < bestLife ? index : bestIndex
        }, 0)

  const spreadX = (Math.random() - 0.5) * 8 * speedWeight
  const spreadY = (Math.random() - 0.5) * 8 * speedWeight
  const driftX = ((Math.random() - 0.5) * 0.42 + velocityX * 0.018) * speedWeight
  const driftY = ((Math.random() - 0.5) * 0.42 + velocityY * 0.018) * speedWeight
  const holdFrames = Math.round(80 + speed * 7)
  const fadeFrames = Math.round(24 + speed * 12)
  const nextParticle = {
    ...trailParticles.value[replaceIndex],
    x: currentGlowX + spreadX,
    y: currentGlowY + spreadY,
    driftX,
    driftY,
    hold: holdFrames,
    fade: fadeFrames,
    maxFade: fadeFrames,
    maxOpacity: 0.72 - replaceIndex * 0.012,
    opacity: 0.78,
  }

  trailParticles.value.splice(replaceIndex, 1, nextParticle)
}

const startGlowAnimation = () => {
  // 使用缓动插值驱动唯一主光点，尾迹通过分阶段残影粒子生成
  if (glowAnimationFrame) {
    return
  }

  const animate = () => {
    velocityX = velocityX * 0.72 + (targetGlowX - currentGlowX) * 0.28
    velocityY = velocityY * 0.72 + (targetGlowY - currentGlowY) * 0.28
    currentGlowX += (targetGlowX - currentGlowX) * 0.16
    currentGlowY += (targetGlowY - currentGlowY) * 0.16
    emitTrailParticle()
    updateGlowStyles()

    const mainDistance = Math.abs(targetGlowX - currentGlowX) + Math.abs(targetGlowY - currentGlowY)
    const tailDistance = trailParticles.value.reduce((total, particle) => {
      return total + particle.hold + particle.fade
    }, 0)

    if (mainDistance < 0.18 && tailDistance < 8) {
      currentGlowX = targetGlowX
      currentGlowY = targetGlowY
      updateGlowStyles()
      glowAnimationFrame = 0
      return
    }

    glowAnimationFrame = requestAnimationFrame(animate)
  }

  glowAnimationFrame = requestAnimationFrame(animate)
}

const setMouseGlowPosition = (clientX, clientY) => {
  // 根据鼠标位置更新目标点，实际视觉运动由动画缓动完成
  if (!loginContainerRef.value) {
    return
  }

  const { left, top } = loginContainerRef.value.getBoundingClientRect()
  targetGlowX = clientX - left
  targetGlowY = clientY - top
  loginContainerRef.value.style.setProperty('--glow-opacity', '0.82')
  startGlowAnimation()
}

const handleMouseMove = (event) => {
  // 鼠标移动时只更新目标点，避免产生多个前导光点
  setMouseGlowPosition(event.clientX, event.clientY)
}

const handleMouseLeave = () => {
  // 鼠标离开窗口时降低整体亮度，避免页面过亮
  if (!loginContainerRef.value) {
    return
  }

  loginContainerRef.value.style.setProperty('--glow-opacity', '0.56')
}

const resetForm = () => {
  // 切换模式时重置表单
  form.value = {
    userName: '',
    password: '',
    confirmPassword: '',
  }
}

const goRegister = () => {
  // 从登录切到注册
  isRegister.value = true
  errorMessage.value = ''
  resetForm()
}

const backToLogin = () => {
  // 从注册返回登录
  isRegister.value = false
  errorMessage.value = ''
  resetForm()
}

const form = ref({
  userName: '',
  password: '',
  confirmPassword: '',
})

const handleSubmit = async () => {
  if (isRegister.value) {
    await handleRegister()
  } else {
    await handleLogin()
  }
}

const handleLogin = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    await userStore.login({
      userName: form.value.userName,
      password: form.value.password,
    })
    router.push('/')
  } catch (error) {
    // 根据后端返回的错误信息显示具体错误
    if (error.message) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '登录失败，请检查用户名和密码'
    }
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  loading.value = true
  errorMessage.value = ''

  // 检查密码长度
  if (form.value.password.length < 6) {
    errorMessage.value = '密码长度不能少于6位'
    loading.value = false
    return
  }

  // 检查密码确认
  if (form.value.password !== form.value.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    loading.value = false
    return
  }

  try {
    // 注册时传递用户输入的用户名和密码
    const registerResult = await userStore.register({
      userName: form.value.userName,
      password: form.value.password,
    })

    // 检查注册是否成功
    if (registerResult && registerResult.code !== 0) {
      // 注册失败，显示错误信息
      errorMessage.value = registerResult.message || '注册失败'
      loading.value = false
      return
    }

    // 注册成功后使用用户输入的密码直接登录
    await handleLogin()
  } catch (error) {
    // 根据后端返回的错误信息显示具体错误
    if (error.message) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '注册失败，请稍后重试'
    }
    loading.value = false
  }
}

onMounted(() => {
  // 初始化默认光效位置，避免首次进入页面时出现跳变
  if (loginContainerRef.value) {
    const { width, height } = loginContainerRef.value.getBoundingClientRect()
    currentGlowX = width * 0.38
    currentGlowY = height * 0.32
    targetGlowX = currentGlowX
    targetGlowY = currentGlowY
    loginContainerRef.value.style.setProperty('--mouse-x', `${currentGlowX}px`)
    loginContainerRef.value.style.setProperty('--mouse-y', `${currentGlowY}px`)
    loginContainerRef.value.style.setProperty('--trail-x', `${currentGlowX}px`)
    loginContainerRef.value.style.setProperty('--trail-y', `${currentGlowY}px`)
    loginContainerRef.value.style.setProperty('--glow-opacity', '0.62')
    trailParticles.value = trailParticles.value.map((particle, index) => ({
      ...particle,
      x: currentGlowX,
      y: currentGlowY,
      driftX: 0,
      driftY: 0,
      opacity: Math.max(0, 0.18 - index * 0.012),
      hold: Math.max(0, 4 - index),
      fade: Math.max(0, 16 - index),
      maxFade: Math.max(1, 16 - index),
      maxOpacity: Math.max(0.1, 0.46 - index * 0.02),
    }))
  }

  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseleave', handleMouseLeave)
})

onBeforeUnmount(() => {
  // 组件卸载时移除事件并清理动画帧，避免内存泄漏
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseleave', handleMouseLeave)

  if (glowAnimationFrame) {
    cancelAnimationFrame(glowAnimationFrame)
  }
})
</script>

<style lang="scss" scoped>
.login-container {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  overflow: hidden;
  --mouse-x: 50%;
  --mouse-y: 50%;
  --trail-x: 50%;
  --trail-y: 50%;
  --glow-opacity: 0.62;
  background:
    radial-gradient(circle at top left, rgba(144, 19, 139, 0.18), transparent 26%),
    radial-gradient(circle at bottom right, rgba(144, 19, 139, 0.12), transparent 24%),
    linear-gradient(180deg, #f3eef9 0%, #ebe4f4 100%);
}

.mouse-glow-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(
      circle 28px at var(--mouse-x) var(--mouse-y),
      rgba(144, 19, 139, calc(var(--glow-opacity) * 0.16)) 0%,
      rgba(144, 19, 139, calc(var(--glow-opacity) * 0.08)) 42%,
      rgba(144, 19, 139, 0) 88%
    );
  filter: blur(3px);
  opacity: 1;
  transition: opacity 0.3s ease;
  z-index: 0;
}

.mouse-trail-particle {
  position: absolute;
  left: 0;
  top: 0;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.95) 0%, rgba(233, 176, 248, 0.82) 26%, rgba(183, 66, 205, 0.42) 60%, rgba(144, 19, 139, 0) 100%);
  box-shadow: none;
  filter: blur(0.4px);
  z-index: 0;
}

.mouse-orb {
  position: absolute;
  left: 0;
  top: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(circle, rgba(255, 255, 255, 1) 0%, rgba(244, 203, 255, 1) 26%, rgba(198, 78, 226, 0.98) 56%, rgba(144, 19, 139, 0.2) 76%, rgba(144, 19, 139, 0) 100%);
  box-shadow:
    0 0 0 2px rgba(144, 19, 139, 0.08),
    0 0 12px rgba(144, 19, 139, 0.34),
    0 0 20px rgba(144, 19, 139, 0.18);
  transform: translate(calc(var(--mouse-x) - 3.5px), calc(var(--mouse-y) - 3.5px));
  opacity: var(--glow-opacity);
  z-index: 0;
}

.login-decoration {
  position: absolute;
  border-radius: 999px;
  filter: blur(18px);
  pointer-events: none;
}

.login-decoration-left {
  top: 7%;
  left: 5%;
  width: 170px;
  height: 170px;
  background: rgba(144, 19, 139, 0.14);
}

.login-decoration-right {
  right: 8%;
  bottom: 10%;
  width: 220px;
  height: 220px;
  background: rgba(144, 19, 139, 0.1);
}

.login-shell {
  width: 100%;
  max-width: 1200px;
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(380px, 440px);
  gap: 36px;
  align-items: center;
  z-index: 1;
}

/* 左侧品牌区 */
.hero-panel {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  min-height: 100%;
  gap: 14px;
  padding-top: 10px;
}

.hero-brand {
  width: 88px;
  height: 88px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(17, 24, 39, 0.06);
  box-shadow: 0 16px 36px rgba(17, 24, 39, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-logo {
  width: 82%;
  height: 82%;
  object-fit: contain;
}

.hero-kicker {
  margin: 0 0 12px;
  color: var(--primary-color, #90138b);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.22em;
}

.hero-title {
  margin: 0;
  color: #111827;
  font-size: clamp(2.8rem, 4.6vw, 4.4rem);
  line-height: 0.98;
  letter-spacing: -0.06em;
  font-weight: 700;

  span {
    display: block;
  }

  span + span {
    margin-top: 8px;
    color: #141b2d;
  }
}

.hero-subtitle {
  margin: 10px 0 0;
  max-width: 520px;
  color: #6b7280;
  font-size: 1.02rem;
  line-height: 1.85;
}

.hero-features {
  list-style: none;
  display: grid;
  gap: 10px;
  margin: 8px 0 0;
  padding: 0;

  li {
    position: relative;
    padding-left: 22px;
    color: #475467;
    font-size: 0.98rem;
    line-height: 1.7;
  }

  li::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0.7em;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: linear-gradient(135deg, #90138b 0%, #b43bb0 100%);
    box-shadow: 0 0 0 4px rgba(144, 19, 139, 0.08);
  }
}

/* 右侧登录区 */
.auth-panel {
  display: flex;
  justify-content: flex-end;
}

.login-form {
  width: 100%;
  max-width: 430px;
  position: relative;
  padding: 34px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(17, 24, 39, 0.06);
  box-shadow: 0 24px 60px rgba(17, 24, 39, 0.12);
  backdrop-filter: blur(20px);
}

.back-arrow {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;

  .back-arrow-icon {
    font-size: 22px;
    line-height: 1;
    transform: translateX(-1px);
  }
}

.auth-head {
  margin-bottom: 22px;

  h2 {
    margin: 0;
    color: #111827;
    font-size: 1.7rem;
    line-height: 1.15;
    letter-spacing: -0.04em;
  }

  p {
    margin: 10px 0 0;
    color: #6b7280;
    font-size: 0.98rem;
    line-height: 1.6;
  }
}

.form-group {
  margin-bottom: 16px;

  label {
    display: block;
    margin-bottom: 8px;
    color: #374151;
    font-size: 0.92rem;
    font-weight: 600;
  }

  input {
    width: 100%;
    height: 52px;
    padding: 0 16px;
    border: 1px solid #d8dce6;
    border-radius: 14px;
    background: #fff;
    font-size: 0.98rem;
    color: #111827;
    outline: none;
    transition:
      border-color 0.2s ease,
      box-shadow 0.2s ease,
      transform 0.2s ease;

    &:focus {
      border-color: rgba(144, 19, 139, 0.55);
      box-shadow: 0 0 0 4px rgba(144, 19, 139, 0.1);
    }

    &::placeholder {
      color: #98a2b3;
    }
  }
}

.primary-button {
  width: 100%;
  height: 52px;
  border: none;
  border-radius: 14px;
  background: var(--primary-color, #90138b);
  color: #fff;
  font-size: 0.98rem;
  font-weight: 600;
  box-shadow: 0 12px 24px rgba(144, 19, 139, 0.18);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    opacity 0.2s ease;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 16px 28px rgba(144, 19, 139, 0.22);
  }

  &:disabled {
    opacity: 0.72;
    cursor: not-allowed;
  }
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  color: #6b7280;
  font-size: 0.92rem;
}

.toggle-button {
  height: auto;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--primary-color, #90138b);
  font-weight: 700;
  cursor: pointer;
}

.error-message {
  margin: 0 0 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(254, 242, 242, 0.92);
  border: 1px solid rgba(220, 38, 38, 0.12);
  color: #b42318;
}

@media (max-width: 980px) {
  .login-container {
    padding: 24px;
  }

  .login-shell {
    grid-template-columns: 1fr;
    gap: 36px;
  }

  .hero-panel {
    align-items: center;
    text-align: center;
    padding-top: 0;
  }

  .hero-features li {
    padding-left: 0;
  }

  .hero-features li::before {
    display: none;
  }

  .auth-panel {
    justify-content: center;
  }
}

@media (max-width: 560px) {
  .login-container {
    padding: 16px;
  }

  .login-form {
    padding: 26px 20px;
  }

  .hero-title {
    font-size: 2.4rem;
  }

  .switch-row {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
