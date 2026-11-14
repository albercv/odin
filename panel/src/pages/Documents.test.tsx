import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { Documents } from './Documents';
import { mockFetch, type Routes } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

const list = 'GET /v1/admin/companies/c1/documents?offset=0&limit=25';
const projects = 'GET /v1/admin/companies/c1/projects';
const supersede = 'PUT /v1/admin/documents/d1/superseded-by';
const base = { companyId: 'c1', filename: 'f.pdf', format: 'PDF', paginated: true, attemptCount: 1, pagesWithoutText: [] as number[], topics: [] as unknown[], uploadedAt: '2026-03-10T10:00:00Z' };
const old = { ...base, id: 'd1', title: 'Póliza 2022', status: 'READY', projectId: 'p1' };
const renewed = { ...base, id: 'd2', title: 'Póliza 2024', status: 'READY', projectId: 'p1', uploadedAt: '2026-05-02T10:00:00Z' };
const draft = { ...base, id: 'd3', title: 'Borrador', status: 'FAILED' };
const marked = { ...old, supersededBy: { id: 'd2', title: 'Póliza 2024' }, supersededAt: '2026-06-01T09:00:00Z' };
const candidatesUrl = 'GET /v1/admin/companies/c1/documents?offset=0&limit=50';
const routes = (documents: unknown[], more: Routes = {}): Routes => ({
  [list]: () => ({ documents, total: documents.length }), [candidatesUrl]: () => ({ documents, total: documents.length }), [projects]: () => [{ id: 'p1', name: 'Los Almendros' }], ...more,
});

const show = (r: Routes, scannedDocumentsSupported = false) => {
  const calls = mockFetch(r);
  renderWithProviders(<Documents companyId="c1" scannedDocumentsSupported={scannedDocumentsSupported} />);
  return calls;
};
const openMenu = async (title: string) => {
  const toggle = await screen.findByRole('button', { name: `Más acciones de ${title}` });
  if (toggle.getAttribute('aria-expanded') !== 'true') await userEvent.click(toggle);
};
const openDialog = async (title: string) => {
  await openMenu(title);
  await userEvent.click(screen.getByRole('button', { name: `Marcar como sustituido ${title}` }));
  return screen.getByRole('dialog', { name: 'Marcar como sustituido' });
};

test('showsWhoUploadedEachDocument', async () => {
  show(routes([{ ...old, uploadedByName: 'Marta Gil' }, renewed]));
  const row = (await screen.findByText('Póliza 2022')).closest('tr')!;
  expect(within(row).getByText('Marta Gil')).toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: 'Subido por' })).toBeInTheDocument();
});

test('showsSupersededMarkWithReplacementTitle', async () => {
  show(routes([marked, renewed]));
  const row = (await screen.findByText('Póliza 2022')).closest('tr')!;
  expect(within(row).getByText('Sustituido')).toBeInTheDocument();
  expect(within(row).getByText('por Póliza 2024')).toBeInTheDocument();
  expect(within(screen.getByText('Póliza 2024', { selector: 'td' }).closest('tr')!).queryByText('Sustituido')).toBeNull();
});

test('candidatesAreReadyDocumentsWithProjectAndDateSearchedOnTheServer', async () => {
  const zzz = `${candidatesUrl}&q=zzz`;
  show(routes([old, renewed, draft], { [zzz]: () => ({ documents: [], total: 0 }) }));
  const dialog = await openDialog('Póliza 2022');
  expect(within(dialog).getByText(/Usa esta marca cuando un documento sustituye entero a otro, como una póliza renovada\. Un anexo que cambia una parte no sustituye al contrato\./)).toBeInTheDocument();
  const group = within(dialog).getByRole('group', { name: 'Documento que lo sustituye' });
  const options = await within(group).findAllByRole('radio');
  expect(options).toHaveLength(1);
  expect(options[0]).toHaveAccessibleName('Póliza 2024 · Los Almendros · 2 may 2026');
  fireEvent.change(within(dialog).getByRole('searchbox', { name: 'Buscar por título' }), { target: { value: 'zzz' } });
  expect(await within(dialog).findByText('Ningún documento listo coincide con la búsqueda.')).toBeInTheDocument();
});

test('supersedeDialogFindsACandidateThatIsNotOnThePage', async () => {
  const far = { ...renewed, id: 'd9', title: 'Póliza 2030' };
  const q = `${candidatesUrl}&q=2030`;
  show(routes([old, renewed], { [q]: () => ({ documents: [far], total: 1 }) }));
  const dialog = await openDialog('Póliza 2022');
  fireEvent.change(within(dialog).getByRole('searchbox', { name: 'Buscar por título' }), { target: { value: '2030' } });
  expect(await within(dialog).findByRole('radio', { name: /Póliza 2030/ })).toBeInTheDocument();
});

test('supersedeDialogShowsAReadyCandidateBeyondNonReadyOnesWithoutAFalseHint', async () => {
  const failed = Array.from({ length: 10 }, (_, i) => ({ ...draft, id: `f${i}`, title: `Fallido ${i}` }));
  show(routes([old, ...failed, renewed]));
  const dialog = await openDialog('Póliza 2022');
  expect(await within(dialog).findByRole('radio', { name: /Póliza 2024/ })).toBeInTheDocument();
  expect(within(dialog).queryByText('Escribe al menos 3 letras del título para afinar')).toBeNull();
});

test('supersedeDialogHintsOnlyWhenReadyCandidatesAreHiddenAndNoSearchIsActive', async () => {
  const ready = Array.from({ length: 11 }, (_, i) => ({ ...renewed, id: `r${i}`, title: `Póliza ${2030 + i}` }));
  const q = `${candidatesUrl}&q=2031`;
  show(routes([old, ...ready], { [q]: () => ({ documents: [ready[1]], total: 1 }) }));
  const dialog = await openDialog('Póliza 2022');
  expect(await within(dialog).findByText('Escribe al menos 3 letras del título para afinar')).toBeInTheDocument();
  fireEvent.change(within(dialog).getByRole('searchbox', { name: 'Buscar por título' }), { target: { value: '2031' } });
  await waitFor(() => expect(within(dialog).getAllByRole('radio')).toHaveLength(1)); // ya llegó la búsqueda
  expect(within(dialog).queryByText('Escribe al menos 3 letras del título para afinar')).toBeNull();
});

test('supersedeDialogShowsSearchErrorAndNoFalseEmptyMessage', async () => {
  show(routes([old], { [candidatesUrl]: () => ({ status: 500, body: { detail: 'Fallo del buscador' } }) }));
  const dialog = await openDialog('Póliza 2022');
  expect(await within(dialog).findByText('Fallo del buscador')).toBeInTheDocument();
  expect(within(dialog).queryByText('Ningún documento listo coincide con la búsqueda.')).toBeNull();
});

test('supersedeDialogDropsStaleSearchResponses', async () => {
  const slow: Record<string, (r: Response) => void> = {};
  const json = (b: unknown) => new Response(JSON.stringify(b), { status: 200 });
  const doc = (title: string) => ({ ...renewed, id: title, title });
  vi.stubGlobal('fetch', vi.fn((url: string) => {
    if (url.endsWith('/projects')) return Promise.resolve(json([]));
    if (url.includes('q=')) return new Promise<Response>((res) => { slow[url.split('q=')[1]!] = res; });
    return Promise.resolve(json({ documents: [old], total: 1, items: [] }));
  }));
  renderWithProviders(<Documents companyId="c1" />);
  const dialog = await openDialog('Póliza 2022');
  const box = within(dialog).getByRole('searchbox', { name: 'Buscar por título' });
  fireEvent.change(box, { target: { value: 'pol' } });
  await waitFor(() => expect(slow['pol']).toBeDefined());
  fireEvent.change(box, { target: { value: 'poli' } });
  await waitFor(() => expect(slow['poli']).toBeDefined());
  slow['poli']!(json({ documents: [doc('Nueva')], total: 1 }));
  await within(dialog).findByRole('radio', { name: /Nueva/ });
  slow['pol']!(json({ documents: [doc('Vieja')], total: 1 }));
  await new Promise((r) => setTimeout(r, 20));
  expect(within(dialog).queryByRole('radio', { name: /Vieja/ })).toBeNull();
});
test('marksAsSupersededAfterTheConfirmationText', async () => {
  const calls = show(routes([old, renewed], { [supersede]: () => ({ status: 200, body: marked }) }));
  const dialog = await openDialog('Póliza 2022');
  expect(within(dialog).queryByRole('button', { name: 'Marcar como sustituido' })).toBeNull();
  await userEvent.click(within(dialog).getByRole('radio'));
  expect(within(dialog).getByText('“Póliza 2022” quedará guardado, pero Odín lo dará como sustituido por “Póliza 2024” y lo pondrá detrás en las búsquedas.')).toBeInTheDocument();
  await userEvent.click(within(dialog).getByRole('button', { name: 'Marcar como sustituido' }));
  await waitFor(() => expect(calls.find((c) => c.key === supersede)?.body).toEqual({ replacementId: 'd2' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
});

test('conflictMessageStaysInTheOpenDialog', async () => {
  show(routes([old, renewed], { [supersede]: () => ({ status: 409, body: { detail: 'Ese documento ya está sustituido' } }) }));
  const dialog = await openDialog('Póliza 2022');
  await userEvent.click(within(dialog).getByRole('radio'));
  await userEvent.click(within(dialog).getByRole('button', { name: 'Marcar como sustituido' }));
  expect(await within(dialog).findByRole('alert')).toHaveTextContent('Ese documento ya está sustituido');
  expect(screen.getByRole('dialog', { name: 'Marcar como sustituido' })).toBeInTheDocument();
});

test('removesTheMark', async () => {
  const del = 'DELETE /v1/admin/documents/d1/superseded-by';
  const calls = show(routes([marked, renewed], { [del]: () => ({ status: 204 }) }));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Quitar marca de sustituido de Póliza 2022' }));
  await waitFor(() => expect(calls.some((c) => c.key === del)).toBe(true));
  expect(screen.queryByRole('dialog')).toBeNull();
  await waitFor(() => expect(calls.filter((c) => c.key === list)).toHaveLength(2));
});

test('unmarkedDocumentOffersNoRemoval', async () => {
  show(routes([old, renewed]));
  await openMenu('Póliza 2022');
  expect(screen.queryByRole('button', { name: /Quitar marca/ })).toBeNull();
});

test('detailShowsReplacementAndMarkDate', async () => {
  show(routes([marked, renewed]));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Ver detalle de Póliza 2022' }));
  const dialog = screen.getByRole('dialog', { name: 'Póliza 2022' });
  expect(within(dialog).getByText('Listo')).toBeInTheDocument();
  expect(within(dialog).getByText('Los Almendros')).toBeInTheDocument();
  expect(within(dialog).getByText('10 mar 2026')).toBeInTheDocument();
  expect(within(dialog).getByText('Sustituido por Póliza 2024')).toBeInTheDocument();
  expect(within(dialog).getByText('Marcado el 1 jun 2026')).toBeInTheDocument();
});

const waiting = { ...base, id: 'd4', title: 'Acta escaneada', status: 'PENDING_READER' };
const reading = { ...base, id: 'd5', title: 'Plano', status: 'READER_PROCESSING' };

test('pendingReaderAndReaderProcessingLabels', async () => {
  show(routes([waiting, reading]), true);
  expect(within((await screen.findByText('Acta escaneada')).closest('tr')!).getByText('Esperando al lector OCR')).toBeInTheDocument();
  expect(within(screen.getByText('Plano').closest('tr')!).getByText('Leyendo con OCR')).toBeInTheDocument();
});

test('pendingReaderSaysInstallationDoesNotReadScansWhenUnsupported', async () => {
  show(routes([waiting]), false);
  const row = (await screen.findByText('Acta escaneada')).closest('tr')!;
  expect(within(row).getByText('Esta instalación no lee documentos escaneados')).toBeInTheDocument();
  expect(within(row).queryByText('Esperando al lector OCR')).toBeNull();
});

test('listsPagesWithoutText', async () => {
  show(routes([{ ...old, pagesWithoutText: [3, 5] }, renewed]));
  const row = (await screen.findByText('Póliza 2022')).closest('tr')!;
  expect(within(row).getByText('Páginas sin texto, no leídas: 3, 5')).toBeInTheDocument();
  expect(within(screen.getByText('Póliza 2024', { selector: 'td' }).closest('tr')!).queryByText(/Páginas sin texto/)).toBeNull();
});

const uploadUrl = 'POST /v1/admin/companies/c1/documents';
const pdf = (name: string) => new File(['x'], name, { type: 'application/pdf' });
const openUpload = async (r: Routes) => {
  const calls = show(r);
  await userEvent.click(await screen.findByRole('button', { name: 'Subir documento' }));
  return { calls, dialog: screen.getByRole('dialog', { name: 'Subir documento' }) };
};
const uploadedKeys = (calls: { key: string }[]) => calls.filter((c) => c.key.startsWith(uploadUrl)).map((c) => c.key);

test('uploadSendsCaseDocument', async () => {
  const { calls, dialog } = await openUpload(routes([old], { [`${uploadUrl}?title=Acta&caseDocument=true`]: () => ({ documentId: 'n1' }) }));
  await userEvent.upload(within(dialog).getByLabelText('Fichero'), pdf('acta.pdf'));
  await userEvent.type(within(dialog).getByLabelText('Título (opcional)'), 'Acta');
  await userEvent.click(within(dialog).getByLabelText('Son fichas de caso'));
  await userEvent.click(within(dialog).getByRole('button', { name: 'Subir' }));
  await waitFor(() => expect(uploadedKeys(calls)).toEqual([`${uploadUrl}?title=Acta&caseDocument=true`]));
});

test('uploadWithoutCheckboxSendsNoCaseDocument', async () => {
  const { calls, dialog } = await openUpload(routes([old], { [`${uploadUrl}?`]: () => ({ documentId: 'n2' }) }));
  await userEvent.upload(within(dialog).getByLabelText('Fichero'), pdf('b.pdf'));
  await userEvent.click(within(dialog).getByRole('button', { name: 'Subir' }));
  await waitFor(() => expect(uploadedKeys(calls)).toEqual([`${uploadUrl}?`]));
});

test('uploadsSeveralFilesWithOneCaseCheckbox', async () => {
  const { calls, dialog } = await openUpload(routes([old], { [`${uploadUrl}?title=uno&caseDocument=true`]: () => ({ documentId: 'n1' }), [`${uploadUrl}?title=dos&caseDocument=true`]: () => ({ documentId: 'n2' }) }));
  await userEvent.upload(within(dialog).getByLabelText('Fichero'), [pdf('uno.pdf'), pdf('dos.pdf')]);
  expect(within(dialog).queryByLabelText('Título (opcional)')).toBeNull();
  await userEvent.click(within(dialog).getByLabelText('Son fichas de caso'));
  await userEvent.click(within(dialog).getByRole('button', { name: 'Subir' }));
  await waitFor(() => expect(uploadedKeys(calls)).toHaveLength(2));
  expect(uploadedKeys(calls)).toEqual([`${uploadUrl}?title=uno&caseDocument=true`, `${uploadUrl}?title=dos&caseDocument=true`]);
  const forms = calls.filter((c) => c.key.startsWith(uploadUrl)).map((c) => ((c.body as FormData).get('file') as File).name);
  expect(forms).toEqual(['uno.pdf', 'dos.pdf']);
  expect(await screen.findByText('Se subieron 2 documentos.')).toBeInTheDocument();
});

test('batchUploadReportsFailuresByFile', async () => {
  const { dialog } = await openUpload(routes([old], { [`${uploadUrl}?title=uno`]: () => ({ documentId: 'n1' }), [`${uploadUrl}?title=dos`]: () => ({ status: 400, body: { detail: 'Formato no admitido' } }) }));
  await userEvent.upload(within(dialog).getByLabelText('Fichero'), [pdf('uno.pdf'), pdf('dos.pdf')]);
  await userEvent.click(within(dialog).getByRole('button', { name: 'Subir' }));
  expect(await screen.findByText('Se subió 1 de 2 documentos. Fallaron: dos.pdf (Formato no admitido).')).toBeInTheDocument();
});

test('rowShowsCaseLabel', async () => {
  show(routes([{ ...old, caseDocument: true }, renewed]));
  const row = (await screen.findByText('Póliza 2022')).closest('tr')!;
  expect(within(row).getByText('Caso')).toBeInTheDocument();
  expect(within(screen.getByText('Póliza 2024', { selector: 'td' }).closest('tr')!).queryByText('Caso')).toBeNull();
});

test('togglesCaseMark', async () => {
  const patch = (id: string) => `PATCH /v1/admin/documents/${id}`;
  const calls = show(routes([{ ...old, caseDocument: true }, renewed], { [patch('d1')]: () => old, [patch('d2')]: () => renewed }));
  await openMenu('Póliza 2024');
  expect(screen.queryByRole('button', { name: 'Quitar marca de caso de Póliza 2024' })).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Marcar como caso Póliza 2024' }));
  await waitFor(() => expect(calls.find((c) => c.key === patch('d2'))?.body).toEqual({ caseDocument: true }));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Quitar marca de caso de Póliza 2022' }));
  await waitFor(() => expect(calls.find((c) => c.key === patch('d1'))?.body).toEqual({ caseDocument: false }));
});

test('marksSelectedDocumentsAsCases', async () => {
  const marks = 'POST /v1/admin/companies/c1/documents/case-marks';
  const calls = show(routes([old, renewed, draft], { [marks]: () => ({ updated: 2 }) }));
  await screen.findByText('Póliza 2022');
  expect(screen.queryByRole('button', { name: 'Marcar como caso' })).toBeNull();
  await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar Póliza 2022' }));
  await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar Póliza 2024' }));
  await userEvent.click(screen.getByRole('button', { name: 'Marcar como caso' }));
  await waitFor(() => expect(calls.find((c) => c.key === marks)?.body).toEqual({ documentIds: ['d1', 'd2'], caseDocument: true }));
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Marcar como caso' })).toBeNull());
  await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar Borrador' }));
  await userEvent.click(screen.getByRole('button', { name: 'Quitar marca de caso' }));
  await waitFor(() => expect(calls.filter((c) => c.key === marks).at(-1)?.body).toEqual({ documentIds: ['d3'], caseDocument: false }));
});

test('showsTaggingStatusLabels', async () => {
  const tagged = { ...old, id: 'd4', title: 'Con tema', taggingStatus: 'DONE', topics: [{ id: 't1', name: 'Agua', source: 'AI' }, { id: 't2', name: 'Seguros', source: 'ADMIN' }] };
  show(routes([tagged, { ...renewed, taggingStatus: 'TAGGING' }, { ...draft, taggingStatus: 'FAILED' }, { ...old, id: 'd5', title: 'Sin fila' }, { ...old, id: 'd6', title: 'En espera', taggingStatus: 'PENDING' }]));
  const label = async (title: string) => within((await screen.findByText(title)).closest('tr')!);
  expect((await label('Con tema')).getByText('Etiquetado')).toBeInTheDocument();
  expect((await label('Póliza 2024')).getByText('Etiquetando')).toBeInTheDocument();
  expect((await label('Borrador')).getByText('Falló')).toBeInTheDocument();
  expect((await label('Sin fila')).getByText('Sin etiquetar')).toBeInTheDocument();
  expect((await label('En espera')).getByText('Sin etiquetar')).toBeInTheDocument();
  const topics = await label('Con tema');
  expect(topics.getByText('Agua')).toHaveTextContent('asignado por IA');
  expect(topics.getByText('Seguros')).not.toHaveTextContent('asignado por IA');
  expect(screen.getByRole('columnheader', { name: 'Temas' })).toBeInTheDocument();
});

const withdrawnList = `${list}&withdrawn=true`;
const withdrawal = 'PUT /v1/admin/documents/d1/withdrawal';
const restoreUrl = 'DELETE /v1/admin/documents/d1/withdrawal';

test('withdrawAsksConfirmationWithHelpTextAndReloads', async () => {
  const calls = show(routes([old, renewed], { [withdrawal]: () => ({ status: 200, body: { ...old, withdrawnAt: '2026-11-13T10:00:00Z' } }) }));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Retirar Póliza 2022' }));
  const dialog = screen.getByRole('dialog', { name: /Retirar «Póliza 2022»/ });
  expect(within(dialog).getByText(/Dejará de salir en las búsquedas y en los expedientes/)).toBeInTheDocument();
  expect(within(dialog).getByText(/Puedes recuperarlo desde «Retirados»/)).toBeInTheDocument();
  expect(calls.some((c) => c.key === withdrawal)).toBe(false);
  await userEvent.click(within(dialog).getByRole('button', { name: 'Retirar' }));
  await waitFor(() => expect(calls.some((c) => c.key === withdrawal)).toBe(true));
  await waitFor(() => expect(calls.filter((c) => c.key === list)).toHaveLength(2));
});

test('cancellingTheConfirmationDoesNotWithdraw', async () => {
  const calls = show(routes([old], { [withdrawal]: () => ({ status: 200, body: old }) }));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Retirar Póliza 2022' }));
  await userEvent.click(within(screen.getByRole('dialog', { name: /Retirar/ })).getByRole('button', { name: 'Cancelar' }));
  expect(calls.some((c) => c.key === withdrawal)).toBe(false);
});

test('withdrawnFilterListsOnlyWithdrawnAndRestores', async () => {
  const gone = { ...old, withdrawnAt: '2026-11-13T10:00:00Z' };
  const calls = show(routes([renewed], { [withdrawnList]: () => ({ documents: [gone], total: 1 }), [restoreUrl]: () => ({ status: 200, body: old }) }));
  await screen.findByText('Póliza 2024');
  await userEvent.click(screen.getByRole('button', { name: 'Retirados' }));
  expect(await screen.findByText('Póliza 2022')).toBeInTheDocument();
  expect(screen.queryByText('Póliza 2024')).toBeNull();
  await userEvent.click(screen.getByRole('button', { name: 'Recuperar Póliza 2022' }));
  await waitFor(() => expect(calls.some((c) => c.key === restoreUrl)).toBe(true));
});

test('restoreConflictShowsTheServerMessage', async () => {
  const gone = { ...old, withdrawnAt: '2026-11-13T10:00:00Z' };
  show(routes([], { [withdrawnList]: () => ({ documents: [gone], total: 1 }), [restoreUrl]: () => ({ status: 409, body: { detail: 'Ya hay un documento activo igual' } }) }));
  await userEvent.click(await screen.findByRole('button', { name: 'Retirados' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Recuperar Póliza 2022' }));
  expect(await screen.findByText('Ya hay un documento activo igual')).toBeInTheDocument();
});

test('caseDocumentLinksToItsCaseScreen', async () => {
  show(routes([{ ...old, caseDocument: true }, renewed]));
  const row = (await screen.findByText('Póliza 2022')).closest('tr')!;
  expect(within(row).getByRole('link', { name: 'Ver expediente de Póliza 2022' })).toHaveAttribute('href', '/casos/d1?empresa=c1');
});

test('detailOffersTheOriginalDownload', async () => {
  show(routes([old, renewed]));
  await openMenu('Póliza 2022');
  await userEvent.click(screen.getByRole('button', { name: 'Ver detalle de Póliza 2022' }));
  const dialog = screen.getByRole('dialog', { name: 'Póliza 2022' });
  expect(within(dialog).getByRole('link', { name: 'Descargar original' })).toHaveAttribute('href', '/v1/admin/documents/d1/original');
});

test('withdrawnViewHasNoSelectionNorCaseLink', async () => {
  const gone = { ...old, caseDocument: true, withdrawnAt: '2026-11-13T10:00:00Z' };
  show(routes([], { [withdrawnList]: () => ({ documents: [gone], total: 1 }) }));
  await userEvent.click(await screen.findByRole('button', { name: 'Retirados' }));
  await screen.findByText('Póliza 2022');
  expect(screen.queryByRole('checkbox')).toBeNull();
  expect(screen.queryByRole('link', { name: /Ver expediente/ })).toBeNull();
});

const pageOf = (n: number, total = 60) => ({ documents: Array.from({ length: Math.min(n, 3) }, (_, i) => ({ ...old, id: `x${i}`, title: `Doc ${i}` })), total });
const search = (value: string) => fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar documentos' }), { target: { value } });

test('urlCarriesQueryOffsetAndLimit', async () => {
  const next = `${list.replace('offset=0', 'offset=25')}`;
  const calls = show(routes([old], { [list]: () => pageOf(25), [next]: () => pageOf(25), [`${list}&q=poli`]: () => pageOf(1, 1) }));
  await screen.findByText('Doc 0');
  expect(screen.getByRole('navigation', { name: 'Páginas de documentos' })).toHaveTextContent('Página 1 de 3');
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  await waitFor(() => expect(calls.some((c) => c.key === next)).toBe(true));
  search('poli');
  await waitFor(() => expect(calls.some((c) => c.key === `${list}&q=poli`)).toBe(true));
});

test('changingPageSizeGoesBackToPageOne', async () => {
  const p2 = list.replace('offset=0', 'offset=25');
  const calls = show(routes([old], { [list]: () => pageOf(25), [p2]: () => pageOf(25), [list.replace('limit=25', 'limit=50')]: () => pageOf(50) }));
  await screen.findByText('Doc 0');
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByText('Página 2 de 3');
  await userEvent.selectOptions(screen.getByLabelText('Por página'), '50');
  await waitFor(() => expect(calls.at(-1)?.key).toBe(list.replace('limit=25', 'limit=50')));
});

test('selectionClearsOnPageChange', async () => {
  show(routes([old], { [list]: () => pageOf(25), [list.replace('offset=0', 'offset=25')]: () => pageOf(25) }));
  await userEvent.click(await screen.findByRole('checkbox', { name: 'Seleccionar Doc 0' }));
  expect(screen.getByText('1 seleccionado')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  await waitFor(() => expect(screen.queryByText('1 seleccionado')).toBeNull());
});

test('withdrawnToggleGoesBackToPageOne', async () => {
  const calls = show(routes([old], { [list]: () => pageOf(25), [list.replace('offset=0', 'offset=25')]: () => pageOf(25), [withdrawnList]: () => pageOf(1, 1) }));
  await screen.findByText('Doc 0');
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByText('Página 2 de 3');
  await userEvent.click(screen.getByRole('button', { name: 'Retirados' }));
  await waitFor(() => expect(calls.at(-1)?.key).toBe(withdrawnList));
});

const next25 = list.replace('offset=0', 'offset=25');
const selectFirst = async () => {
  await userEvent.click(await screen.findByRole('checkbox', { name: 'Seleccionar Doc 0' }));
  expect(screen.getByText('1 seleccionado')).toBeInTheDocument();
};
const gone = () => waitFor(() => expect(screen.queryByText('1 seleccionado')).toBeNull());

test('selectionClearsOnSearch', async () => {
  show(routes([old], { [list]: () => pageOf(25), [`${list}&q=poli`]: () => pageOf(1, 1) }));
  await selectFirst();
  search('poli');
  await gone();
});

test('selectionClearsOnPageSizeChange', async () => {
  show(routes([old], { [list]: () => pageOf(25), [list.replace('limit=25', 'limit=50')]: () => pageOf(50) }));
  await selectFirst();
  await userEvent.selectOptions(screen.getByLabelText('Por página'), '50');
  await gone();
});

test('selectionClearsOnWithdrawnToggle', async () => {
  show(routes([old], { [list]: () => pageOf(25), [withdrawnList]: () => pageOf(1, 1) }));
  await selectFirst();
  await userEvent.click(screen.getByRole('button', { name: 'Retirados' }));
  await gone();
});

test('selectionKeepsOnlyRowsStillInViewAfterReload', async () => {
  let rows = pageOf(25);
  show(routes([old], { [list]: () => rows }));
  await selectFirst();
  rows = { documents: [{ ...old, id: 'z9', title: 'Otro' }], total: 1 };
  await userEvent.click(screen.getByRole('button', { name: 'Actualizar' }));
  await screen.findByText('Otro');
  await gone();
});

test('togglingWithdrawnTwiceReturnsToPageOne', async () => {
  const calls = show(routes([old], { [list]: () => pageOf(25), [next25]: () => pageOf(25), [withdrawnList]: () => pageOf(1, 1) }));
  await screen.findByText('Doc 0');
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
  await screen.findByText('Página 2 de 3');
  await userEvent.click(screen.getByRole('button', { name: 'Retirados' }));
  await waitFor(() => expect(calls.at(-1)?.key).toBe(withdrawnList));
  await userEvent.click(screen.getByRole('button', { name: 'Retirados' }));
  await screen.findByText('Página 1 de 3');
  expect(calls.at(-1)?.key).toBe(list);
});

test('failedLoadShowsOnlyTheErrorNotTheEmptyCompanyState', async () => {
  show(routes([old], { [list]: () => ({ status: 500, body: { detail: 'Se cayó' } }) }));
  expect(await screen.findByText('Se cayó')).toBeInTheDocument();
  expect(screen.queryByText('Aún no hay documentos')).toBeNull();
});

test('searchWithoutResultsDoesNotShowTheEmptyCompanyState', async () => {
  show(routes([old], { [list]: () => pageOf(25), [`${list}&q=zzzz`]: () => ({ documents: [], total: 0 }) }));
  await screen.findByText('Doc 0');
  search('zzzz');
  expect(await screen.findByText('Ningún resultado para «zzzz»')).toBeInTheDocument();
  expect(screen.queryByText('Aún no hay documentos')).toBeNull();
});

test('laterFailureDoesNotLeaveOldRowsLookingCurrent', async () => {
  show(routes([old], { [list]: () => pageOf(25), [`${list}&q=zzzz`]: () => ({ status: 500, body: { detail: 'Se cayó' } }) }));
  await screen.findByText('Doc 0');
  search('zzzz');
  expect(await screen.findByText('Se cayó')).toBeInTheDocument();
  expect(screen.queryByText('Doc 0')).toBeNull();
});

test('clearingAZeroResultSearchNeverFlashesTheEmptyState', async () => {
  show(routes([old], { 'GET /v1/admin/companies/c1/documents?offset=0&limit=25&q=zzz': () => ({ documents: [], total: 0 }) }));
  await screen.findByText('Póliza 2022');
  fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar documentos' }), { target: { value: 'zzz' } });
  await screen.findByText(/Ningún resultado/);
  let flashed = false;
  const watch = new MutationObserver(() => { flashed ||= !!screen.queryByText('Aún no hay documentos'); });
  watch.observe(document.body, { childList: true, subtree: true });
  fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar documentos' }), { target: { value: '' } });
  await screen.findByText('Póliza 2022');
  watch.disconnect();
  expect(flashed).toBe(false);
});
