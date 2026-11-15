import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'

// 占位首页：正式接入时由前端包（BAS-FE-01）按授权快照菜单生成路由。
const Home = {
  render: () => h('div', { style: 'padding: 24px' }, '{app} 示例页')
}

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: Home }]
})

createApp(App).use(ElementPlus).use(router).mount('#app')
