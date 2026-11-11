import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { expect, test, type Locator, type Page } from '@playwright/test';

const state = JSON.parse(readFileSync(process.env.DEMO_STATE ?? '', 'utf8')) as { admin: { email: string; password: string }; sessionCookie: string };

const COMPANY = 'Empresa E2E';
const WORKER = { name: 'Trabajador E2E', email: 'e2e-worker@demo.test', renamed: 'Trabajador Renombrado' };
const COMPANY_ADMIN = { name: 'Admin E2E', email: 'e2e-admin@demo.test', password: `clave-${randomUUID()}`, newPassword: `clave-${randomUUID()}`, resetPassword: `clave-${randomUUID()}` };
const PROJECT = { name: 'Proyecto E2E', renamed: 'Proyecto E2E renombrado' };

const usersSection = (page: Page): Locator => page.locator('section', { has: page.getByRole('heading', { name: 'Personas' }) });
const projectsSection = (page: Page): Locator => page.locator('section', { has: page.getByRole('heading', { name: 'Proyectos' }) });
const row = (section: Locator, text: string): Locator => section.getByRole('row').filter({ hasText: text }).first();

async function openActions(section: Locator, name: string): Promise<void> {
  const toggle = section.getByRole('button', { name: `Más acciones de ${name}`, exact: true });
  if ((await toggle.getAttribute('aria-expanded')) !== 'true') await toggle.click();
}

async function confirmDialog(page: Page, message: string, confirmLabel: string): Promise<void> {
  const dialog = page.getByRole('dialog', { name: message });
  await dialog.getByRole('button', { name: confirmLabel, exact: true }).click();
  await expect(dialog).toHaveCount(0);
}

async function logIn(page: Page, email: string, password: string): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Entra en el panel' })).toBeVisible();
  await page.getByLabel('Correo').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

async function createUser(page: Page, user: { name: string; email?: string; password?: string; role?: 'WORKER' | 'COMPANY_ADMIN' }): Promise<void> {
  await page.getByRole('button', { name: 'Añadir persona', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Añadir persona' });
  const form = dialog.locator('form');
  await form.getByLabel('Rol').selectOption(user.role ?? 'WORKER');
  await form.getByLabel('Nombre', { exact: true }).fill(user.name);
  if (user.email) await form.getByLabel('Correo').fill(user.email);
  if (user.password) await form.getByLabel('Contraseña').fill(user.password);
  await form.getByRole('button', { name: 'Crear usuario' }).click();
  await expect(dialog).toHaveCount(0);
}

async function createCompanyAndOpenIt(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Nueva empresa' }).click();
  const dialog = page.getByRole('dialog', { name: 'Nueva empresa' });
  await dialog.getByLabel('Nombre de la empresa').fill(COMPANY);
  await dialog.getByRole('button', { name: 'Crear empresa' }).click();
  await page.getByRole('button', { name: `Abrir ${COMPANY}` }).click();
  await expect(page.getByRole('heading', { name: COMPANY })).toBeVisible();
  console.log('✓ Admin 1: empresa «Empresa E2E» creada y abierta');
}

async function workerWithoutEmail(page: Page): Promise<void> {
  await createUser(page, { name: WORKER.name });
  await expect(row(usersSection(page), WORKER.name)).toContainText('Sin correo');
  console.log('✓ Admin 2: trabajador sin correo ni contraseña, listado como «Sin correo»');
}

async function companyAdminWithPassword(page: Page): Promise<void> {
  await createUser(page, { name: COMPANY_ADMIN.name, email: COMPANY_ADMIN.email, password: COMPANY_ADMIN.password, role: 'COMPANY_ADMIN' });
  await expect(row(usersSection(page), COMPANY_ADMIN.name)).toContainText(COMPANY_ADMIN.email);
  console.log('✓ Admin 3: administrador de empresa creado con correo y contraseña');
}

async function deactivateAndActivate(page: Page): Promise<void> {
  const users = usersSection(page);
  const worker = row(users, WORKER.name);
  await openActions(users, WORKER.name);
  await users.getByRole('button', { name: `Desactivar ${WORKER.name}`, exact: true }).click();
  await expect(worker).toContainText('Desactivado');
  await users.getByRole('button', { name: `Activar ${WORKER.name}`, exact: true }).click();
  await expect(worker).toContainText('Activo');
  await expect(worker).not.toContainText('Desactivado');
  console.log('✓ Admin 4: trabajador desactivado y activado de nuevo');
}

async function editNameAndEmail(page: Page): Promise<void> {
  const users = usersSection(page);
  await openActions(users, WORKER.name);
  await users.getByRole('button', { name: `Editar ${WORKER.name}`, exact: true }).click();
  const form = users.locator('form').filter({ has: page.getByRole('button', { name: `Guardar ${WORKER.name}` }) });
  await form.getByLabel('Nombre').fill(WORKER.renamed);
  await form.getByLabel('Correo').fill(COMPANY_ADMIN.email);
  await form.getByRole('button', { name: `Guardar ${WORKER.name}` }).click();
  await expect(form.getByRole('alert')).toHaveText('Ya existe un usuario con ese correo');
  await form.getByLabel('Correo').fill(WORKER.email);
  await form.getByRole('button', { name: `Guardar ${WORKER.name}` }).click();
  await expect(row(users, WORKER.renamed)).toContainText(WORKER.email);
  console.log('✓ Admin 5: nombre y correo editados; el correo repetido da «Ya existe un usuario con ese correo»');
}

async function giveKeyShownOnce(page: Page): Promise<void> {
  const users = usersSection(page);
  await openActions(users, WORKER.renamed);
  await users.getByRole('button', { name: `Claves de ${WORKER.renamed}`, exact: true }).click();
  await users.getByLabel('Etiqueta de la clave').fill('clave e2e');
  await users.getByRole('button', { name: 'Dar clave' }).click();
  const issued = page.getByRole('dialog', { name: 'Clave nueva' });
  await expect(issued).toContainText('No se volverá a mostrar');
  await expect(issued.getByRole('button', { name: 'Cerrar' })).toHaveCount(0);
  const key = (await issued.locator('code').textContent()) ?? '';
  expect(key.length).toBeGreaterThan(20);
  await issued.getByRole('button', { name: 'Entendido' }).click();
  await expect(page.getByText(key)).toHaveCount(0);
  console.log('✓ Admin 6: clave personal entregada una sola vez');
}

async function removeWorkerAndAddThemBack(page: Page): Promise<void> {
  const users = usersSection(page);
  await openActions(users, WORKER.renamed);
  await users.getByRole('button', { name: `Quitar ${WORKER.renamed}`, exact: true }).click();
  await confirmDialog(page, `¿Quitar a ${WORKER.renamed} de la empresa?`, 'Quitar');
  await expect(row(users, WORKER.renamed)).toHaveCount(0);
  await createUser(page, { name: 'Otro nombre E2E', email: WORKER.email });
  await expect(row(users, WORKER.renamed)).toContainText(WORKER.email);
  console.log('✓ Admin 7: trabajador quitado de la empresa y vuelto a añadir por su correo, con su nombre de siempre');
}

async function projectLifecycle(page: Page): Promise<void> {
  const projects = projectsSection(page);
  const add = async (name: string) => {
    await projects.getByRole('button', { name: 'Nuevo proyecto' }).click();
    await page.getByLabel('Nombre del proyecto').fill(name);
    await page.getByRole('button', { name: 'Crear proyecto' }).click();
  };
  await add(PROJECT.name);
  await expect(row(projects, PROJECT.name)).toBeVisible();
  await openActions(projects, PROJECT.name);
  await projects.getByRole('button', { name: `Renombrar ${PROJECT.name}`, exact: true }).click();
  await projects.getByLabel('Nombre', { exact: true }).fill(PROJECT.renamed);
  await projects.getByRole('button', { name: `Guardar ${PROJECT.name}`, exact: true }).click();
  await expect(row(projects, PROJECT.renamed)).toBeVisible();
  await add(PROJECT.renamed);
  const dialog = page.getByRole('dialog', { name: 'Nuevo proyecto' });
  await expect(dialog.getByRole('alert')).toHaveText('Ya existe un proyecto con ese nombre');
  await dialog.getByRole('button', { name: 'Cerrar' }).click();
  await openActions(projects, PROJECT.renamed);
  await projects.getByRole('button', { name: `Borrar ${PROJECT.renamed}`, exact: true }).click();
  await confirmDialog(page, `¿Borrar el proyecto ${PROJECT.renamed}? Sus documentos se conservan.`, 'Borrar');
  await expect(row(projects, PROJECT.renamed)).toHaveCount(0);
  console.log('✓ Admin 8: proyecto creado, renombrado, duplicado rechazado y borrado');
}

async function resetCompanyAdminPasswordAndEnter(page: Page): Promise<void> {
  const users = usersSection(page);
  await openActions(users, COMPANY_ADMIN.name);
  await users.getByRole('button', { name: `Restablecer contraseña de ${COMPANY_ADMIN.name}`, exact: true }).click();
  await users.getByLabel('Nueva contraseña').fill(COMPANY_ADMIN.resetPassword);
  await users.getByRole('button', { name: `Guardar contraseña de ${COMPANY_ADMIN.name}` }).click();
  await expect(users.getByLabel('Nueva contraseña')).toHaveCount(0);
  await expect(page.getByRole('status')).toHaveText('Contraseña restablecida');
  await page.getByRole('button', { name: 'Salir' }).click();
  await logIn(page, COMPANY_ADMIN.email, COMPANY_ADMIN.resetPassword);
  await expect(page.getByRole('heading', { name: COMPANY })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Empresas' })).toHaveCount(0);
  console.log('✓ Admin 9: contraseña restablecida; el administrador de empresa entra y solo ve la suya');
}

async function changeOwnPassword(page: Page): Promise<void> {
  await page.getByRole('link', { name: /Mi cuenta/ }).click();
  await page.getByLabel('Contraseña actual').fill(COMPANY_ADMIN.resetPassword);
  await page.getByLabel('Contraseña nueva').fill(COMPANY_ADMIN.newPassword);
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await expect(page.getByText('Contraseña cambiada. Entra de nuevo.')).toBeVisible();
  await logIn(page, COMPANY_ADMIN.email, COMPANY_ADMIN.newPassword);
  await expect(page.getByRole('heading', { name: COMPANY })).toBeVisible();
  console.log('✓ Admin 10: contraseña propia cambiada desde «Mi cuenta» y entrada con la nueva');
}

// El login admite 5 intentos por minuto y por IP; entre prepare, demo.spec, este fichero y confirm ya hay varios.
// El administrador del grupo reutiliza la sesión de prepare (sin login) y los pasos que entran de nuevo van al final.
test('administración del panel de extremo a extremo', async ({ page, context }) => {
  const [name, value] = state.sessionCookie.split('=');
  await context.addCookies([{ name: name ?? '', value: value ?? '', url: process.env.DEMO_PANEL_URL ?? '' }]);
  await page.goto(process.env.DEMO_PANEL_URL ?? '');
  await createCompanyAndOpenIt(page);
  await workerWithoutEmail(page);
  await companyAdminWithPassword(page);
  await deactivateAndActivate(page);
  await editNameAndEmail(page);
  await giveKeyShownOnce(page);
  await removeWorkerAndAddThemBack(page);
  await projectLifecycle(page);
  await resetCompanyAdminPasswordAndEnter(page);
  await changeOwnPassword(page);
});
