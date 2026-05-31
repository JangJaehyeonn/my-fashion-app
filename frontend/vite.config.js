import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login': { target: 'http://localhost:8080', changeOrigin: true },
      // 개발용: 프로덕션에서는 Spring Boot가 AI 서버를 프록시해야 함
      '/ai': { target: 'http://localhost:8000', changeOrigin: true },
    },
  },
})
