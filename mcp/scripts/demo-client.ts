// Demostración de cierre de la fase 1 (spec 3.1). Fases: `prepare` (pasos 1-7d) y `confirm` (pasos 9 y 10).
// Los pasos 8 y 8b (panel) los hace Playwright en panel/e2e/demo.spec.ts entre ambas fases.
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const API = process.env.DEMO_API_URL ?? 'http://127.0.0.1:18080';
const SETUP_TOKEN = need('DEMO_SETUP_TOKEN');
const STATE = need('DEMO_STATE');
const PDF = need('DEMO_PDF');
const RENEWED = need('DEMO_RENEWED');
const SCAN = need('DEMO_SCAN');
const ADMIN = { email: 'admin@demo.invalid', password: 'demo-clave-ficticia-1234' };
const QUERY = 'franquicia daños por agua ATL-4417-2024';
const TITLE = 'Póliza ficticia';
const RENEWED_TITLE = 'Póliza ficticia renovada';
const NO_SOURCE = 'No consta en los documentos de tu empresa.';
const CASE_QUERY = 'filtración de agua desde la azotea lámina';
const CASE_FILES = [need('DEMO_CASE_1'), need('DEMO_CASE_2')];
const FILE_DIR = need('DEMO_EXPEDIENTE_DIR');
const PHOTOS_DIR = need('DEMO_PHOTOS_DIR');
const PUBLIC_URL = process.env.DEMO_PANEL_URL ?? 'http://127.0.0.1:18088';
const EXPEDIENTE: [string, string][] = [['informe-perito-ficticio.pdf', 'Informe del perito'], ['presupuesto-1-ficticio.pdf', 'Presupuesto 1'],
  ['presupuesto-2-ficticio.pdf', 'Presupuesto 2'], ['factura-ficticio.pdf', 'Factura'], ['correo-ficticio.pdf', 'Correo impreso'], ['foto-azotea-ficticia.png', 'Foto de la azotea']];
const CARD = { title: 'Franquicia por daños de agua', body: 'La póliza ATL-4417-2024 aplica una franquicia de 150 euros por siniestro de daños por agua.' };

function need(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`falta la variable ${name}`);
  return v;
}
function check(ok: boolean, text: string): void {
  console.log(`${ok ? '✓' : '✗'} ${text}`);
  if (!ok) throw new Error(`falló: ${text}`);
}

const MIME: Record<string, string> = { '.pdf': 'application/pdf', '.png': 'image/png' };
let cookie = '';
async function http(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<Response> {
  const isRaw = body instanceof FormData;
  const h: Record<string, string> = { ...headers, ...(cookie && { Cookie: cookie }) };
  if (body !== undefined && !isRaw) h['Content-Type'] = 'application/json';
  return fetch(API + path, { method, headers: h, body: body === undefined ? undefined : isRaw ? body : JSON.stringify(body) });
}
async function json<T>(r: Response, expected: number): Promise<T> {
  if (r.status !== expected) throw new Error(`HTTP ${r.status} (se esperaba ${expected}): ${await r.text()}`);
  return (await r.json()) as T;
}

type Id = { id: string };
async function createCompany(name: string, code: string, extra: string[] = []): Promise<string> {
  const c = await json<Id>(await http('POST', '/v1/admin/companies', { name, code }), 201);
  const caps = ['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD', ...extra];
  await http('PUT', `/v1/admin/companies/${c.id}/capabilities`, { capabilities: caps });
  return c.id;
}
async function createWorker(companyId: string, firstName: string, lastName: string, email: string): Promise<{ id: string; key: string }> {
  const added = await json<{ member: { userId: string } }>(
    await http('POST', `/v1/admin/companies/${companyId}/members`, { firstName, lastName, email, roles: ['WORKER'] }), 201);
  const { userId } = added.member;
  const k = await json<{ key: string }>(await http('POST', `/v1/admin/companies/${companyId}/members/${userId}/keys`, { label: 'demo' }), 201);
  return { id: userId, key: k.key };
}
async function uploadFile(companyId: string, path: string, title: string, projectId?: string, caseDocument = false): Promise<string> {
  const form = new FormData();
  form.append('file', new Blob([readFileSync(path)], { type: MIME[path.slice(path.lastIndexOf('.'))] ?? 'text/plain' }), basename(path));
  const scope = (projectId ? `&projectId=${projectId}` : '') + (caseDocument ? '&caseDocument=true' : '');
  const r = await http('POST', `/v1/admin/companies/${companyId}/documents?title=${encodeURIComponent(title)}${scope}`, form);
  return (await json<{ document: Id }>(r, 201)).document.id;
}
async function waitReady(companyId: string, docId: string): Promise<void> {
  for (let i = 0; i < 60; i++) {
    const docs = await json<{ id: string; status: string }[]>(await http('GET', `/v1/admin/companies/${companyId}/documents`), 200);
    const status = docs.find((d) => d.id === docId)?.status;
    if (status === 'READY') return;
    if (status === 'FAILED') throw new Error('el documento ha fallado');
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error('el documento no llegó a READY en 60 s');
}

async function withMcp<T>(key: string, run: (call: (name: string, args: object) => Promise<string>) => Promise<T>): Promise<T> {
  const script = fileURLToPath(new URL('../dist/stdio.js', import.meta.url));
  const transport = new StdioClientTransport({ command: process.execPath, args: [script], env: { PATH: process.env.PATH ?? '', ODIN_API_URL: API, ODIN_ACCESS_KEY: key } });
  const client = new Client({ name: 'odin-demo', version: '0.1.0' });
  await client.connect(transport);
  try {
    return await run(async (name, args) => {
      const res = await client.callTool({ name, arguments: args });
      const text = (res.content as { text: string }[])[0]?.text ?? '';
      if (res.isError) throw new Error(`${name}: ${text}`);
      return text;
    });
  } finally {
    await client.close();
  }
}

type Hit = { documentId: string; pageNumber: number | null; excerpt: string };
async function searchAsA(key: string, docId: string): Promise<{ hit: Hit; verified: boolean }> {
  return withMcp(key, async (call) => {
    const { hits } = JSON.parse(await call('searchKnowledge', { queries: [QUERY] })) as { hits: Hit[] };
    const hit = hits.find((h) => h.documentId === docId && h.pageNumber !== null && /ATL-4417|franquicia/i.test(h.excerpt));
    if (!hit) throw new Error('ningún resultado es del PDF subido con texto de la póliza');
    // el extracto puede traer elipsis de recorte; se cita lo que hay entre ellas
    const quote = hit.excerpt.replace(/[…[\]]|\.\.\./g, ' ').split(/\s{2,}/).sort((a, b) => b.length - a.length)[0]!.trim();
    const v = JSON.parse(await call('verifyCitation', { citations: [{ documentId: hit.documentId, pageNumber: hit.pageNumber, quote }] })) as Verified;
    return { hit, verified: v.results[0]?.verified === true };
  });
}

type Verified = { results: { verified: boolean }[] };

async function closedProjectStep(): Promise<void> {
  const c = await createCompany('Empresa C', 'EC');
  const assigned = await createWorker(c, 'Trabajadora', 'Asignada', 'asignada@demo.invalid');
  const outsider = await createWorker(c, 'Trabajador', 'Ajeno', 'ajeno@demo.invalid');
  const project = await json<Id>(await http('POST', `/v1/admin/companies/${c}/projects`, { name: 'Proyecto cerrado' }), 201);
  const assign = await http('PUT', `/v1/admin/companies/${c}/projects/${project.id}/members/${assigned.id}`);
  if (assign.status !== 204) throw new Error(`HTTP ${assign.status} al asignar el proyecto: ${await assign.text()}`);
  const docId = await uploadFile(c, PDF, TITLE, project.id);
  await waitReady(c, docId);
  const found = await searchAsA(assigned.key, docId);
  check(found.verified, 'Paso 7b: el trabajador asignado al proyecto cerrado encuentra su documento y la cita se verifica');
  const outBlind = await withMcp(outsider.key, (call) => call('searchKnowledge', { queries: [QUERY] }));
  check(outBlind === NO_SOURCE, 'Paso 7c: el trabajador no asignado recibe «No consta en los documentos de tu empresa.»');
}

async function scannedStep(): Promise<void> {
  const companyId = await createCompany('Empresa D', 'ED');
  const { key } = await createWorker(companyId, 'Trabajadora', 'Escaneos', 'escaneos@demo.invalid');
  const scanId = await uploadFile(companyId, SCAN, 'Acta ficticia escaneada');
  await waitReady(companyId, scanId);
  const found = await withMcp(key, async (call) => {
    const { hits } = JSON.parse(await call('searchKnowledge', { queries: ['bajante del patio'] })) as { hits: Hit[] };
    const hit = hits.find((h) => h.documentId === scanId && h.pageNumber === 1);
    const page = hit && (JSON.parse(await call('readPage', { documentId: scanId, pageNumber: 1 })) as { readerName?: string });
    return { hit, readerName: page?.readerName };
  });
  check(!!found.hit, 'Paso 7e: el escaneado ficticio se lee con el lector de prueba y searchKnowledge lo encuentra en la página 1');
  check(found.readerName === 'reader-stub', 'Paso 7f: readPage devuelve readerName «reader-stub»');
}

type TaggedDoc = { id: string; topics: { name: string; source: string }[] };
type DocTopics = { topics: { topicId: string; state: string }[]; tagging: { status: string } | null };

async function createTopic(companyId: string, name: string, definition: string): Promise<string> {
  return (await json<Id>(await http('POST', `/v1/admin/companies/${companyId}/topics`, { name, definition }), 201)).id;
}
async function waitTagged(companyId: string, docId: string): Promise<void> {
  for (let i = 0; i < 60; i++) {
    const docs = await json<{ id: string; taggingStatus: string | null }[]>(await http('GET', `/v1/admin/companies/${companyId}/documents`), 200);
    const status = docs.find((d) => d.id === docId)?.taggingStatus;
    if (status === 'DONE') return;
    if (status === 'FAILED') throw new Error('el etiquetado ha fallado');
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error('el documento no llegó a DONE en 60 s');
}
async function listedByTopic(key: string, docId: string): Promise<TaggedDoc | undefined> {
  const { documents } = await withMcp(key, async (call) => JSON.parse(await call('listDocuments', { topic: 'agua y humedades' })) as { documents: TaggedDoc[] });
  return documents.find((d) => d.id === docId);
}

// El etiquetador de prueba responde «agua y humedades» si el texto lleva «filtración» o «fuga»: prueba el camino de Odín, no el modelo.
async function taggingStep(): Promise<void> {
  const companyId = await createCompany('Empresa E', 'EE');
  const { key } = await createWorker(companyId, 'Trabajadora', 'Temas', 'temas@demo.invalid');
  const water = await createTopic(companyId, 'agua y humedades', 'Problemas de agua en el edificio');
  await createTopic(companyId, 'otros', 'Todo lo demás');
  const file = join(mkdtempSync(join(tmpdir(), 'odin-demo-')), 'incidencia-ficticia.txt');
  writeFileSync(file, 'Incidencia ficticia: se detectó una filtración en la bajante del patio.');
  const docId = await uploadFile(companyId, file, 'Incidencia ficticia');
  await waitTagged(companyId, docId);
  const found = await listedByTopic(key, docId);
  check(found?.topics.some((t) => t.name === 'agua y humedades' && t.source === 'AI') === true,
    'Paso 11: el documento con «filtración» llega a DONE y listDocuments(topic: "agua y humedades") lo devuelve con source AI');
  const base = `/v1/admin/documents/${docId}/topics`;
  check((await http('DELETE', `${base}/${water}`)).status === 204 && (await http('POST', `${base}/retag`)).status === 202,
    'Paso 11b: el administrador quita el tema y pide volver a etiquetar');
  await waitTagged(companyId, docId);
  const after = await json<DocTopics>(await http('GET', base), 200);
  check(after.topics.find((t) => t.topicId === water)?.state === 'REMOVED' && (await listedByTopic(key, docId)) === undefined,
    'Paso 11c: al terminar de etiquetar, el tema sigue quitado');
}

type SimilarCase = { documentId: string; title: string; pages: { number: number; text: string }[]; authorName?: string };

async function addProject(companyId: string, name: string, workerId: string): Promise<string> {
  const project = await json<Id>(await http('POST', `/v1/admin/companies/${companyId}/projects`, { name }), 201);
  const member = await http('PUT', `/v1/admin/companies/${companyId}/projects/${project.id}/members/${workerId}`);
  if (member.status !== 204) throw new Error(`HTTP ${member.status} al hacer miembro del proyecto: ${await member.text()}`);
  return project.id;
}

// La duda que se repite entre comunidades: el trabajador es miembro de las dos (D-047) y ve sus casos; el documento que no es caso no sale.
async function similarCasesStep(): Promise<void> {
  const companyId = await createCompany('Empresa F', 'EF', ['SIMILAR_CASES']);
  const worker = await createWorker(companyId, 'Trabajadora', 'Casos', 'casos@demo.invalid');
  const [olmo, almendros] = [await addProject(companyId, 'Comunidad Olmo 14', worker.id), await addProject(companyId, 'Comunidad Los Almendros', worker.id)];
  const file = join(mkdtempSync(join(tmpdir(), 'odin-demo-')), 'acta-ficticia.txt');
  writeFileSync(file, 'Acta ficticia de junta: se habló de la filtración de agua desde la azotea y de la lámina, sin ficha de caso.');
  const ids = [await uploadFile(companyId, CASE_FILES[0]!, 'Caso azotea Olmo 14', olmo, true),
    await uploadFile(companyId, CASE_FILES[1]!, 'Caso azotea Los Almendros', almendros, true),
    await uploadFile(companyId, file, 'Acta de junta', olmo)];
  for (const id of ids) await waitReady(companyId, id);
  const found = await withMcp(worker.key, async (call) => JSON.parse(await call('findSimilarCases', { queries: [CASE_QUERY] })) as { cases: SimilarCase[] });
  const returned = found.cases.map((c) => c.documentId).sort();
  check(JSON.stringify(returned) === JSON.stringify([ids[0], ids[1]].sort()), 'Paso 12: findSimilarCases devuelve las dos fichas de caso y no el documento que no es caso');
  check(found.cases.every((c) => c.pages.length > 0 && c.pages[0]!.text.length > 0 && !('authorName' in c)), 'Paso 12b: cada caso trae sus páginas y ninguno lleva autor');
}

// El autor ve su ficha pendiente marcada, su compañero no la ve; rechazada, el autor ve la nota y ya no la encuentra (D-036).
async function authorStep(): Promise<void> {
  const companyId = await createCompany('Empresa G', 'EG');
  const author = await createWorker(companyId, 'Autora', 'Fichas', 'autora@demo.invalid');
  const colleague = await createWorker(companyId, 'Compañero', 'Fichas', 'companero@demo.invalid');
  const card = { title: 'Reparto de llaves del garaje', body: 'Las llaves del garaje las custodia la portería desde la junta de marzo.' };
  const saved = JSON.parse(await withMcp(author.key, (call) => call('saveKnowledgeCard', card))) as { id: string };
  const search = (key: string, query = 'llaves del garaje') => withMcp(key, async (call) =>
    JSON.parse((await call('searchKnowledge', { queries: [query] })).replace(/^No consta.*$/, '{"hits":[]}')) as { hits: { cardId?: string; reviewStatus?: string }[] });
  const own = await search(author.key);
  check(own.hits.some((h) => h.cardId === saved.id && h.reviewStatus === 'PENDING'), 'Paso 13: el autor encuentra su ficha con reviewStatus PENDING');
  check((await search(colleague.key)).hits.length === 0, 'Paso 13b: el compañero de la misma empresa no la encuentra');
  const note = 'Falta la fecha de la junta';
  await json(await http('POST', `/v1/admin/cards/${saved.id}/review`, { decision: 'REJECT', note }), 200);
  const mine = await withMcp(author.key, async (call) => JSON.parse(await call('listMyKnowledgeCards', { status: 'REJECTED' })) as { cards: { cardId: string; status: string; review: { note: string } }[] });
  check(mine.cards.some((c) => c.cardId === saved.id && c.status === 'REJECTED' && c.review.note === note), 'Paso 13c: el autor ve su ficha como REJECTED con la nota del revisor');
  check((await search(author.key, 'portería del garaje')).hits.length === 0, 'Paso 13d: la ficha rechazada ya no sale en su búsqueda');
  const questions = await withMcp(author.key, async (call) => JSON.parse(await call('listMyQuestions', {})) as { questions: { text: string }[] });
  check(questions.questions.map((q) => q.text).join('|') === 'portería del garaje|llaves del garaje', 'Paso 13e: listMyQuestions devuelve sus dos consultas, la primera la más reciente');
  check((await withMcp(colleague.key, async (call) => JSON.parse(await call('listMyQuestions', {})) as { questions: unknown[] })).questions.length === 1,
    'Paso 13f: el compañero solo ve su propia consulta');
}

type CaseFileView = { documents: { id: string; title: string; filename: string; status: string }[]; pendingDocuments: number; unreadableDocuments: number };
type CaseFileText = { pages: { documentId: string; pageNumber: number; text: string; unread: boolean }[] };
type Link = { url: string; expiresAt: string };
const absolute = (url: string): string => (url.startsWith('http') ? url : PUBLIC_URL + url);

async function seedExpediente(): Promise<{ companyId: string; caseId: string; ids: Record<string, string>; worker: { id: string; key: string }; outsider: { id: string; key: string } }> {
  const companyId = await createCompany('Empresa H', 'EH', ['CASE_FILES']);
  const worker = await createWorker(companyId, 'Trabajadora', 'Expediente', 'expediente@demo.invalid');
  const outsider = await createWorker(companyId, 'Trabajador', 'Otro proyecto', 'otro@demo.invalid');
  const olmo = await addProject(companyId, 'Comunidad Olmo 14 expediente', worker.id);
  const caseId = await uploadFile(companyId, CASE_FILES[0]!, 'Caso 2026-101 del expediente', olmo, true);
  const ids: Record<string, string> = {};
  for (const [file, title] of EXPEDIENTE) ids[title] = await uploadFile(companyId, join(FILE_DIR, file), title, olmo);
  const acta = join(mkdtempSync(join(tmpdir(), 'odin-demo-')), 'acta-fuera.txt');
  writeFileSync(acta, 'Acta ficticia fuera del expediente: se habló de la filtración de agua desde la azotea y de la lámina.');
  ids['Acta fuera'] = await uploadFile(companyId, acta, 'Acta fuera del expediente', olmo);
  for (const id of [caseId, ...Object.values(ids)]) await waitReady(companyId, id);
  const body = { documentIds: EXPEDIENTE.map(([, title]) => ids[title]) };
  await json(await http('POST', `/v1/admin/companies/${companyId}/cases/${caseId}/file/documents`, body), 200);
  return { companyId, caseId, ids, worker, outsider };
}

const caseFile = async (key: string, caseId: string): Promise<CaseFileView> => withMcp(key, async (call) => JSON.parse(await call('getCaseFile', { caseId })) as CaseFileView);

async function readCitedStep(key: string, caseId: string, ids: Record<string, string>): Promise<void> {
  await withMcp(key, async (call) => {
    const text = JSON.parse(await call('readCaseFile', { caseId })) as CaseFileText;
    const page = text.pages.find((p) => p.documentId === ids['Informe del perito'] && p.text.includes('Mario Ficticio Lorente'));
    check(!!page, 'Paso 14b: readCaseFile devuelve el texto del informe del perito');
    const v = JSON.parse(await call('verifyCitation', { citations: [{ documentId: page!.documentId, pageNumber: page!.pageNumber, quote: 'Mario Ficticio Lorente' }] })) as Verified;
    check(v.results[0]?.verified === true, 'Paso 14c: verifyCitation confirma una cita tomada de la lectura del expediente');
  });
}

async function searchAndRemoveStep(key: string, caseId: string, ids: Record<string, string>): Promise<void> {
  await withMcp(key, async (call) => {
    const { hits } = JSON.parse(await call('searchKnowledge', { queries: ['filtración de agua azotea lámina'], caseId })) as { hits: Hit[] };
    // la ficha del caso también es parte del expediente que busca caseId
    const inside = new Set([caseId, ...EXPEDIENTE.map(([, title]) => ids[title])]);
    const decoy = hits.some((h) => h.documentId === ids['Acta fuera']);
    check(hits.length > 0 && !decoy && hits.every((h) => inside.has(h.documentId)), 'Paso 14d: searchKnowledge con caseId devuelve solo documentos del expediente (no el acta de fuera)');
    const factura = ids['Factura']!;
    await call('removeFromCaseFile', { caseId, documentId: factura });
    check(!(await caseFile(key, caseId)).documents.some((d) => d.id === factura), 'Paso 14e: removeFromCaseFile quita la factura del expediente');
    await call('addToCaseFile', { caseId, documentIds: [factura] });
    check((await caseFile(key, caseId)).documents.some((d) => d.id === factura), 'Paso 14f: addToCaseFile la vuelve a añadir');
  });
}

async function openOriginalStep(key: string, documentId: string): Promise<void> {
  const link = JSON.parse(await withMcp(key, (call) => call('openOriginal', { documentId, page: 1 }))) as Link;
  const [first, second] = [await fetch(absolute(link.url)), await fetch(absolute(link.url))];
  check(first.status === 200 && first.headers.get('cache-control')?.includes('no-store') === true && first.headers.get('x-content-type-options') === 'nosniff',
    'Paso 15: openOriginal devuelve una URL; el original responde 200 con Cache-Control no-store y nosniff');
  check(!(first.headers.get('content-security-policy') ?? '').includes('sandbox') && (first.headers.get('content-type') ?? '').includes('application/pdf'),
    'Paso 15b: el PDF se sirve sin «Content-Security-Policy: sandbox»');
  check(second.status === 200, 'Paso 15c: una segunda descarga desde la misma dirección sigue funcionando');
}

/** Token de la ruta /subir/{token} del panel; la API lo expone en /v1/upload-links/{token}. */
function uploadLinkToken(url: string): string {
  const parsed = new URL(absolute(url));
  check(parsed.pathname.startsWith('/subir/') && parsed.search === '', 'Paso 16: requestUploadLink devuelve /subir/{token} sin query string');
  return parsed.pathname.split('/').pop() ?? '';
}

async function uploadLinkStep(s: Awaited<ReturnType<typeof seedExpediente>>): Promise<void> {
  const link = JSON.parse(await withMcp(s.worker.key, (call) => call('requestUploadLink', { caseId: s.caseId }))) as Link;
  const token = uploadLinkToken(link.url);
  const info = await fetch(`${PUBLIC_URL}/v1/upload-links/${token}`);
  const body = await info.json().catch(() => ({})) as { caseTitle?: string; projectName?: string };
  check(info.status === 200 && (body.caseTitle ?? '').includes('Caso 2026-101') && (body.projectName ?? '').includes('Comunidad Olmo 14 expediente'),
    'Paso 16: requestUploadLink da un enlace cuya API muestra el caso y el proyecto');
  const form = new FormData();
  form.append('file', new Blob([readFileSync(join(FILE_DIR, 'nota-enlace-ficticia.txt'))], { type: 'text/plain' }), 'nota-enlace-ficticia.txt');
  const up = await fetch(`${PUBLIC_URL}/v1/upload-links/${token}/documents`, { method: 'POST', body: form });
  check(up.status === 200, `Paso 16b: la subida por el enlace responde HTTP 200 (HTTP ${up.status})`);
  const docs = await json<{ id: string; filename: string }[]>(await http('GET', `/v1/admin/companies/${s.companyId}/documents`), 200);
  const id = docs.find((d) => d.filename === 'nota-enlace-ficticia.txt')?.id;
  if (!id) throw new Error('el documento subido por el enlace no aparece');
  await waitReady(s.companyId, id);
  check((await caseFile(s.worker.key, s.caseId)).documents.some((d) => d.id === id), 'Paso 16c: el documento subido por el enlace aparece en el expediente');
}

async function waitStatus(companyId: string, docId: string, wanted: string): Promise<void> {
  for (let i = 0; i < 60; i++) {
    const docs = await json<{ id: string; status: string }[]>(await http('GET', `/v1/admin/companies/${companyId}/documents`), 200);
    if (docs.find((d) => d.id === docId)?.status === wanted) return;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`el documento no llegó a ${wanted} en 60 s`);
}

async function addPhoto(s: Awaited<ReturnType<typeof seedExpediente>>, file: string, title: string): Promise<string> {
  const project = (await json<{ id: string; projectId: string }[]>(await http('GET', `/v1/admin/companies/${s.companyId}/documents`), 200))
    .find((d) => d.id === s.caseId)?.projectId;
  const id = await uploadFile(s.companyId, join(PHOTOS_DIR, file), title, project);
  await json(await http('POST', `/v1/admin/companies/${s.companyId}/cases/${s.caseId}/file/documents`, { documentIds: [id] }), 200);
  return id;
}

async function photosStep(s: Awaited<ReturnType<typeof seedExpediente>>): Promise<void> {
  const key = s.worker.key;
  const bare = await addPhoto(s, 'foto-sin-texto-ficticia.png', 'Foto sin texto');
  await waitStatus(s.companyId, bare, 'READY');
  const view = await caseFile(key, s.caseId);
  const text = await withMcp(key, async (call) => JSON.parse(await call('readCaseFile', { caseId: s.caseId })) as CaseFileText);
  check(view.documents.some((d) => d.id === bare) && view.unreadableDocuments === 0 && text.pages.some((p) => p.documentId === bare && p.unread),
    'Paso 18: una foto sin texto queda READY, sale en getCaseFile con su página sin texto y no cuenta como ilegible');
  const link = JSON.parse(await withMcp(key, (call) => call('openOriginal', { documentId: bare, page: 1 }))) as Link;
  check((await fetch(absolute(link.url))).status === 200, 'Paso 18b: openOriginal de la foto sin texto devuelve 200');
  const retry = await addPhoto(s, 'foto-reintento-ficticia.png', 'Foto de reintento');
  await waitStatus(s.companyId, retry, 'FAILED');
  const failed = await caseFile(key, s.caseId);
  check(failed.unreadableDocuments === 1 && failed.pendingDocuments === 0 && !failed.documents.some((d) => d.id === retry),
    'Paso 18c: la foto que falló en su primera lectura cuenta como ilegible y no se lista');
  await json(await http('POST', `/v1/admin/documents/${retry}/retry`), 200);
  await waitStatus(s.companyId, retry, 'READY');
  const fixed = await caseFile(key, s.caseId);
  check(fixed.unreadableDocuments === 0 && fixed.documents.some((d) => d.id === retry),
    'Paso 18d: tras «Reintentar» la foto queda READY, se lista y ya no hay ilegibles');
}

async function caseFileStep(): Promise<void> {
  const s = await seedExpediente();
  const view = await caseFile(s.worker.key, s.caseId);
  check(EXPEDIENTE.every(([file]) => view.documents.some((d) => d.filename === file && d.status === 'READY')) && view.pendingDocuments === 0,
    'Paso 14: getCaseFile muestra el expediente con sus seis documentos READY y ninguno pendiente');
  await readCitedStep(s.worker.key, s.caseId, s.ids);
  await searchAndRemoveStep(s.worker.key, s.caseId, s.ids);
  await openOriginalStep(s.worker.key, s.ids['Informe del perito']!);
  await uploadLinkStep(s);
  await photosStep(s);
  // errorMessage de mcp/src/tools/result.ts: 404 «No se ha encontrado…»; el ajeno tiene la capacidad, así que la API responde 404
  const denied = await caseFile(s.outsider.key, s.caseId).then(() => '', (e: Error) => e.message);
  check(/no (se ha )?encontrad/i.test(denied), 'Paso 17: un trabajador de otro proyecto recibe un 404 de getCaseFile para ese caso');
}

async function prepare(): Promise<void> {
  const setup = await http('POST', '/v1/setup', { setupToken: SETUP_TOKEN, groupName: 'Grupo Demo', adminName: 'Administrador Demo', adminEmail: ADMIN.email, password: ADMIN.password });
  check(setup.status === 201, `Paso 1: setup inicial del grupo`);
  const login = await http('POST', '/v1/admin/session', ADMIN);
  cookie = (login.headers.get('set-cookie') ?? '').split(';')[0] ?? '';
  check(login.status === 200 && cookie.startsWith('odin_session='), 'Paso 2: sesión del administrador del grupo');
  const a = await createCompany('Empresa A', 'EA');
  const b = await createCompany('Empresa B', 'EB');
  const wa = await createWorker(a, 'Trabajadora', 'A', 'a@demo.invalid');
  const wb = await createWorker(b, 'Trabajador', 'B', 'b@demo.invalid');
  check(!!wa.key && !!wb.key, 'Paso 3: empresas A y B, un trabajador cada una y sus claves personales');
  const docId = await uploadFile(a, PDF, TITLE);
  await waitReady(a, docId);
  check(true, 'Paso 4: PDF ficticio subido a la empresa A y procesado (READY)');
  const found = await searchAsA(wa.key, docId);
  check(!!found.hit.pageNumber && !!found.hit.excerpt, `Paso 5: MCP de A devuelve un resultado del PDF (página ${found.hit.pageNumber})`);
  check(found.verified, 'Paso 5b: verifyCitation confirma el extracto como literal');
  const outB = await withMcp(wb.key, (call) => call('searchKnowledge', { queries: [QUERY] }));
  check(outB === NO_SOURCE, 'Paso 6: MCP de B recibe «No consta en los documentos de tu empresa.»');
  const pageUrl = `${API}/v1/documents/${docId}/pages/${found.hit.pageNumber}`;
  const own = await fetch(pageUrl, { headers: { Authorization: `Bearer ${wa.key}` } });
  const ownText = own.status === 200 ? ((await own.json()) as { text: string }).text : '';
  check(own.status === 200 && ownText.includes('ATL-4417-2024'), 'Paso 6a: control positivo, el trabajador de A lee esa misma página (200, texto de la póliza)');
  const page = await fetch(pageUrl, { headers: { Authorization: `Bearer ${wb.key}` } });
  check(page.status === 404, 'Paso 6b: el trabajador de B no puede leer la página del PDF de A (404)');
  const saved = await withMcp(wa.key, (call) => call('saveKnowledgeCard', CARD));
  check(saved.includes('PENDING') && saved.includes('Ficha guardada; queda pendiente de revisión.'), 'Paso 7: MCP de A guarda la ficha, queda PENDING');
  await closedProjectStep();
  const renewedId = await uploadFile(a, RENEWED, RENEWED_TITLE);
  await waitReady(a, renewedId);
  check(true, 'Paso 7d: póliza ficticia renovada subida a la empresa A y procesada (READY)');
  await scannedStep();
  await taggingStep();
  await similarCasesStep();
  await authorStep();
  await caseFileStep();
  writeFileSync(STATE, JSON.stringify({ companyId: a, cardTitle: CARD.title, admin: ADMIN, sessionCookie: cookie, workerKey: wa.key, oldId: docId, renewedId }));
}

async function confirm(): Promise<void> {
  const { companyId } = JSON.parse(readFileSync(STATE, 'utf8')) as { companyId: string };
  cookie = (await http('POST', '/v1/admin/session', ADMIN).then((r) => r.headers.get('set-cookie') ?? '')).split(';')[0] ?? '';
  const cards = await json<{ title: string; status: string }[]>(await http('GET', `/v1/admin/companies/${companyId}/cards?status=APPROVED`), 200);
  check(cards.some((c) => c.title === CARD.title), 'Paso 9: la API confirma que la ficha está APPROVED');
  await confirmSuperseded(JSON.parse(readFileSync(STATE, 'utf8')) as State);
}

type State = { workerKey: string; oldId: string; renewedId: string };
type DocHit = { documentId: string; superseded?: boolean; replacement?: { documentId: string; title: string } | null };

async function confirmSuperseded({ workerKey, oldId, renewedId }: State): Promise<void> {
  const { hits } = await withMcp(workerKey, async (call) => JSON.parse(await call('searchKnowledge', { queries: [QUERY] })) as { hits: DocHit[] });
  const firstSuperseded = hits.findIndex((h) => h.superseded);
  const lastCurrent = hits.map((h) => !h.superseded).lastIndexOf(true);
  const oldHits = hits.filter((h) => h.documentId === oldId);
  check(hits.some((h) => h.documentId === renewedId) && firstSuperseded > lastCurrent, 'Paso 10: la renovada sale y ningún sustituido sale delante de un vigente');
  check(oldHits.length > 0 && oldHits.every((h) => h.superseded === true && h.replacement?.documentId === renewedId && h.replacement.title === RENEWED_TITLE),
    'Paso 10b: cada resultado de la póliza antigua sale marcado como sustituido y con la renovada como sustituto');
}

const phase = process.argv[2];
(phase === 'prepare' ? prepare() : phase === 'confirm' ? confirm() : Promise.reject(new Error('uso: demo-client.ts prepare|confirm')))
  .catch((e: Error) => { console.error(`✗ ${e.message}`); process.exit(1); });
