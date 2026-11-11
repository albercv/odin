// Demostración de cierre de la fase 1 (spec 3.1). Fases: `prepare` (pasos 1-7c) y `confirm` (paso 9).
// El paso 8 (panel) lo hace Playwright en panel/e2e/demo.spec.ts entre ambas fases.
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const API = process.env.DEMO_API_URL ?? 'http://127.0.0.1:18080';
const SETUP_TOKEN = need('DEMO_SETUP_TOKEN');
const STATE = need('DEMO_STATE');
const PDF = need('DEMO_PDF');
const ADMIN = { email: 'admin@demo.invalid', password: 'demo-clave-ficticia-1234' };
const QUERY = 'franquicia daños por agua ATL-4417-2024';
const NO_SOURCE = 'No consta en los documentos de tu empresa.';
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
async function createCompany(name: string, code: string): Promise<string> {
  const c = await json<Id>(await http('POST', '/v1/admin/companies', { name, code }), 201);
  const caps = ['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD'];
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
async function uploadPdf(companyId: string, projectId?: string): Promise<string> {
  const form = new FormData();
  form.append('file', new Blob([readFileSync(PDF)], { type: 'application/pdf' }), 'poliza-ficticia.pdf');
  const scope = projectId ? `&projectId=${projectId}` : '';
  const r = await http('POST', `/v1/admin/companies/${companyId}/documents?title=${encodeURIComponent('Póliza ficticia')}${scope}`, form);
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
    const v = JSON.parse(await call('verifyCitation', { documentId: hit.documentId, pageNumber: hit.pageNumber, quote })) as { verified: boolean };
    return { hit, verified: v.verified === true };
  });
}

async function closedProjectStep(): Promise<void> {
  const c = await createCompany('Empresa C', 'EC');
  const assigned = await createWorker(c, 'Trabajadora', 'Asignada', 'asignada@demo.invalid');
  const outsider = await createWorker(c, 'Trabajador', 'Ajeno', 'ajeno@demo.invalid');
  const project = await json<Id>(await http('POST', `/v1/admin/companies/${c}/projects`, { name: 'Proyecto cerrado' }), 201);
  const assign = await http('PUT', `/v1/admin/companies/${c}/projects/${project.id}/members/${assigned.id}`);
  if (assign.status !== 204) throw new Error(`HTTP ${assign.status} al asignar el proyecto: ${await assign.text()}`);
  const docId = await uploadPdf(c, project.id);
  await waitReady(c, docId);
  const found = await searchAsA(assigned.key, docId);
  check(found.verified, 'Paso 7b: el trabajador asignado al proyecto cerrado encuentra su documento y la cita se verifica');
  const outBlind = await withMcp(outsider.key, (call) => call('searchKnowledge', { queries: [QUERY] }));
  check(outBlind === NO_SOURCE, 'Paso 7c: el trabajador no asignado recibe «No consta en los documentos de tu empresa.»');
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
  const docId = await uploadPdf(a);
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
  writeFileSync(STATE, JSON.stringify({ companyId: a, cardTitle: CARD.title, admin: ADMIN, sessionCookie: cookie }));
}

async function confirm(): Promise<void> {
  const { companyId } = JSON.parse(readFileSync(STATE, 'utf8')) as { companyId: string };
  cookie = (await http('POST', '/v1/admin/session', ADMIN).then((r) => r.headers.get('set-cookie') ?? '')).split(';')[0] ?? '';
  const cards = await json<{ title: string; status: string }[]>(await http('GET', `/v1/admin/companies/${companyId}/cards?status=APPROVED`), 200);
  check(cards.some((c) => c.title === CARD.title), 'Paso 9: la API confirma que la ficha está APPROVED');
}

const phase = process.argv[2];
(phase === 'prepare' ? prepare() : phase === 'confirm' ? confirm() : Promise.reject(new Error('uso: demo-client.ts prepare|confirm')))
  .catch((e: Error) => { console.error(`✗ ${e.message}`); process.exit(1); });
