import { readFileSync } from 'node:fs';
import { expect, test } from '@playwright/test';

const state = JSON.parse(readFileSync(process.env.DEMO_STATE ?? '', 'utf8')) as {
  cardTitle: string;
  admin: { email: string; password: string };
};

test('paso 8: el administrador ve la ficha pendiente en Revisión y la aprueba', async ({ page }) => {
  await page.goto(process.env.DEMO_PANEL_URL ?? '');
  await page.getByLabel('Correo').fill(state.admin.email);
  await page.getByLabel('Contraseña').fill(state.admin.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByRole('button', { name: 'Abrir Empresa A' }).click();
  await page.getByRole('link', { name: 'Revisión' }).click();
  await expect(page.getByRole('heading', { name: state.cardTitle })).toBeVisible();
  await page.getByRole('button', { name: 'Aprobar', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Ficha aprobada');
  await expect(page.getByText('No hay fichas pendientes.')).toBeVisible();
  console.log('✓ Paso 8: el administrador aprobó la ficha desde Revisión en el panel');
});

test('paso 8b: el administrador marca la póliza antigua como sustituida por la renovada', async ({ page }) => {
  await page.goto(process.env.DEMO_PANEL_URL ?? '');
  await page.getByLabel('Correo').fill(state.admin.email);
  await page.getByLabel('Contraseña').fill(state.admin.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByRole('button', { name: 'Abrir Empresa A' }).click();
  await page.getByRole('link', { name: 'Documentos' }).click();
  await page.getByRole('button', { name: 'Más acciones de Póliza ficticia', exact: true }).click();
  await page.getByRole('button', { name: 'Marcar como sustituido Póliza ficticia', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Marcar como sustituido' });
  await dialog.getByLabel('Buscar por título').fill('renovada');
  await dialog.getByRole('radio', { name: /^Póliza ficticia renovada/ }).check();
  await expect(dialog.getByText('“Póliza ficticia” quedará guardado, pero Odín lo dará como sustituido por “Póliza ficticia renovada” y lo pondrá detrás en las búsquedas.')).toBeVisible();
  await dialog.getByRole('button', { name: 'Marcar como sustituido' }).click();
  const row = page.getByRole('row').filter({ hasText: 'Sustituido' });
  await expect(row).toContainText('por Póliza ficticia renovada');
  console.log('✓ Paso 8b: el administrador marcó la póliza antigua como sustituida desde Documentos en el panel');
});
