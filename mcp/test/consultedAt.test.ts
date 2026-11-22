import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi } from './fakeApi.js';

let fake: FakeApi;
afterEach(() => fake?.close());

const ARGS: Record<string, Record<string, unknown>> = {
  searchKnowledge: { queries: ['póliza'] },
  readPage: { documentId: 'd1', pageNumber: 1 },
  listDocuments: {},
  verifyCitation: { citations: [{ documentId: 'd1', pageNumber: 1, quote: 'x' }] },
  findSimilarCases: { queries: ['fuga'] },
  getCaseFile: { caseId: 'c1' },
  readCaseFile: { caseId: 'c1' },
  listProjects: {},
  listMyQuestions: {},
  listMyKnowledgeCards: {},
  saveKnowledgeCard: { title: 't', body: 'b' },
  requestUploadLink: { caseId: 'c1' },
  openOriginal: { documentId: 'd1' },
  addToCaseFile: { caseId: 'c1', documentIds: ['d1'] },
  removeFromCaseFile: { caseId: 'c1', documentId: 'd1' },
};

async function connect(now: () => Date) {
  fake = await startFakeApi((r) => (r.url === '/v1/me' ? meWith(['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD', 'CASE_FILES', 'UPLOAD_DOCUMENTS', 'SIMILAR_CASES']) : { status: 500, body: {} }));
  const server = await buildServer(createApiClient(fake.url, 'clave-secreta-1'), now);
  const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
  return client;
}

const stampOf = (result: unknown) => {
  const blocks = (result as { content: { text: string }[] }).content;
  return JSON.parse(blocks[blocks.length - 1]!.text).consultedAt as string;
};

describe('consultedAt (D-126, criterio 14)', () => {
  it('todas las herramientas registradas dicen cuándo se consultó, también en error', async () => {
    const client = await connect(() => new Date('2026-11-22T10:00:00.000Z'));
    const tools = (await client.listTools()).tools;
    expect(tools.length).toBeGreaterThan(10);
    for (const tool of tools) {
      expect(ARGS[tool.name], `falta ARGS para ${tool.name}`).toBeDefined();
      const result = await client.callTool({ name: tool.name, arguments: ARGS[tool.name]! });
      expect(stampOf(result), tool.name).toBe('2026-11-22T10:00:00.000Z');
    }
  });

  it('consultedAt cambia entre dos llamadas seguidas', async () => {
    let n = 0;
    const client = await connect(() => new Date(Date.UTC(2026, 10, 22, 10, 0, n++)));
    const first = stampOf(await client.callTool({ name: 'listProjects', arguments: {} }));
    const second = stampOf(await client.callTool({ name: 'listProjects', arguments: {} }));
    expect(first).not.toBe(second);
  });

  it('listActivity (conector de administración) también lo dice', async () => {
    fake = await startFakeApi(() => ({ status: 200, body: { userId: 'u1', displayName: 'Ana', role: 'ADMIN', kind: 'ADMIN_CHAT', projects: [], topics: [], tagging: { pendingDocuments: 0 } } }));
    const server = await buildServer(createApiClient(fake.url, 'clave-secreta-1'), () => new Date('2026-11-22T10:00:00.000Z'));
    const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
    const client = new Client({ name: 'test', version: '1' });
    await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
    expect((await client.listTools()).tools.map((t) => t.name)).toEqual(['listActivity']);
    expect(stampOf(await client.callTool({ name: 'listActivity', arguments: { from: '2026-11-20T00:00:00Z', to: '2026-11-21T00:00:00Z' } }))).toBe('2026-11-22T10:00:00.000Z');
  });

  const FRESH = 'Los datos de Odín no se responden de memoria: toda respuesta sobre el estado o el contenido de un documento sale de una llamada hecha para esa respuesta, aunque la conversación ya lo haya leído; lo leído antes son solo pistas, porque un compañero pudo cambiar los datos; consultedAt dice cuándo se leyó cada resultado.';
  const describe_ = async () => {
    const client = await connect(() => new Date());
    return new Map((await client.listTools()).tools.map((t) => [t.name, t.description ?? '']));
  };

  it('las herramientas que leen datos de Odín traen la regla de no responder de memoria', async () => {
    const tools = await describe_();
    for (const name of ['searchKnowledge', 'readPage', 'listDocuments', 'verifyCitation', 'getCaseFile', 'readCaseFile', 'findSimilarCases']) {
      expect(tools.get(name), name).toContain(FRESH);
    }
  });

  it('verifyCitation: la cita solo vale con la comprobación hecha en esa respuesta', async () => {
    const d = (await describe_()).get('verifyCitation')!;
    expect(d).toContain('Una cita solo se da por buena con la comprobación hecha en esa misma respuesta');
    expect(d).toContain('lectura nueva del estado del documento (superseded, corrected)');
  });

  it('searchKnowledge y listDocuments: antes de afirmar una ausencia, consulta nueva', async () => {
    const tools = await describe_();
    for (const name of ['searchKnowledge', 'listDocuments']) {
      expect(tools.get(name), name).toContain('Antes de afirmar una ausencia o un estado general («no hay otra póliza vigente», «ninguna está sustituida», «no consta»), haz una consulta nueva en esa respuesta, aunque la conversación ya haya leído algo parecido.');
    }
  });
});
