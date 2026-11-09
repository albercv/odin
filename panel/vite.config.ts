import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/v1': 'http://127.0.0.1:8080' } },
  test: { environment: 'jsdom', globals: true, setupFiles: ['src/setup.ts'], include: ['src/**/*.test.tsx'] },
});
