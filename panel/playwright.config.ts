import { defineConfig } from '@playwright/test';

// channel 'chromium' = Chromium completo en modo headless nuevo; el headless-shell no entrega requestAnimationFrame aquí.
// workers 1: admin.spec.ts y demo.spec.ts entran con el mismo administrador y el login tiene límite de intentos por minuto.
export default defineConfig({ testDir: 'e2e', workers: 1, reporter: 'line', use: { channel: 'chromium' } });
