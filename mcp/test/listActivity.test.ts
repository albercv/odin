import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi, type Handler } from './fakeApi.js';

let fake: FakeApi;
afterEach(() => fake?.close());

async function connect(handler: Handler) {
  fake = await startFakeApi(handler);
  const server = await buildServer(createApiClient(fake.url, 'odin-adm_abcdefgh_secreto'));
  const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
  return client;
}

const adminMe = (company: unknown = null) => ({
  status: 200,
  body: { kind: 'ADMIN_CHAT', userId: 'u1', displayName: 'Ana', role: 'GROUP_ADMIN', company, projects: [], topics: [], tagging: { pendingDocuments: 0 } },
});
const textOf = (result: unknown) => (result as { content: { text: string }[] }).content[0]!.text;
const names = async (client: Client) => (await client.listTools()).tools.map((t) => t.name);
const RANGE = { from: '2026-11-01T00:00:00Z', to: '2026-11-08T00:00:00Z' };

describe('listActivity', () => {
  it('con clave del chat registra solo listActivity, aunque la empresa sea nula', async () => {
    const client = await connect(() => adminMe());
    expect(await names(client)).toEqual(['listActivity']);
  });

  it('con clave de trabajador no registra listActivity y deja el conjunto de siempre', async () => {
    const client = await connect(() => meWith(['SEARCH_KNOWLEDGE']));
    const tools = await names(client);
    expect(tools).not.toContain('listActivity');
    expect(tools).toContain('searchKnowledge');
  });

  it('rechaza más de 7 días sin llamar a la API', async () => {
    const client = await connect(() => adminMe());
    const result = await client.callTool({ name: 'listActivity', arguments: { from: RANGE.from, to: '2026-11-08T00:00:01Z' } });
    expect(result.isError).toBe(true);
    expect(textOf(result)).toContain('7 días');
    expect(fake.requests.filter((r) => r.url.startsWith('/v1/activity'))).toEqual([]);
  });

  it('reenvía los parámetros y devuelve filas y nextCursor', async () => {
    const client = await connect((r) => (r.url.startsWith('/v1/activity') ? { status: 200, body: { items: [{ id: 'e1' }], nextCursor: 'c2' } } : adminMe()));
    const result = await client.callTool({
      name: 'listActivity',
      arguments: { ...RANGE, companyId: 'c1', projectId: 'p1', action: 'DOCUMENT_UPLOADED', actorUserId: 'a1', channel: 'PANEL', outcome: 'DONE', cursor: 'c1' },
    });
    expect(JSON.parse(textOf(result))).toEqual({ items: [{ id: 'e1' }], nextCursor: 'c2' });
    const url = new URL('http://x' + fake.requests.find((r) => r.url.startsWith('/v1/activity'))!.url);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      from: RANGE.from, to: RANGE.to, companyId: 'c1', projectId: 'p1', action: 'DOCUMENT_UPLOADED', actorUserId: 'a1',
      channel: 'PANEL', outcome: 'DONE', cursor: 'c1',
    });
  });

  it.each([
    [429, 'Has llegado al tope diario de 500 entradas'],
    [403, 'fuera de tu ámbito'],
    [409, 'consulta desde el chat'],
    [400, 'no es válida'],
  ])('traduce el %i a un mensaje claro', async (status, expected) => {
    const client = await connect((r) => (r.url.startsWith('/v1/activity') ? { status, body: {} } : adminMe()));
    const result = await client.callTool({ name: 'listActivity', arguments: RANGE });
    expect(result.isError).toBe(true);
    expect(textOf(result)).toContain(expected);
  });

  it('la descripción no valora ni cuenta personas (D-005)', async () => {
    const client = await connect(() => adminMe());
    const d = (await client.listTools()).tools[0]!.description!;
    expect(d).toContain('no valores, compares, ordenes ni cuentes a las personas');
    expect(d).toContain('7 días');
  });
});
