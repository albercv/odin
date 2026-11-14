import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { UploadByLink } from './UploadByLink';
import { mockFetch, type Routes } from '../mockApi';

afterEach(() => vi.restoreAllMocks());

const info = 'GET /v1/upload-links/tok';
const post = 'POST /v1/upload-links/tok/documents';
const ok = { target: 'CASE', caseTitle: 'Expte 2024/017', projectName: 'Los Almendros', expiresAt: '2026-11-14T10:00:00Z' };
const show = (r: Routes) => { const calls = mockFetch(r); render(<UploadByLink token="tok" />); return calls; };
const files = (...names: string[]) => names.map((n) => new File(['x'], n, { type: 'image/jpeg' }));

test('showsCaseAndProjectBeforeUploading', async () => {
  show({ [info]: () => ok });
  expect(await screen.findByText('Expte 2024/017')).toBeInTheDocument();
  expect(screen.getByText('Caso')).toBeInTheDocument();
  expect(screen.getByText('Proyecto')).toBeInTheDocument();
  expect(screen.getByText('Los Almendros')).toBeInTheDocument();
  expect(screen.getByText(/caduca/i)).toBeInTheDocument();
});

test('uploadsSeveralFilesWithAStatusEach', async () => {
  const calls = show({ [info]: () => ok, [post]: (body) => ({ filename: ((body as FormData).get('file') as File).name }) });
  await userEvent.upload(await screen.findByLabelText('Elegir ficheros'), files('a.jpg', 'b.jpg'));
  expect(await screen.findByText('recibido: a.jpg')).toBeInTheDocument();
  expect(screen.getByText('recibido: b.jpg')).toBeInTheDocument();
  expect(calls.filter((c) => c.key === post)).toHaveLength(2);
  expect(screen.queryByRole('link')).toBeNull();
  expect(screen.queryByRole('list', { name: /documentos/i })).toBeNull();
});

test('aFailedFileShowsItsMessageAndTheOthersGoOn', async () => {
  let n = 0;
  show({ [info]: () => ok, [post]: () => (++n === 1 ? { status: 409, body: { detail: 'Este enlace ya admite 20 ficheros' } } : { filename: 'b.jpg' }) });
  await userEvent.upload(await screen.findByLabelText('Elegir ficheros'), files('a.jpg', 'b.jpg'));
  expect(await screen.findByText(/a\.jpg.*Este enlace ya admite 20 ficheros/)).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('recibido: b.jpg')).toBeInTheDocument());
});

test('invalidOrExpiredLinkShowsOnlyAMessage', async () => {
  show({ [info]: () => ({ status: 404, body: { detail: 'Este enlace ha caducado o no es válido' } }) });
  expect(await screen.findByRole('alert')).toHaveTextContent('Este enlace ha caducado o no es válido');
  expect(screen.queryByText('Caso')).toBeNull();
  expect(screen.queryByLabelText('Elegir ficheros')).toBeNull();
});

test('tokenIsEncodedInThePaths', async () => {
  const calls = mockFetch({ 'GET /v1/upload-links/a%2Fb%3Fc': () => ok });
  render(<UploadByLink token="a/b?c" />);
  expect(await screen.findByText('Expte 2024/017')).toBeInTheDocument();
  expect(calls[0]?.key).toBe('GET /v1/upload-links/a%2Fb%3Fc');
});

const project = { target: 'PROJECT', projectName: 'Los Almendros', expiresAt: '2026-11-14T10:00:00Z' };

test('titleIsNeutralWhileLoadingAndOnError', async () => {
  show({ [info]: () => ({ status: 404, body: { detail: 'Este enlace ha caducado o no es válido' } }) });
  expect(screen.getByRole('heading', { level: 1, name: 'Subir documentos' })).toBeInTheDocument();
  await screen.findByRole('alert');
  expect(screen.getByRole('heading', { level: 1, name: 'Subir documentos' })).toBeInTheDocument();
});

test('caseTargetKeepsTheCaseTitle', async () => {
  show({ [info]: () => ok });
  expect(await screen.findByRole('heading', { level: 1, name: 'Subir documentos al caso' })).toBeInTheDocument();
});

test('projectTargetShowsOnlyTheProject', async () => {
  show({ [info]: () => project });
  expect(await screen.findByRole('heading', { level: 1, name: 'Subir documentos al proyecto' })).toBeInTheDocument();
  expect(screen.getByText('Proyecto')).toBeInTheDocument();
  expect(screen.getByText('Los Almendros')).toBeInTheDocument();
  expect(screen.queryByText('Caso')).toBeNull();
});

test('projectDuplicateSaysItWasAlreadyInTheProject', async () => {
  show({ [info]: () => project, [post]: (body) => ({ filename: ((body as FormData).get('file') as File).name, duplicate: true }) });
  await userEvent.upload(await screen.findByLabelText('Elegir ficheros'), files('a.jpg'));
  expect(await screen.findByText(/a\.jpg.*Ya estaba en el proyecto/)).toBeInTheDocument();
  expect(screen.queryByText('recibido: a.jpg')).toBeNull();
});

test('caseDuplicateKeepsTheNormalText', async () => {
  show({ [info]: () => ok, [post]: () => ({ filename: 'a.jpg', duplicate: true }) });
  await userEvent.upload(await screen.findByLabelText('Elegir ficheros'), files('a.jpg'));
  expect(await screen.findByText('recibido: a.jpg')).toBeInTheDocument();
});
