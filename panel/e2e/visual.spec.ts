import { readFileSync } from 'node:fs';
import AxeBuilder from '@axe-core/playwright';
import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Cookie, type Page } from '@playwright/test';

const state = JSON.parse(readFileSync(process.env.DEMO_STATE ?? '', 'utf8')) as { admin: { email: string; password: string } };
const PANEL = process.env.DEMO_PANEL_URL ?? '';
const LONG = (prefix: string) => `${prefix} ${'Muy larga '.repeat(11)}`.slice(0, 120).trim();
const LONG_COMPANY = LONG('Empresa');
const LONG_PERSON = LONG('Persona');
const LONG_PROJECT = LONG('Proyecto');
const LONG_EMAIL = `${'correo-largo-sin-espacios-'.repeat(4)}@ficticio.test`;
const SWITCHER = { email: 'doble@demo.invalid', password: 'clave-ficticia-larga-1' };
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
  const empty = await postJson<{ id: string }>(request, '/v1/admin/companies', { name: EMPTY_COMPANY, code: 'VACIA' });
  const admin = { firstName: 'Administradora', lastName: 'Doble', email: SWITCHER.email, roles: ['COMPANY_ADMIN'], password: SWITCHER.password };
  await postJson(request, `/v1/admin/companies/${empty.id}/members`, admin);
  await request.put(`${API}/v1/admin/companies/${company.id}/capabilities`, { data: { capabilities: ['SAVE_KNOWLEDGE_CARD'] } });
  await postJson(request, `/v1/admin/companies/${company.id}/members`, { ...admin, password: undefined });
  const added = await postJson<{ member: { userId: string } }>(request, `/v1/admin/companies/${company.id}/members`,
    { firstName: 'Trabajadora', lastName: 'Visual', email: 'visual@demo.invalid', roles: ['WORKER'] });
  await postJson(request, `/v1/admin/companies/${company.id}/projects`, { name: 'Proyecto visual' });
  await postJson(request, `/v1/admin/companies/${company.id}/members/${added.member.userId}/keys`, { label: 'panel visual' });
  const { key } = await postJson<{ key: string }>(request, `/v1/admin/companies/${company.id}/members/${added.member.userId}/keys`, { label: 'visual' });
  await postJson(request, '/v1/cards', { title: 'Franquicia ficticia por daños de agua', body: 'La póliza ficticia aplica una franquicia de 150 euros por siniestro.' }, { Authorization: `Bearer ${key}` });
}

// admin.spec.ts closes the session from prepare, and failed logins are rate-limited: log in once and share the cookies.
let sessionCookies: Cookie[] = [];
let switcherCookies: Cookie[] = [];

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(PANEL);
  await page.getByLabel('Correo').fill(state.admin.email);
  await page.getByLabel('Contraseña').fill(state.admin.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Grupo', exact: true })).toBeVisible();
  sessionCookies = await context.cookies();
  await seedReviewData(context.request);
  await context.close();
  switcherCookies = await logInAs(browser, SWITCHER.email, SWITCHER.password);
});

async function logInAs(browser: Browser, email: string, password: string): Promise<Cookie[]> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(PANEL);
  await page.getByLabel('Correo').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('combobox', { name: 'Empresa' })).toBeVisible();
  const cookies = await context.cookies();
  await context.close();
  return cookies;
}

async function signIn(context: BrowserContext): Promise<void> {
  await context.addCookies(sessionCookies);
}

async function openCompany(page: Page, name: string): Promise<void> {
  await page.goto(PANEL);
  await page.getByRole('button', { name: `Abrir ${name}`, exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}

const openCompanyA = (page: Page) => openCompany(page, 'Empresa A');

async function openPersonAccesses(page: Page): Promise<void> {
  await openCompany(page, CARD_COMPANY);
  await page.getByRole('button', { name: 'Más acciones de Trabajadora Visual', exact: true }).click();
  await page.getByRole('button', { name: 'Accesos de Trabajadora Visual', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Accesos de Trabajadora Visual' })).toBeVisible();
}

async function openRolesForm(page: Page): Promise<void> {
  await openCompany(page, CARD_COMPANY);
  await page.getByRole('button', { name: 'Más acciones de Trabajadora Visual', exact: true }).click();
  await page.getByRole('button', { name: 'Papeles de Trabajadora Visual', exact: true }).click();
  await page.getByLabel('Administra la empresa').check();
  await expect(page.getByLabel('Contraseña')).toBeVisible();
}

async function openKeys(page: Page): Promise<void> {
  await openCompany(page, CARD_COMPANY);
  await page.getByRole('button', { name: 'Más acciones de Trabajadora Visual', exact: true }).click();
  await page.getByRole('button', { name: 'Claves de Trabajadora Visual', exact: true }).click();
  await expect(page.getByText('Cada clave da acceso a lo mismo que ve Trabajadora Visual en esta empresa.')).toBeVisible();
}

async function openProjectMembers(page: Page): Promise<void> {
  await openCompany(page, CARD_COMPANY);
  await page.getByRole('button', { name: 'Más acciones de Proyecto visual', exact: true }).click();
  await page.getByRole('button', { name: 'Miembros de Proyecto visual', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Miembros de Proyecto visual' })).toBeVisible();
}

async function openUploadDocument(page: Page): Promise<void> {
  await openCompany(page, CARD_COMPANY);
  await page.getByRole('link', { name: 'Documentos', exact: true }).click();
  await page.getByRole('button', { name: 'Subir documento', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Subir documento' })).toBeVisible();
}

async function openSwitcher(page: Page): Promise<void> {
  await page.goto(PANEL);
  await expect(page.getByRole('combobox', { name: 'Empresa' })).toBeVisible();
}

async function openAddPerson(page: Page): Promise<void> {
  await openCompany(page, CARD_COMPANY);
  await page.getByRole('button', { name: 'Añadir persona', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Añadir persona' })).toBeVisible();
}

async function openReview(page: Page, company = 'Empresa A'): Promise<void> {
  await openCompany(page, company);
  await page.getByRole('link', { name: 'Revisión' }).click();
  await expect(page.getByRole('heading', { name: 'Revisión' }).first()).toBeVisible();
}

// La bandeja se prueba con respuestas fijas de la API: no hay lote real en los datos de la demo.
async function openIntake(page: Page): Promise<void> {
  const item = (id: string, title: string) => ({ id, documentId: `d-${id}`, title, source: 'RULES', reason: `${title}, cabecera` });
  const batch = { id: 'b1', companyId: 'c1', channel: 'PANEL', state: 'IN_REVIEW', createdAt: '2026-11-13T10:00:00Z', uploaded: 40, reading: 0, pending: 12, alreadyThere: 3, discarded: 1, proposed: { PROJECT: 2 }, unplaced: 1,
    warnings: [{ code: 'NO_LOCAL_MODEL', message: 'No hay modelo local: solo proponen las reglas.' }] };
  const replies: Record<string, unknown> = {
    '/proposals': { groups: [{ kind: 'PROJECT', confidence: 'HIGH', proposals: [item('a', 'Factura 12'), item('b', 'Acta 3')] }] },
    '/project-candidates': { candidates: [] }, '/documents?unplaced=true': { documents: [{ id: 'u1', title: 'Escaneo suelto', path: 'cliente/varios', status: 'READY' }] },
  };
  await page.route('**/intake-batches**', (route) => {
    const url = route.request().url();
    const key = Object.keys(replies).find((k) => url.endsWith(k));
    return route.fulfill({ json: key ? replies[key] : batch, status: route.request().method() === 'POST' ? 201 : 200 });
  });
  await openCompany(page, CARD_COMPANY);
  await page.getByRole('link', { name: 'Primera carga' }).click();
  await page.getByRole('button', { name: 'Empezar una primera carga' }).click();
  await page.getByRole('button', { name: '2. Proyectos' }).click();
  await page.getByRole('button', { name: 'Ver documentos de Proyecto sin nombre' }).click();
  await expect(page.getByText('Factura 12, cabecera')).toBeVisible();
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

async function openGroupAdmins(page: Page): Promise<void> {
  await page.goto(PANEL);
  await page.getByRole('link', { name: 'Administración del grupo' }).click();
  await expect(page.getByRole('heading', { name: 'Administración del grupo', level: 1 })).toBeVisible();
  await expect(page.getByRole('row', { name: /Administrador/ }).first()).toBeVisible();
}

async function openTopics(page: Page, company: string): Promise<void> {
  await openCompany(page, company);
  await page.getByRole('link', { name: 'Temas', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Temas', level: 1 })).toBeVisible();
}

// «Empresa E» (demo-client, paso 11) ya tiene temas y un documento etiquetado.
async function openDocumentTopics(page: Page): Promise<void> {
  await openCompany(page, 'Empresa E');
  await page.getByRole('link', { name: 'Documentos', exact: true }).click();
  await page.getByRole('button', { name: 'Más acciones de Incidencia ficticia', exact: true }).click();
  await page.getByRole('button', { name: 'Ver detalle de Incidencia ficticia', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Incidencia ficticia' }).getByRole('button', { name: 'Volver a etiquetar' })).toBeVisible();
}

async function openFromMenu(page: Page, company: string, link: string): Promise<void> {
  await openCompany(page, company);
  await page.getByRole('link', { name: link, exact: true }).click();
  await expect(page.getByRole('heading', { name: link, level: 1 })).toBeVisible();
  await page.waitForLoadState('networkidle');
}

const SCREENS: Record<string, (page: Page) => Promise<void>> = {
  login: async (page) => { await page.goto(PANEL); await expect(page.getByRole('heading', { name: 'Entra en el panel' })).toBeVisible(); },
  cover: async (page) => { await page.goto(`${PANEL}/#cover`); await expect(page.getByRole('heading', { level: 1 })).toBeVisible(); },
  company: openCompanyA,
  'company person accesses': openPersonAccesses,
  'company add person': openAddPerson,
  'company roles form': openRolesForm,
  'company keys': openKeys,
  'project members': openProjectMembers,
  'documents upload dialog': openUploadDocument,
  'company switcher': openSwitcher,
  'group switcher': openSwitcher,
  'group admins': openGroupAdmins,
  'topics empty': (page) => openTopics(page, EMPTY_COMPANY),
  'topics list': async (page) => { await openTopics(page, 'Empresa E'); await expect(page.getByRole('row').filter({ hasText: 'agua y humedades' })).toBeVisible(); },
  'document topics dialog': openDocumentTopics,
  // «Empresa E2E» (admin.spec.ts) tiene tres personas con una pregunta sin resultado y fichas de una sola autora (su desglose se calla).
  'usage with people': async (page) => { await openFromMenu(page, 'Empresa E2E', 'Uso'); await expect(page.getByRole('region', { name: 'Por persona' }).getByText('Uso Dos')).toBeVisible(); await expect(page.getByText('No se muestra para proteger a las personas (pocas en este periodo)')).toBeVisible(); },
  'gaps with rows': async (page) => { await openFromMenu(page, 'Empresa E2E', 'Huecos'); await expect(page.getByRole('row').filter({ hasText: 'xqzvwk' })).toBeVisible(); },
  'gaps empty': async (page) => { await openFromMenu(page, 'Empresa A', 'Huecos'); await expect(page.getByRole('heading', { name: 'Este mes no hay consultas sin resultados' })).toBeVisible(); },
  'review empty': (page) => openReview(page, EMPTY_COMPANY),
  'review with card': openReviewWithCard,
  'review reject panel': openRejectPanel,
  'intake groups': openIntake,
};

for (const scheme of ['light', 'dark'] as const) {
  for (const [screen, open] of Object.entries(SCREENS)) {
    test(`axeFindsNoSeriousIssues: ${screen} in ${scheme} theme`, async ({ page, context }) => {
      await page.emulateMedia({ colorScheme: scheme });
      if (screen === 'company switcher') await context.addCookies(switcherCookies);
      else if (screen !== 'login' && screen !== 'cover') await signIn(context);
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
  await dialog.getByRole('button', { name: 'Añadir persona' }).click();
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

test('personsTableShowsEveryColumnAtPhoneWidth', async ({ page, context }) => {
  await signIn(context);
  await page.setViewportSize({ width: 375, height: 800 });
  await openCompany(page, CARD_COMPANY);
  const row = page.getByRole('row').filter({ hasText: 'Trabajadora Visual' });
  for (const label of ['Código', 'Persona', 'Papel', 'Acceso']) await expect(row.locator(`td[data-label="${label}"]`)).toBeVisible();
  await expect(row).toContainText('visual@demo.invalid');
  await expect(row).toContainText('Trabaja con su IA');
  await expect(row).toContainText('Activo');
  await expectNoHorizontalScroll(page);
  const clipped = await row.locator('td').evaluateAll((cells) => cells.filter((c) => c.getBoundingClientRect().right > window.innerWidth).length);
  expect(clipped).toBe(0);
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
