import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/backend': {
        target: 'https://campus.1314-520.xyz',
        changeOrigin: true
      },
      '/api': {
        target: 'https://campus.1314-520.xyz',
        changeOrigin: true
      }
    }
  }
})
