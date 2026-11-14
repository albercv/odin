import { readFileSync } from 'node:fs';
import AxeBuilder from '@axe-core/playwright';
import { expect, test, type APIRequestContext, type Cookie, type Locator, type Page } from '@playwright/test';

const state = JSON.parse(readFileSync(process.env.DEMO_STATE ?? '', 'utf8')) as { admin: { email: string; password: string } };
const PANEL = process.env.DEMO_PANEL_URL ?? '';
const API = process.env.DEMO_API_URL ?? '';
const COMPANY = 'Empresa listas';
const pad = (n: number): string => String(n).padStart(2, '0');
const range = (n: number): number[] => Array.from({ length: n }, (_, i) => i + 1);
const RARE_DOCUMENT = 'Memoria zafiro'; // la última por título: queda fuera de la primera página y de los candidatos del diálogo
const WITHDRAWN = [1, 2, 3].map((n) => `Informe ${pad(n)}`);

let cookies: Cookie[] = [];

async function send<T>(request: APIRequestContext, method: 'post' | 'put' | 'get', path: string, options: Record<string, unknown> = {}): Promise<T> {
  const response = await request[method](`${API}${path}`, options);
  expect(response.ok(), `${method} ${path} ${response.status()}`).toBeTruthy();
  return (response.status() === 204 ? undefined : await response.json()) as T;
}

async function seedPeople(request: APIRequestContext, base: string): Promise<string> {
  let key = '';
  for (const n of range(12)) {
    const [firstName, lastName, email] = n === 12 ? ['Ximena', 'Zafiro', 'ximena@listas.test'] : [`Persona${pad(n)}`, 'Prueba', `persona-${pad(n)}@listas.test`];
    const added = await send<{ member: { userId: string } }>(request, 'post', `${base}/members`, { data: { firstName, lastName, email, roles: ['WORKER'] } });
    if (n === 12) key = (await send<{ key: string }>(request, 'post', `${base}/members/${added.member.userId}/keys`, { data: { label: 'listas' } })).key;
  }
  return key;
}

async function seedDocuments(request: APIRequestContext, base: string): Promise<void> {
  const titles = [RARE_DOCUMENT, ...range(59).map((n) => `Informe ${pad(n)}`)];
  for (const title of titles) {
    await send(request, 'post', `${base}/documents`, { params: { title }, multipart: { file: { name: `${title}.txt`, mimeType: 'text/plain', buffer: Buffer.from(`Texto ficticio de ${title}.`) } } });
  }
  const list = await send<{ documents: { id: string; title: string }[] }>(request, 'get', `${base}/documents`, { params: { limit: 500 } });
  for (const doc of list.documents.filter((d) => WITHDRAWN.includes(d.title))) await send(request, 'put', `/v1/admin/documents/${doc.id}/withdrawal`);
  await expect.poll(async () => (await send<{ documents: { status: string }[] }>(request, 'get', `${base}/documents`, { params: { q: 'zafiro' } })).documents[0]?.status, { timeout: 60_000 }).toBe('READY');
}

async function seed(request: APIRequestContext): Promise<void> {
  const company = await send<{ id: string }>(request, 'post', '/v1/admin/companies', { data: { name: COMPANY, code: 'LIST' } });
  const base = `/v1/admin/companies/${company.id}`;
  await send(request, 'put', `${base}/capabilities`, { data: { capabilities: ['SAVE_KNOWLEDGE_CARD'] } });
  for (const n of range(22)) await send(request, 'post', `${base}/projects`, { data: { name: `Obra ${pad(n)}` } });
  await send(request, 'post', `${base}/projects`, { data: { name: 'Torre Zafiro' } });
  const key = await seedPeople(request, base);
  for (const n of range(15)) {
    await send(request, 'post', '/v1/cards', { data: { title: `Ficha de prueba ${pad(n)}`, body: `Cuerpo ficticio de la ficha ${pad(n)}.` }, headers: { Authorization: `Bearer ${key}` } });
  }
  await seedDocuments(request, base);
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ browser }) => {
  test.setTimeout(300_000);
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(PANEL);
  await page.getByLabel('Correo').fill(state.admin.email);
  await page.getByLabel('Contraseña').fill(state.admin.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Grupo', exact: true })).toBeVisible();
  await seed(context.request);
  cookies = await context.cookies();
  await context.close();
});

async function open(page: Page, link: string): Promise<void> {
  await page.context().addCookies(cookies);
  await page.goto(PANEL);
  await page.getByRole('link', { name: 'Empresa', exact: true }).click();
  await page.getByRole('combobox', { name: 'Empresa' }).selectOption({ label: COMPANY });
  await expect(page.getByRole('heading', { name: COMPANY, exact: true })).toBeVisible();
  if (link !== 'Empresa') await page.getByRole('link', { name: link }).click();
}

async function expectAccessible(page: Page): Promise<void> {
  // Sin animaciones en curso: a mitad de una transición de color axe mide un contraste intermedio.
  await page.evaluate(() => Promise.allSettled(document.getAnimations().map((a) => a.finished)));
  const { violations } = await new AxeBuilder({ page }).analyze();
  expect(violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')).toEqual([]);
}

const rowsOf = async (scope: Locator): Promise<number> => (await scope.getByRole('row').count()) - 1;
const section = (page: Page, heading: string): Locator => page.locator('section', { has: page.getByRole('heading', { name: heading }) });

test('el administrador de grupo entra con una empresa ya elegida', async ({ page }) => {
  await page.context().addCookies(cookies);
  await page.goto(PANEL);
  await page.getByRole('link', { name: 'Empresa', exact: true }).click();
  await expect(page.getByRole('combobox', { name: 'Empresa' })).not.toHaveValue('');
  await expect(page.getByText('Elige una empresa en el menú.')).toHaveCount(0);
  await expect(page.getByRole('combobox', { name: 'Empresa' }).locator('option[value=""]')).toHaveCount(0);
});

test('Empresa: proyectos, 2 letras no filtran y 3 sí, y las páginas', async ({ page }) => {
  await open(page, 'Empresa');
  const projects = section(page, 'Proyectos');
  const search = projects.getByRole('searchbox', { name: 'Buscar proyectos' });
  await expect(projects.getByRole('navigation', { name: 'Páginas de proyectos' })).toContainText('Página 1 de 3');
  expect(await rowsOf(projects)).toBe(10);
  await expectAccessible(page);
  await search.fill('To');
  await page.waitForTimeout(600);
  await expect(projects.getByRole('navigation', { name: 'Páginas de proyectos' })).toContainText('Página 1 de 3');
  expect(await rowsOf(projects)).toBe(10);
  await search.fill('Tor');
  await expect(projects.getByText('1 resultado', { exact: true })).toBeVisible();
  await expect(projects.getByRole('row').filter({ hasText: 'Torre Zafiro' })).toHaveCount(1);
  await search.fill('Zzz');
  await expect(projects.getByText('Ningún resultado para «Zzz»')).toBeVisible();
  await expectAccessible(page);
  await search.fill('');
  const nav = projects.getByRole('navigation', { name: 'Páginas de proyectos' });
  await expect(nav).toContainText('Página 1 de 3');
  await nav.getByRole('button', { name: 'Siguiente' }).click();
  await nav.getByRole('button', { name: 'Siguiente' }).click();
  await expect(nav).toContainText('Página 3 de 3');
  expect(await rowsOf(projects)).toBe(3);
  await expectAccessible(page);
  await nav.getByRole('button', { name: 'Anterior' }).click();
  await expect(nav).toContainText('Página 2 de 3');
});

test('Empresa: personas por nombre y por correo, y dos páginas', async ({ page }) => {
  await open(page, 'Empresa');
  const people = section(page, 'Personas');
  const search = people.getByRole('searchbox', { name: 'Buscar personas' });
  await expect(people.getByRole('navigation', { name: 'Páginas de personas' })).toContainText('Página 1 de 2');
  await people.getByRole('navigation', { name: 'Páginas de personas' }).getByRole('button', { name: 'Siguiente' }).click();
  await expect(people.getByRole('navigation', { name: 'Páginas de personas' })).toContainText('Página 2 de 2');
  expect(await rowsOf(people)).toBe(2);
  await search.fill('Zafiro');
  await expect(people.getByText('1 resultado', { exact: true })).toBeVisible();
  await search.fill('persona-07@');
  await expect(people.getByText('1 resultado', { exact: true })).toBeVisible();
  await expect(people.getByRole('row').filter({ hasText: 'Persona07' })).toHaveCount(1);
});

test('Documentos: búsqueda en el servidor, tamaño de página y Retirados', async ({ page }) => {
  await open(page, 'Documentos');
  const size = page.getByLabel('Por página');
  const nav = page.getByRole('navigation', { name: 'Páginas de documentos' });
  await expect(nav).toContainText('Página 1 de 3');
  await expect(size).toHaveValue('25');
  expect(await rowsOf(page.locator('main'))).toBe(25);
  await expect(nav).toContainText('Página 1 de 3');
  await expectAccessible(page);
  await size.selectOption('10');
  await expect(nav).toContainText('Página 1 de 6');
  await expect(page.getByRole('row').filter({ hasText: RARE_DOCUMENT })).toHaveCount(0);
  await size.selectOption('50');
  await expect(nav).toContainText('Página 1 de 2');
  await nav.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page.getByRole('row').filter({ hasText: RARE_DOCUMENT })).toHaveCount(1);
  await expectAccessible(page);
  const search = page.getByRole('searchbox', { name: 'Buscar documentos' });
  await search.fill('Informe 02');
  await expect(page.getByText('Ningún resultado para «Informe 02»')).toBeVisible();
  await expectAccessible(page);
  await page.getByRole('button', { name: 'Retirados' }).click();
  await expect(page.getByText('1 resultado', { exact: true })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Informe 02' })).toHaveCount(1);
  await search.fill('');
  await expect.poll(() => rowsOf(page.locator('main'))).toBe(3);
});

test('Documentos con solo el teclado: buscador, tamaño y páginas', async ({ page }) => {
  await open(page, 'Documentos');
  await expect(page.getByRole('navigation', { name: 'Páginas de documentos' })).toContainText('Página 1 de 3');
  await page.getByRole('searchbox', { name: 'Buscar documentos' }).focus();
  await page.keyboard.type('Informe 1');
  await expect(page.getByText('10 resultados')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('navigation', { name: 'Páginas de documentos' })).toContainText('Página 1 de 3');
  await page.getByLabel('Por página').focus();
  await page.keyboard.type('1'); // por letra: elige «10» sin abrir el desplegable
  await expect(page.getByLabel('Por página')).toHaveValue('10');
  const next = page.getByRole('navigation', { name: 'Páginas de documentos' }).getByRole('button', { name: 'Siguiente' });
  await next.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('navigation', { name: 'Páginas de documentos' })).toContainText('Página 2 de 6');
});

test('Sustituir encuentra un candidato que no está en la página', async ({ page }) => {
  await open(page, 'Documentos');
  await page.getByRole('button', { name: 'Más acciones de Informe 04', exact: true }).click();
  await page.getByRole('button', { name: 'Marcar como sustituido Informe 04', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Marcar como sustituido' });
  await expect(dialog.getByText('Escribe al menos 3 letras del título para afinar')).toBeVisible();
  await expect(dialog.getByRole('radio', { name: new RegExp(`^${RARE_DOCUMENT}`) })).toHaveCount(0);
  await expectAccessible(page);
  await dialog.getByRole('searchbox', { name: 'Buscar por título' }).fill('zafiro');
  await expect(dialog.getByRole('radio', { name: new RegExp(`^${RARE_DOCUMENT}`) })).toBeVisible();
  await dialog.getByRole('radio', { name: new RegExp(`^${RARE_DOCUMENT}`) }).check();
  await expectAccessible(page);
  await dialog.getByRole('searchbox', { name: 'Buscar por título' }).fill('qqqq');
  await expect(dialog.getByText('Ningún documento listo coincide con la búsqueda.')).toBeVisible();
  await expectAccessible(page);
});

test('Revisión: 15 fichas en dos páginas y el número del menú baja al aprobar', async ({ page }) => {
  await open(page, 'Revisión');
  const nav = page.getByRole('navigation', { name: 'Páginas de fichas' });
  const badge = page.getByRole('link', { name: /Revisión/ });
  await expect(badge).toContainText('15 fichas pendientes');
  await expect(nav).toContainText('Página 1 de 2');
  await expectAccessible(page);
  await page.getByRole('searchbox', { name: 'Buscar fichas' }).fill('prueba 15');
  await expect(page.getByText('1 resultado', { exact: true })).toBeVisible();
  await page.getByRole('searchbox', { name: 'Buscar fichas' }).fill('');
  await nav.getByRole('button', { name: 'Siguiente' }).click();
  await expect(nav).toContainText('Página 2 de 2');
  await expectAccessible(page);
  await page.getByRole('button', { name: 'Aprobar', exact: true }).first().click();
  await expect(badge).toContainText('14 fichas pendientes');
});
