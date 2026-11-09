import { defineConfig } from '@playwright/test';

// channel 'chromium' = Chromium completo en modo headless nuevo; el headless-shell no entrega requestAnimationFrame aquí.
export default defineConfig({ testDir: 'e2e', reporter: 'line', use: { channel: 'chromium' } });
