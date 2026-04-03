import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { tooltipDirective } from './directives/tooltip'

const app = createApp(App)

app.directive('tooltip', tooltipDirective)

app.use(createPinia())
app.use(router)
app.mount('#app')
