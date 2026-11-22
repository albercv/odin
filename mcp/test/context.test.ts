import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi, type Handler } from './fakeApi.js';

const CAPS = ['SEARCH_KNOWLEDGE', 'CASE_FILES', 'SIMILAR_CASES'];
let fake: FakeApi;

async function connect(handler: Handler) {
  fake = await startFakeApi((r) => (r.url === '/v1/me' ? meWith(CAPS) : handler(r)));
  const server = await buildServer(createApiClient(fake.url, 'clave'));
  const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
  return client;
}
const call = async (client: Client, name: string, args: Record<string, unknown>) =>
  JSON.parse(((await client.callTool({ name, arguments: args })) as { content: { text: string }[] }).content[0]!.text);
afterEach(() => fake?.close());

const version = { documentId: 'd1', title: 'Póliza 1', current: false, link: null, heading: { page: 1, text: 'Efecto: 1 de octubre de 2016' }, match: { page: 2, excerpt: 'franquicia' } };
const correction = { direction: 'correctedBy', relation: 'RECTIFIES', documentId: 'd9', title: 'Rectificativa', hidden: false, evidencePage: 1, evidenceQuote: 'Rectifica la factura X-1.', confirmed: false };
const hidden = { direction: 'correctedBy', relation: 'RECTIFIES', documentId: null, title: null, hidden: true, evidencePage: null, evidenceQuote: null, confirmed: null };
const context = { versions: [version], corrections: [correction, hidden], incomplete: true };

describe('versiones y correcciones en la salida para el modelo', () => {
  it('searchKnowledgePassesThemForDocumentsAndNotForCards', async () => {
    const doc = { sourceType: 'ORIGINAL_DOCUMENT', sourceId: 'd1', title: 'T', pageNumber: 1, paginated: true, authorName: 'Ana',
      date: '2026-01-01T00:00:00Z', excerpt: 'x', rank: 1, superseded: false, ...context };
    const card = { ...doc, sourceType: 'KNOWLEDGE_CARD', sourceId: 'k1', paginated: false };
    const client = await connect(() => ({ status: 200, body: { hits: [doc, card], noSource: false } }));
    const parsed = await call(client, 'searchKnowledge', { queries: ['x'] });
    expect(parsed.hits[0]).toMatchObject(context);
    expect(parsed.hits[1]).not.toHaveProperty('versions');
    expect(parsed.hits[1]).not.toHaveProperty('corrections');
  });

  it('readPagePassesThem', async () => {
    const body = { documentId: 'd1', title: 'T', pageNumber: 1, paginated: true, text: 'x', superseded: false, ...context };
    const client = await connect(() => ({ status: 200, body }));
    expect(await call(client, 'readPage', { documentId: 'd1', pageNumber: 1 })).toMatchObject(context);
  });

  it('caseFileToolsPassCorrectionsAndIncompletePerDocument', async () => {
    const entry = { id: 'd1', superseded: true, replacement: { documentId: 'd2', title: 'N' }, corrections: [correction], incomplete: true };
    const client = await connect((r) => ({ status: 200, body: r.url.endsWith('/text')
      ? { pages: [], complete: true, documents: [{ documentId: 'd1', superseded: true, replacement: { documentId: 'd2', title: 'N' }, corrections: [correction], incomplete: true }] }
      : { documents: [entry], pendingDocuments: 0 } }));
    expect((await call(client, 'getCaseFile', { caseId: 'c1' })).documents[0]).toMatchObject(entry);
    expect((await call(client, 'readCaseFile', { caseId: 'c1' })).documents[0]).toMatchObject({ superseded: true, corrections: [correction], incomplete: true });
  });

  it('verifyCitationPassesSupersededReplacementAndCorrected', async () => {
    const result = { documentId: 'd1', pageNumber: 1, verified: true, superseded: true, replacement: { documentId: 'd2', title: 'N' }, corrected: true, corrections: [correction] };
    const client = await connect(() => ({ status: 200, body: { results: [result] } }));
    const parsed = await call(client, 'verifyCitation', { citations: [{ documentId: 'd1', pageNumber: 1, quote: 'q' }] });
    expect(parsed.results[0]).toMatchObject({ ...result, quote: 'q' });
  });

  it('listDocumentsPassesCorrectedAndTheCount', async () => {
    const client = await connect(() => ({ status: 200, body: [{ id: 'd1', title: 'T', corrected: true, correctionCount: 2 }] }));
    expect((await call(client, 'listDocuments', {})).documents[0]).toMatchObject({ corrected: true, correctionCount: 2 });
  });

  it('findSimilarCasesPassesCorrections', async () => {
    const view = { sourceType: 'ORIGINAL_DOCUMENT', sourceId: 'd1', title: 'T', addedAt: '2026-01-01T00:00:00Z', pageCount: 1, pages: [],
      truncated: false, excerpt: 'x', origin: 'ORIGINAL_DOCUMENT', superseded: false, corrections: [correction] };
    const client = await connect(() => ({ status: 200, body: { cases: [view], noSource: false } }));
    expect((await call(client, 'findSimilarCases', { queries: ['x'] })).cases[0]).toMatchObject({ corrections: [correction] });
  });

  // P2: otra póliza dice «sustituye y anula a» P2, pero nadie la marcó; P1 sí está marcada y sustituida por P2.
  describe('superseded y replacement se copian tal cual del API, sin deducirlos del texto', () => {
    const p2 = { superseded: false, replacement: null, corrections: [{ ...correction, relation: 'REPLACES', title: 'P3' }], incomplete: true };
    const p1 = { superseded: true, replacement: { documentId: 'p2', title: 'P2' }, corrections: [], incomplete: true };
    const hit = (extra: object) => ({ sourceType: 'ORIGINAL_DOCUMENT', sourceId: 'p2', title: 'P2', pageNumber: 1, paginated: true,
      authorName: 'Ana', date: '2026-01-01T00:00:00Z', excerpt: 'Esta póliza sustituye y anula a la póliza n.º P1', rank: 1, versions: [], ...extra });

    it('searchKnowledge', async () => {
      const client = await connect(() => ({ status: 200, body: { hits: [hit(p2), hit({ sourceId: 'p1', ...p1 })], noSource: false } }));
      const parsed = await call(client, 'searchKnowledge', { queries: ['póliza'] });
      expect(parsed.hits[0]).toMatchObject({ superseded: false, replacement: null, incomplete: true });
      expect(parsed.hits[1]).toMatchObject({ superseded: true, replacement: { documentId: 'p2' } });
    });

    it('readPage y verifyCitation', async () => {
      const client = await connect((r) => ({ status: 200, body: r.url.includes('verify')
        ? { results: [{ documentId: 'p2', pageNumber: 1, verified: true, corrected: true, ...p2 }] }
        : { documentId: 'p2', title: 'P2', pageNumber: 1, paginated: true, text: 'sustituye y anula a P1', ...p2 } }));
      expect(await call(client, 'readPage', { documentId: 'p2', pageNumber: 1 })).toMatchObject({ superseded: false, replacement: null });
      const verified = await call(client, 'verifyCitation', { citations: [{ documentId: 'p2', pageNumber: 1, quote: 'q' }] });
      expect(verified.results[0]).toMatchObject({ superseded: false, replacement: null });
    });

    it('getCaseFile, readCaseFile y listDocuments', async () => {
      const client = await connect((r) => ({ status: 200, body: r.url.endsWith('/text')
        ? { pages: [], complete: true, documents: [{ documentId: 'p2', ...p2 }] }
        : r.url.includes('/file') ? { documents: [{ id: 'p2', ...p2 }, { id: 'p1', ...p1 }], pendingDocuments: 0 }
          : [{ id: 'p2', title: 'P2', superseded: false, corrected: true, correctionCount: 1 }] }));
      const file = await call(client, 'getCaseFile', { caseId: 'c1' });
      expect(file.documents[0]).toMatchObject({ superseded: false, replacement: null });
      expect(file.documents[1]).toMatchObject({ superseded: true, replacement: { documentId: 'p2' } });
      expect((await call(client, 'readCaseFile', { caseId: 'c1' })).documents[0]).toMatchObject({ superseded: false, replacement: null });
      expect((await call(client, 'listDocuments', {})).documents[0]).toMatchObject({ superseded: false, corrected: true });
    });
  });
});
