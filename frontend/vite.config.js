import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server proxies /api to Spring Boot, so the browser sees a single origin
// and no CORS configuration is needed on the backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: 'dist'
  }
})
