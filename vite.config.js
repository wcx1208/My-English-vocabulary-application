import { defineConfig } from 'vite'

// base 用相对路径，方便部署在任意子路径（或本地静态服务器）下打开
export default defineConfig({
  base: './',
  server: { host: true },
  preview: { host: true },
})
