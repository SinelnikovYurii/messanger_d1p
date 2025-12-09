import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // Проксирование HTTP API
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        secure: false,
      },
      // Проксирование WebSocket (сообщения)
      '/ws': {
        target: 'ws://localhost:8083',
        ws: true,
        changeOrigin: true,
        secure: false,
      },
      // Проксирование авторизации
      '/auth': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    outDir: 'dist',
  }
});