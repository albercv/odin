import { readFileSync } from 'node:fs';
import AxeBuilder from '@axe-core/playwright';
import { expect, test, type APIRequestContext, type BrowserContext, type Cookie, type Page } from '@playwright/test';

const state = JSON.parse(readFileSync(process.env.DEMO_STATE ?? '', 'utf8')) as { admin: { email: string; password: string } };
const PANEL = process.env.DEMO_PANEL_URL ?? '';
const LONG = (prefix: string) => `${prefix} ${'Muy larga '.repeat(11)}`.slice(0, 120).trim();
const LONG_COMPANY = LONG('Empresa');
const LONG_PERSON = LONG('Persona');
const LONG_PROJECT = LONG('Proyecto');
const LONG_EMAIL = `${'correo-largo-sin-espacios-'.repeat(4)}@ficticio.test`;
const RULES = ['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'];

const API = process.env.DEMO_API_URL ?? '';
const CARD_COMPANY = 'Empresa con ficha';
const EMPTY_COMPANY = 'Empresa sin fichas';

async function postJson<T>(request: APIRequestContext, path: string, data: unknown, headers: Record<string, string> = {}): Promise<T> {
  const response = await request.post(`${API}${path}`, { data, headers });
  expect(response.ok(), `${path} ${response.status()}`).toBeTruthy();
  return (await response.json()) as T;
}

// Own data, so the Revisión scans do not depend on demo.spec.ts having approved the card from prepare.
async function seedReviewData(request: APIRequestContext): Promise<void> {
  const company = await postJson<{ id: string }>(request, '/v1/admin/companies', { name: CARD_COMPANY, code: 'TARJ' });
  await postJson(request, '/v1/admin/companies', { name: EMPTY_COMPANY, code: 'VACIA' });
  await request.put(`${API}/v1/admin/companies/${company.id}/capabilities`, { data: { capabilities: ['SAVE_KNOWLEDGE_CARD'] } });
  const added = await postJson<{ member: { userId: string } }>(request, `/v1/admin/companies/${company.id}/members`,
    { firstName: 'Trabajadora', lastName: 'Visual', email: 'visual@demo.invalid', roles: ['WORKER'] });
  const { key } = await postJson<{ key: string }>(request, `/v1/admin/companies/${company.id}/members/${added.member.userId}/keys`, { label: 'visual' });
  await postJson(request, '/v1/cards', { title: 'Franquicia ficticia por daños de agua', body: 'La póliza ficticia aplica una franquicia de 150 euros por siniestro.' }, { Authorization: `Bearer ${key}` });
}

// admin.spec.ts closes the session from prepare, and failed logins are rate-limited: log in once and share the cookies.
let sessionCookies: Cookie[] = [];

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(PANEL);
  await page.getByLabel('Correo').fill(state.admin.email);
  await page.getByLabel('Contraseña').fill(state.admin.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Empresas', exact: true })).toBeVisible();
  sessionCookies = await context.cookies();
  await seedReviewData(context.request);
  await context.close();
});

async function signIn(context: BrowserContext): Promise<void> {
  await context.addCookies(sessionCookies);
}

async function openCompany(page: Page, name: string): Promise<void> {
  await page.goto(PANEL);
  await page.getByRole('button', { name: `Abrir ${name}`, exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}

const openCompanyA = (page: Page) => openCompany(page, 'Empresa A');

async function openReview(page: Page, company = 'Empresa A'): Promise<void> {
  await openCompany(page, company);
  await page.getByRole('link', { name: 'Revisión' }).click();
  await expect(page.getByRole('heading', { name: 'Revisión' }).first()).toBeVisible();
}

async function openReviewWithCard(page: Page): Promise<void> {
  await openReview(page, CARD_COMPANY);
  await expect(page.getByRole('button', { name: 'Aprobar', exact: true })).toBeVisible();
}

async function openRejectPanel(page: Page): Promise<void> {
  await openReviewWithCard(page);
  await page.getByRole('button', { name: 'Rechazar', exact: true }).click();
  await expect(page.getByLabel('Motivo del rechazo')).toBeVisible();
}

const SCREENS: Record<string, (page: Page) => Promise<void>> = {
  login: async (page) => { await page.goto(PANEL); await expect(page.getByRole('heading', { name: 'Entra en el panel' })).toBeVisible(); },
  cover: async (page) => { await page.goto(`${PANEL}/#cover`); await expect(page.getByRole('heading', { level: 1 })).toBeVisible(); },
  company: openCompanyA,
  'review empty': (page) => openReview(page, EMPTY_COMPANY),
  'review with card': openReviewWithCard,
  'review reject panel': openRejectPanel,
};

for (const scheme of ['light', 'dark'] as const) {
  for (const [screen, open] of Object.entries(SCREENS)) {
    test(`axeFindsNoSeriousIssues: ${screen} in ${scheme} theme`, async ({ page, context }) => {
      await page.emulateMedia({ colorScheme: scheme });
      if (screen !== 'login' && screen !== 'cover') await signIn(context);
      await open(page);
      await page.waitForTimeout(400);
      const { violations, incomplete } = await new AxeBuilder({ page }).withTags(RULES).analyze();
      const contrast = incomplete.filter((v) => v.id === 'color-contrast');
      console.log(`axe incomplete color-contrast ${screen} ${scheme}: ${contrast.reduce((n, v) => n + v.nodes.length, 0)} nodes${contrast.flatMap((v) => v.nodes.map((n) => ` [${n.target.join(' ')}]`)).join('')}`);
      const blocking = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
      console.log(`axe ${screen} ${scheme}: ${violations.length} violations, ${blocking.length} serious/critical`);
      expect(blocking.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
    });
  }
}

test('coverAppearsOnlyWithCover', async ({ page }) => {
  await page.goto(`${PANEL}/#cover`);
  await expect(page.getByRole('heading', { name: 'Odín, el cerebro digital del despacho' })).toBeVisible();
  const withoutCover = await page.request.get(`${PANEL}/brand/brand.json`).then((r) => r.json()) as Record<string, unknown>;
  delete withoutCover.cover;
  await page.route('**/brand/brand.json', (route) => route.fulfill({ json: withoutCover }));
  await page.goto('about:blank');
  await page.goto(`${PANEL}/#cover`);
  await expect(page.getByRole('heading', { name: 'Entra en el panel' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Odín, el cerebro digital del despacho' })).toHaveCount(0);
});

async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const [scrollWidth, innerWidth] = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);
}

async function createLongNamedData(page: Page): Promise<void> {
  await page.goto(PANEL);
  await page.getByRole('button', { name: 'Nueva empresa' }).click();
  await page.getByLabel('Nombre de la empresa').fill(LONG_COMPANY);
  await page.getByLabel('Código').fill('LARGA');
  await page.getByRole('button', { name: 'Crear empresa' }).click();
  await page.getByRole('button', { name: `Abrir ${LONG_COMPANY}` }).click();
  await expect(page.getByRole('heading', { name: LONG_COMPANY })).toBeVisible();
  await page.getByRole('button', { name: 'Añadir persona', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Añadir persona' });
  await dialog.getByLabel('Nombre', { exact: true }).fill(LONG_PERSON);
  await dialog.getByLabel('Apellidos').fill('Apellidos de prueba');
  await dialog.getByLabel('Correo').fill(LONG_EMAIL);
  await dialog.getByRole('button', { name: 'Crear usuario' }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: 'Nuevo proyecto' }).click();
  await page.getByLabel('Nombre del proyecto').fill(LONG_PROJECT);
  await page.getByRole('button', { name: 'Crear proyecto' }).click();
  await expect(page.getByRole('row').filter({ hasText: LONG_PROJECT })).toBeVisible();
}

test('longNamesDoNotOverflow', async ({ page, context }) => {
  await signIn(context);
  await createLongNamedData(page);
  await expectNoHorizontalScroll(page);
  await expect(page.getByRole('row').filter({ hasText: LONG_PERSON })).toContainText(LONG_EMAIL);
  await expect(page.getByRole('heading', { name: LONG_COMPANY })).toContainText(LONG_COMPANY);
  await page.setViewportSize({ width: 375, height: 800 });
  await expectNoHorizontalScroll(page);
});

test('narrowScreenHasNoHorizontalScroll', async ({ page, context }) => {
  await signIn(context);
  await page.setViewportSize({ width: 375, height: 800 });
  await openCompanyA(page);
  await expectNoHorizontalScroll(page);
  await expect(page.getByRole('button', { name: 'Añadir persona', exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Revisión' }).click();
  await expect(page.getByRole('heading', { name: 'Revisión' }).first()).toBeVisible();
  await expectNoHorizontalScroll(page);
  await expect(page.getByRole('button', { name: 'Salir' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Mi cuenta' })).toBeVisible();
});

test('brandFilesCannotRunScripts', async ({ request }) => {
  const manifest = await request.get(`${PANEL}/brand/brand.json`);
  expect(manifest.headers()['content-security-policy']).toContain('sandbox');
  const { logo } = (await manifest.json()) as { logo: string };
  const logoResponse = await request.get(`${PANEL}/brand/${logo}`);
  expect(logoResponse.ok()).toBeTruthy();
  expect(logoResponse.headers()['content-security-policy']).toContain('sandbox');
});

test('brandIsNotCached', async ({ request }) => {
  const response = await request.get(`${PANEL}/brand/brand.json`);
  expect(response.headers()['cache-control']).toContain('no-cache');
});
