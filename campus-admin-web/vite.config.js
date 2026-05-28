import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/backend': {
        target: 'http://39.105.153.170',
        changeOrigin: true
      },
      '/api': {
        target: 'http://39.105.153.170',
        changeOrigin: true
      }
    }
  }
})
