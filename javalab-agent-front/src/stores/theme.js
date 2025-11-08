import { defineStore } from 'pinia'

export const useThemeStore = defineStore('theme', () => {
  // 初始化浅色主题
  const initTheme = () => {
    const root = document.documentElement
    root.classList.remove('dark-theme')
    root.classList.add('light-theme')
  }
  
  // 初始化
  initTheme()
  
  return {
    initTheme
  }
})

