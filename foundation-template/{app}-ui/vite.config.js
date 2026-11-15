import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// {app}-ui 最小可构建骨架。
// 正式接入时以 BAS-FE-01 前端组件包替换登录跳转、路由、按钮权限指令等实现。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173
  }
})
