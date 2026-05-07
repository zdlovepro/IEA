/**
 * 应用入口文件
 * 创建 Vue 3 实例，注册 Pinia 状态管理和 Vue Router，挂载到 #app
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/styles/main.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
