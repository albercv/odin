import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi, type Handler } from './fakeApi.js';

const ALL = ['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD'];
let fake: FakeApi;

async function connect(handler: Handler, key = 'clave-secreta-1') {
  fake = await startFakeApi(handler);
  const server = await buildServer(createApiClient(fake.url, key));
  const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
  return client;
}

const textOf = (result: unknown) => (result as { content: { text: string }[] }).content[0]!.text;

afterEach(() => fake?.close());

describe('buildServer', () => {
  it('registersOnlyEnabledCapabilities', async () => {
    const onlySave = await connect(() => meWith(['SAVE_KNOWLEDGE_CARD']));
    expect((await onlySave.listTools()).tools.map((t) => t.name)).toEqual(['saveKnowledgeCard']);
    await fake.close();
    const all = await connect(() => meWith(ALL));
    expect((await all.listTools()).tools.map((t) => t.name).sort()).toEqual(
      ['listDocuments', 'readPage', 'saveKnowledgeCard', 'searchKnowledge', 'verifyCitation'],
    );
    expect(all.getInstructions()).toContain('verifyCitation');
  });

  it('searchMapsHitsWithCitation', async () => {
    const hit = { sourceType: 'ORIGINAL_DOCUMENT', sourceId: 'd1', title: 'Manual', pageNumber: 3, paginated: true,
      authorName: 'Ana', date: '2026-01-01T00:00:00Z', excerpt: 'el <b>plazo</b>', rank: 0.5 };
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(ALL) : { status: 200, body: { hits: [hit], noSource: false } }));
    const result = await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['plazo'], limit: 5 } });
    const parsed = JSON.parse(textOf(result));
    expect(parsed.hits[0]).toMatchObject({ documentId: 'd1', title: 'Manual', pageNumber: 3, excerpt: 'el <b>plazo</b>' });
    expect(JSON.parse(fake.requests.at(-1)!.body)).toEqual({ queries: ['plazo'], limit: 5 });
  });

  it('noSourceMessage', async () => {
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(ALL) : { status: 200, body: { hits: [], noSource: true } }));
    const result = await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['nada'] } });
    expect(textOf(result)).toBe('No consta en los documentos de tu empresa.');
    expect(result.isError).toBeFalsy();
  });

  it('listDocumentsPagesUntilEnd', async () => {
    const docs = (n: number) => Array.from({ length: n }, (_, i) => ({ id: `d${i}`, title: `T${i}` }));
    const client = await connect((r) => {
      if (r.url === '/v1/me') return meWith(ALL);
      const offset = Number(new URL(r.url, 'http://x').searchParams.get('offset'));
      return { status: 200, body: docs(offset >= 1000 ? 200 : 500) };
    });
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'listDocuments', arguments: {} })));
    expect(parsed.documents).toHaveLength(1200);
    expect(parsed.truncated).toBe(false);
    expect(fake.requests.filter((r) => r.url.startsWith('/v1/documents')).map((r) => r.url)).toEqual([
      '/v1/documents?offset=0&limit=500', '/v1/documents?offset=500&limit=500', '/v1/documents?offset=1000&limit=500',
    ]);
  });

  it('listDocumentsTruncatesAtLimit', async () => {
    const client = await connect((r) =>
      r.url === '/v1/me' ? meWith(ALL) : { status: 200, body: Array.from({ length: 500 }, (_, i) => ({ id: `d${i}` })) });
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'listDocuments', arguments: {} })));
    expect(parsed.documents).toHaveLength(2000);
    expect(parsed.truncated).toBe(true);
  });

  it('apiErrorBecomesToolError', async () => {
    const client = await connect((r) => r.url === '/v1/me' ? meWith(ALL) : { status: 403, body: { detail: 'Tu empresa no tiene activada esta función' } });
    const result = await client.callTool({ name: 'saveKnowledgeCard', arguments: { title: 't', body: 'b' } });
    expect(result.isError).toBe(true);
    expect(textOf(result)).toContain('Tu empresa no tiene activada esta función');
    const notFound = await connect((r) => r.url === '/v1/me' ? meWith(ALL) : { status: 404, body: { detail: 'Documento no encontrado' } });
    const missing = await notFound.callTool({ name: 'readPage', arguments: { documentId: 'x', pageNumber: 1 } });
    expect(missing.isError).toBe(true);
    expect(textOf(missing)).toContain('Documento no encontrado');
  });

  it('saveKnowledgeCardReturnsApiMessage', async () => {
    const message = 'Ficha guardada; queda pendiente de revisión.';
    const client = await connect((r) => r.url === '/v1/me' ? meWith(ALL) : { status: 201, body: { id: 'k1', status: 'PENDING', message } });
    const result = await client.callTool({ name: 'saveKnowledgeCard', arguments: { title: 't', body: 'b' } });
    expect(textOf(result)).toContain(message);
  });

  it('revokedKeyMidSessionReturnsError', async () => {
    let revoked = false;
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(ALL) : revoked ? { status: 401, body: { detail: 'x' } } : { status: 200, body: { verified: true } }));
    const args = { documentId: 'd1', pageNumber: 1, quote: 'q' };
    expect(JSON.parse(textOf(await client.callTool({ name: 'verifyCitation', arguments: args }))).verified).toBe(true);
    revoked = true;
    const result = await client.callTool({ name: 'verifyCitation', arguments: args });
    expect(result.isError).toBe(true);
    expect(textOf(result)).toContain('revocada');
    expect(textOf(result)).not.toContain('clave-secreta-1');
  });
});
