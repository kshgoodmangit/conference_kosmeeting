import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(),
    tailwindcss(),
  ],
  build: {
    // Gradle copyFrontendAssets 작업이 Spring Boot 정적 리소스로 복사합니다.
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/public': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        // Preserve the browser-facing domain/port for access-request links.
        changeOrigin: false,
      },
    },
  },
});
