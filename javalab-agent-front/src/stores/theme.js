import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  // 主题：'light' 或 'dark'
  const theme = ref('dark')
  
  // 初始化主题（从 localStorage 加载或使用系统偏好）
  const initTheme = () => {
    const savedTheme = localStorage.getItem('chat_theme')
    if (savedTheme === 'light' || savedTheme === 'dark') {
      theme.value = savedTheme
    } else {
      // 使用系统偏好
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      theme.value = prefersDark ? 'dark' : 'light'
    }
    applyTheme(theme.value)
  }
  
  // 应用主题到 DOM
  const applyTheme = (newTheme) => {
    const root = document.documentElement
    if (newTheme === 'light') {
      root.classList.remove('dark-theme')
      root.classList.add('light-theme')
    } else {
      root.classList.remove('light-theme')
      root.classList.add('dark-theme')
    }
  }
  
  // 切换主题
  const toggleTheme = () => {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem('chat_theme', theme.value)
    applyTheme(theme.value)
  }
  
  // 设置主题
  const setTheme = (newTheme) => {
    if (newTheme === 'light' || newTheme === 'dark') {
      theme.value = newTheme
      localStorage.setItem('chat_theme', theme.value)
      applyTheme(theme.value)
    }
  }
  
  // 监听主题变化，同步到 DOM
  watch(theme, (newTheme) => {
    applyTheme(newTheme)
  })
  
  // 初始化
  initTheme()
  
  return {
    theme,
    toggleTheme,
    setTheme,
    initTheme
  }
})

