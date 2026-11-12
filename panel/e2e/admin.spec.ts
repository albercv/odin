import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { expect, test, type Browser, type Locator, type Page } from '@playwright/test';

const state = JSON.parse(readFileSync(process.env.DEMO_STATE ?? '', 'utf8')) as { admin: { email: string; password: string }; sessionCookie: string };

const COMPANY = 'Empresa E2E';
const COMPANY_CODE = 'E2E';
const SECOND_COMPANY = 'Empresa E2E bis';
const PERSON_CODE = new RegExp(`^[2-9A-HJKMNP-Z]{4}-${COMPANY_CODE}$`);
const PROMOTED = { name: 'Ascendida E2E', email: 'e2e-promoted@demo.test', password: `clave-${randomUUID()}` };
const CLOSED_PROJECT = 'Proyecto cerrado E2E';
const PROJECT_PEOPLE = ['Paula Uno', 'Pedro Dos', 'Pilar Tres'];
const WORKER = { name: 'Trabajador E2E', email: 'e2e-worker@demo.test', renamed: 'Trabajador Renombrado' };
const COMPANY_ADMIN = { name: 'Admin E2E', email: 'e2e-admin@demo.test', password: `clave-${randomUUID()}`, newPassword: `clave-${randomUUID()}`, resetPassword: `clave-${randomUUID()}` };
const FIRST_GROUP_ADMIN = 'Administrador Demo';
const SECOND_GROUP_ADMIN = { name: 'Segundo Relevo', email: 'e2e-relevo@demo.test', password: `clave-${randomUUID()}` };
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

/** Names are written as «Nombre Apellidos»: the first word is the first name, the rest are the surnames. */
const firstNameOf = (name: string): string => name.split(' ')[0] ?? name;
const lastNamesOf = (name: string): string => name.split(' ').slice(1).join(' ');

type NewUser = { name: string; email?: string; password?: string; role?: 'WORKER' | 'COMPANY_ADMIN'; alsoWorker?: boolean; projects?: string[] };

async function createUser(page: Page, user: NewUser): Promise<void> {
  await page.getByRole('button', { name: 'Añadir persona', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Añadir persona' });
  const form = dialog.locator('form');
  if (user.role === 'COMPANY_ADMIN') {
    await form.getByLabel('Administra la empresa').check();
    if (!user.alsoWorker) await form.getByLabel('Trabaja con su IA').uncheck();
  }
  for (const project of user.projects ?? []) await form.getByLabel(project, { exact: true }).check();
  await form.getByLabel('Nombre', { exact: true }).fill(firstNameOf(user.name));
  await form.getByLabel('Apellidos').fill(lastNamesOf(user.name));
  if (user.email) await form.getByLabel('Correo').fill(user.email);
  if (user.password) await form.getByLabel('Contraseña').fill(user.password);
  await form.getByRole('button', { name: 'Añadir persona' }).click();
  await expect(dialog).toHaveCount(0);
}

async function openCompany(page: Page, name: string): Promise<void> {
  await page.getByRole('button', { name: `Abrir ${name}`, exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}

async function createCompany(page: Page, name: string, code: string): Promise<void> {
  await page.getByRole('button', { name: 'Nueva empresa' }).click();
  const dialog = page.getByRole('dialog', { name: 'Nueva empresa' });
  await dialog.getByLabel('Nombre de la empresa').fill(name);
  await dialog.getByLabel('Código').fill(code);
  await dialog.getByRole('button', { name: 'Crear empresa' }).click();
}

async function createCompanyAndOpenIt(page: Page): Promise<void> {
  await createCompany(page, COMPANY, COMPANY_CODE);
  await openCompany(page, COMPANY);
  console.log('✓ Admin 1: empresa «Empresa E2E» creada y abierta');
}

async function giveKey(page: Page, name: string, label: string): Promise<void> {
  const users = usersSection(page);
  await openActions(users, name);
  await users.getByRole('button', { name: `Claves de ${name}`, exact: true }).click();
  await users.getByLabel('Etiqueta de la clave').fill(label);
  await users.getByRole('button', { name: 'Dar clave' }).click();
  const issued = page.getByRole('dialog', { name: 'Clave nueva' });
  await expect(issued.locator('code')).toContainText('odin_');
  await issued.getByRole('button', { name: 'Entendido' }).click();
  await expect(users.getByText(`${label} (`)).toBeVisible();
}

async function workerWithoutEmail(page: Page): Promise<void> {
  await createUser(page, { name: WORKER.name });
  await expect(row(usersSection(page), WORKER.name)).toContainText('sin correo');
  await expect(row(usersSection(page), WORKER.name).locator('code')).toHaveText(PERSON_CODE);
  console.log('✓ Admin 2: trabajador sin correo ni contraseña, listado como «sin correo» y con su código completo');
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
  await form.getByLabel('Nombre').fill(firstNameOf(WORKER.renamed));
  await form.getByLabel('Apellidos').fill(lastNamesOf(WORKER.renamed));
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
  await confirmDialog(page, `¿Quitar a ${WORKER.renamed} de esta empresa? Sus claves y sus proyectos de esta empresa dejarán de valer.`, 'Quitar');
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
  await expect(page.getByRole('combobox', { name: 'Empresa' })).toBeVisible();
  await page.getByRole('combobox', { name: 'Empresa' }).selectOption({ label: COMPANY });
  await expect(page.getByRole('heading', { name: COMPANY, exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Grupo', exact: true })).toHaveCount(0);
  console.log('✓ Admin 9: contraseña restablecida; quien administra dos empresas entra, ve el selector y no ve el resto del grupo');
}

async function changeOwnPassword(page: Page): Promise<void> {
  await page.getByRole('link', { name: /Mi cuenta/ }).click();
  await page.getByLabel('Contraseña actual').fill(COMPANY_ADMIN.resetPassword);
  await page.getByLabel('Contraseña nueva').fill(COMPANY_ADMIN.newPassword);
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await expect(page.getByText('Contraseña cambiada. Entra de nuevo.')).toBeVisible();
  await logIn(page, COMPANY_ADMIN.email, COMPANY_ADMIN.newPassword);
  await expect(page.getByRole('combobox', { name: 'Empresa' })).toBeVisible();
  console.log('✓ Admin 10: contraseña propia cambiada desde «Mi cuenta» y entrada con la nueva');
}

async function samePersonInSecondCompany(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Grupo', exact: true }).click();
  await createCompany(page, SECOND_COMPANY, 'E2B');
  await openCompany(page, SECOND_COMPANY);
  await createUser(page, { name: COMPANY_ADMIN.name, email: COMPANY_ADMIN.email, password: COMPANY_ADMIN.password, role: 'COMPANY_ADMIN', alsoWorker: true });
  await expect(page.getByText('Esta persona ya existía en el grupo: se ha añadido a esta empresa')).toBeVisible();
  const person = row(usersSection(page), COMPANY_ADMIN.name);
  await expect(person).toContainText('Administra la empresa · Trabaja con su IA');
  await expect(person.locator('code')).toHaveText(/^[2-9A-HJKMNP-Z]{4}-E2B$/);
  await giveKey(page, COMPANY_ADMIN.name, 'clave segunda empresa');
  await page.getByRole('combobox', { name: 'Empresa' }).selectOption({ label: COMPANY });
  await expect(page.getByRole('heading', { name: COMPANY, exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Grupo', exact: true })).toBeVisible();
  console.log('✓ Admin 11: la misma persona en una segunda empresa (aviso de que ya existía), con los dos papeles y una clave en ella');
}

async function changeRolesOfCompanyAdmin(page: Page): Promise<void> {
  const users = usersSection(page);
  await openActions(users, COMPANY_ADMIN.name);
  await users.getByRole('button', { name: `Papeles de ${COMPANY_ADMIN.name}`, exact: true }).click();
  await users.getByLabel('Trabaja con su IA').check();
  await users.getByRole('button', { name: `Guardar papeles de ${COMPANY_ADMIN.name}`, exact: true }).click();
  await expect(row(users, COMPANY_ADMIN.name)).toContainText('Administra la empresa · Trabaja con su IA');
  console.log('✓ Admin 12: papeles cambiados; la persona administra y trabaja a la vez en la primera empresa');
}

async function closedProjectWithMembers(page: Page): Promise<void> {
  const projects = projectsSection(page);
  await projects.getByRole('button', { name: 'Nuevo proyecto' }).click();
  await page.getByLabel('Nombre del proyecto').fill(CLOSED_PROJECT);
  await page.getByRole('button', { name: 'Crear proyecto' }).click();
  await expect(row(projects, CLOSED_PROJECT)).toContainText('Nadie todavía');
  for (const name of PROJECT_PEOPLE) await createUser(page, { name, projects: [CLOSED_PROJECT] });
  await expect(row(projects, CLOSED_PROJECT)).toContainText('3 miembros');
  console.log('✓ Admin 13: proyecto cerrado y tres personas dadas de alta con él');
}

async function openProjectMembers(page: Page): Promise<Locator> {
  const projects = projectsSection(page);
  await openActions(projects, CLOSED_PROJECT);
  const toggle = projects.getByRole('button', { name: `Miembros de ${CLOSED_PROJECT}`, exact: true });
  if ((await toggle.getAttribute('aria-expanded')) !== 'true') await toggle.click();
  return page.getByRole('region', { name: `Miembros de ${CLOSED_PROJECT}` });
}

async function removeFromProjectAndSeeAccesses(page: Page): Promise<void> {
  const [first = '', second = ''] = PROJECT_PEOPLE;
  const users = usersSection(page);
  await openActions(users, first);
  await users.getByRole('button', { name: `Accesos de ${first}`, exact: true }).click();
  await expect(page.getByRole('region', { name: `Accesos de ${first}` }).getByRole('list', { name: 'Proyectos asignados' })).toContainText(CLOSED_PROJECT);
  await openActions(users, first);
  await users.getByRole('button', { name: `Accesos de ${first}`, exact: true }).click();
  const members = await openProjectMembers(page);
  await expect(members.getByRole('list', { name: 'Miembros del proyecto' })).toContainText(second);
  await members.getByRole('button', { name: `Quitar ${first}`, exact: true }).click();
  await expect(row(projectsSection(page), CLOSED_PROJECT)).toContainText('2 miembros');
  await openActions(users, first);
  await users.getByRole('button', { name: `Accesos de ${first}`, exact: true }).click();
  await expect(page.getByRole('region', { name: `Accesos de ${first}` })).toContainText('Sin proyectos asignados.');
  console.log('✓ Admin 14: la persona con proyecto lo ve en sus «Accesos» y entre sus miembros; quitada desde el proyecto, desaparece de sus «Accesos»');
}

async function promoteWorkerWithoutPassword(page: Page, browser: Browser): Promise<void> {
  await createUser(page, { name: PROMOTED.name, email: PROMOTED.email });
  const users = usersSection(page);
  await openActions(users, PROMOTED.name);
  await users.getByRole('button', { name: `Papeles de ${PROMOTED.name}`, exact: true }).click();
  await users.getByLabel('Administra la empresa').check();
  await users.getByLabel('Contraseña', { exact: true }).fill(PROMOTED.password);
  await users.getByRole('button', { name: `Guardar papeles de ${PROMOTED.name}`, exact: true }).click();
  await expect(row(users, PROMOTED.name)).toContainText('Administra la empresa');
  const context = await browser.newContext();
  const own = await context.newPage();
  await own.goto(process.env.DEMO_PANEL_URL ?? '');
  await logIn(own, PROMOTED.email, PROMOTED.password);
  await expect(own.getByRole('heading', { name: COMPANY, exact: true })).toBeVisible();
  await context.close();
  console.log('✓ Admin 15: trabajadora sin contraseña ascendida a administradora; el panel pide la contraseña y ella entra con ella');
}

async function deleteProjectMember(page: Page): Promise<void> {
  const second = PROJECT_PEOPLE[1] ?? '';
  const users = usersSection(page);
  await openActions(users, second);
  await users.getByRole('button', { name: `Borrar ${second}`, exact: true }).click();
  await confirmDialog(page, `¿Borrar a ${second}? Desaparece de todas las empresas y de sus proyectos, y sus claves dejan de valer. No se puede deshacer.`, 'Borrar');
  await expect(row(users, second)).toHaveCount(0);
  await expect(row(projectsSection(page), CLOSED_PROJECT)).toContainText('1 miembro');
  const members = await openProjectMembers(page);
  await expect(members.getByRole('list', { name: 'Miembros del proyecto' })).not.toContainText(second);
  console.log('✓ Admin 16: una persona miembro de un proyecto, borrada por el administrador del grupo: «Quién lo ve» baja en uno y ya no figura');
}

const groupAdminsSection = (page: Page): Locator => page.locator('section', { has: page.getByRole('heading', { name: 'Administradores del grupo' }) });

async function openGroupAdmins(page: Page, email: string, password: string): Promise<void> {
  await page.goto(process.env.DEMO_PANEL_URL ?? '');
  await logIn(page, email, password);
  await page.getByRole('link', { name: 'Administración del grupo' }).click();
  await expect(page.getByRole('heading', { name: 'Administración del grupo', level: 1 })).toBeVisible();
}

async function addSecondGroupAdmin(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Añadir administrador del grupo' }).click();
  const dialog = page.getByRole('dialog', { name: 'Añadir administrador del grupo' });
  await dialog.getByLabel('Nombre', { exact: true }).fill(firstNameOf(SECOND_GROUP_ADMIN.name));
  await dialog.getByLabel('Apellidos').fill(lastNamesOf(SECOND_GROUP_ADMIN.name));
  await dialog.getByLabel('Correo').fill(SECOND_GROUP_ADMIN.email);
  await dialog.getByLabel('Contraseña').fill(SECOND_GROUP_ADMIN.password);
  await dialog.getByRole('button', { name: 'Añadir administrador' }).click();
  await expect(dialog).toHaveCount(0);
  await expect(row(groupAdminsSection(page), SECOND_GROUP_ADMIN.name)).toContainText(SECOND_GROUP_ADMIN.email);
  await expect(row(groupAdminsSection(page), SECOND_GROUP_ADMIN.name).locator('code')).toHaveText(/^[2-9A-HJKMNP-Z]{4}$/);
}

async function toggleGroupAdmin(page: Page, name: string, action: 'Desactivar' | 'Activar', message: string): Promise<void> {
  const admins = groupAdminsSection(page);
  await openActions(admins, name);
  await admins.getByRole('button', { name: `${action} ${name}`, exact: true }).click();
  await confirmDialog(page, message, action);
}

// Al final, porque desactivar al primero cierra su sesión: demo.spec.ts y visual.spec.ts entran con él de nuevo.
async function groupAdminHandover(browser: Browser): Promise<void> {
  const first = await browser.newContext();
  const firstPage = await first.newPage();
  await openGroupAdmins(firstPage, state.admin.email, state.admin.password);
  await addSecondGroupAdmin(firstPage);
  await first.close();
  const second = await browser.newContext();
  const page = await second.newPage();
  await openGroupAdmins(page, SECOND_GROUP_ADMIN.email, SECOND_GROUP_ADMIN.password);
  await expect(page.getByRole('button', { name: `Más acciones de ${SECOND_GROUP_ADMIN.name}` })).toHaveCount(0);
  await toggleGroupAdmin(page, FIRST_GROUP_ADMIN, 'Desactivar', `¿Desactivar a ${FIRST_GROUP_ADMIN} en todo el grupo? No podrá usar Odín en ninguna empresa hasta que se reactive. Sus claves y sus proyectos se conservan.`);
  await expect(row(groupAdminsSection(page), FIRST_GROUP_ADMIN)).toContainText('Desactivado');
  const locked = await browser.newContext();
  const attempt = await locked.newPage();
  await attempt.goto(process.env.DEMO_PANEL_URL ?? '');
  await logIn(attempt, state.admin.email, state.admin.password);
  await expect(attempt.getByRole('alert')).toBeVisible();
  await expect(attempt.getByRole('heading', { name: 'Entra en el panel' })).toBeVisible();
  await locked.close();
  await toggleGroupAdmin(page, FIRST_GROUP_ADMIN, 'Activar', `¿Activar a ${FIRST_GROUP_ADMIN}? Vuelve a entrar en el panel y sus claves vuelven a valer.`);
  await expect(row(groupAdminsSection(page), FIRST_GROUP_ADMIN)).toContainText('Activo');
  await second.close();
  console.log('✓ Admin 18: relevo del administrador del grupo: el segundo entra, desactiva al primero (que ya no entra) y lo reactiva');
}

const CASE_FILES = ['caso-e2e-1', 'caso-e2e-2'];

async function uploadCaseFilesAndMarkSelection(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Documentos', exact: true }).click();
  await page.getByRole('button', { name: 'Subir documento', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Subir documento' });
  await dialog.getByLabel('Fichero').setInputFiles(CASE_FILES.map((name) => ({ name: `${name}.txt`, mimeType: 'text/plain', buffer: Buffer.from(`Ficha de caso ficticia ${name}: filtración en la azotea, resuelta con una lámina nueva.`) })));
  await dialog.getByLabel('Son fichas de caso').check();
  await dialog.getByRole('button', { name: 'Subir', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Se subieron 2 documentos.');
  const caseLabel = (name: string) => page.getByRole('row').filter({ hasText: name }).getByText('Caso', { exact: true });
  for (const name of CASE_FILES) await expect(caseLabel(name)).toBeVisible();
  for (const name of CASE_FILES) await page.getByRole('checkbox', { name: `Seleccionar ${name}`, exact: true }).check();
  await page.getByRole('button', { name: 'Quitar marca de caso', exact: true }).click();
  for (const name of CASE_FILES) await expect(caseLabel(name)).toHaveCount(0);
  await page.getByRole('checkbox', { name: `Seleccionar ${CASE_FILES[1]}`, exact: true }).check();
  await page.getByRole('button', { name: 'Marcar como caso', exact: true }).click();
  await expect(caseLabel(CASE_FILES[1] ?? '')).toBeVisible();
  await expect(caseLabel(CASE_FILES[0] ?? '')).toHaveCount(0);
  await page.getByRole('link', { name: 'Empresa', exact: true }).click();
  console.log('✓ Admin 17: dos ficheros subidos a la vez como fichas de caso; la selección se desmarca y se vuelve a marcar por lotes');
}

// El login admite 5 intentos por minuto y por IP; entre prepare, demo.spec, este fichero y confirm ya hay varios.
// El administrador del grupo reutiliza la sesión de prepare (sin login) y los pasos que entran de nuevo van al final.
test('administración del panel de extremo a extremo', async ({ page, context, browser }) => {
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
  await samePersonInSecondCompany(page);
  await changeRolesOfCompanyAdmin(page);
  await closedProjectWithMembers(page);
  await removeFromProjectAndSeeAccesses(page);
  await promoteWorkerWithoutPassword(page, browser);
  await deleteProjectMember(page);
  await uploadCaseFilesAndMarkSelection(page);
  await resetCompanyAdminPasswordAndEnter(page);
  await changeOwnPassword(page);
  await groupAdminHandover(browser);
});
