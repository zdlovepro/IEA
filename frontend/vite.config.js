import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const resolveFromRoot = relativePath => fileURLToPath(new URL(relativePath, import.meta.url))

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolveFromRoot('./src'),
      '@components': resolveFromRoot('./src/components'),
      '@views': resolveFromRoot('./src/views'),
      '@stores': resolveFromRoot('./src/stores'),
      '@utils': resolveFromRoot('./src/utils'),
      '@assets': resolveFromRoot('./src/assets'),
      '@constants': resolveFromRoot('./src/constants'),
    },
  },
  server: {
    port: 5173,
    host: true,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (['vue', 'vue-router', 'pinia'].some(pkg => id.includes(`node_modules/${pkg}/`))) {
            return 'vue'
          }
          return undefined
        },
      },
    },
  },
})
