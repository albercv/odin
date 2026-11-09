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
  await expect(page.getByText('No hay fichas pendientes.')).toBeVisible();
  console.log('✓ Paso 8: el administrador aprobó la ficha desde Revisión en el panel');
});
