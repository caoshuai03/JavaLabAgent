import { createRouter, createWebHistory } from 'vue-router'
import Chat from '../views/Chat.vue'
import Login from '../views/Login.vue'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: Chat,
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  
  if (to.meta.requiresAuth) {
    // 验证token有效性
    const isValid = await userStore.validateToken()
    if (!isValid || !userStore.isLoggedIn()) {
      next('/login')
    } else {
      next()
    }
  } else if (to.name === 'Login') {
    // 验证token有效性
    const isValid = await userStore.validateToken()
    if (isValid && userStore.isLoggedIn()) {
      next('/')
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router