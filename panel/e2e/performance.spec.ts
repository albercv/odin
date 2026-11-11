import { expect, test, type Page } from '@playwright/test';

const PANEL = process.env.DEMO_PANEL_URL ?? '';
const MAX_MEDIAN_FRAME_MS = 4;
const MAX_EXTRA_TYPING_MS = 50;

async function throttle(page: Page): Promise<void> {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
}

const median = (values: number[]): number => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)] ?? 0;

test('brainFrameUnderFourMsAtQuarterCpu', async ({ page }) => {
  await throttle(page);
  await page.goto(PANEL);
  const canvas = page.locator('canvas[data-median-frame-ms]');
  await expect(canvas).toHaveCount(1, { timeout: 30_000 });
  const average = Number(await canvas.getAttribute('data-average-frame-ms'));
  const medianMs = Number(await canvas.getAttribute('data-median-frame-ms'));
  console.log(`brain frame at 4x CPU throttle: average ${average} ms, median ${medianMs} ms`);
  expect(medianMs).toBeLessThanOrEqual(MAX_MEDIAN_FRAME_MS);
});

async function typingMs(page: Page, reducedMotion: 'reduce' | 'no-preference'): Promise<number> {
  await page.emulateMedia({ reducedMotion });
  await page.goto(PANEL);
  const email = page.getByLabel('Correo');
  await expect(email).toBeVisible();
  if (reducedMotion === 'no-preference') await expect(page.locator('canvas[data-median-frame-ms]')).toHaveCount(1, { timeout: 30_000 });
  await email.focus();
  const begin = Date.now();
  await page.keyboard.type('persona.ficticia@ejemplo.test', { delay: 0 });
  await page.evaluate(() => new Promise((done) => requestAnimationFrame(() => done(true))));
  return Date.now() - begin;
}

test('loginNotSlowedByTheBrain', async ({ page }) => {
  await throttle(page);
  const animated: number[] = [];
  const still: number[] = [];
  for (let i = 0; i < 5; i += 1) {
    animated.push(await typingMs(page, 'no-preference'));
    still.push(await typingMs(page, 'reduce'));
  }
  const extra = median(animated) - median(still);
  console.log(`typing in Correo at 4x CPU: animated ${animated.map((n) => n.toFixed(0))} ms, still ${still.map((n) => n.toFixed(0))} ms, median difference ${extra.toFixed(1)} ms`);
  expect(extra).toBeLessThanOrEqual(MAX_EXTRA_TYPING_MS);
});
