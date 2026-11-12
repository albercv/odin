import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi, type Handler, type MeExtras } from './fakeApi.js';

const ALL = ['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD'];
const TOPICS = [
  { id: 't1', name: 'Agua y humedades', definition: 'Fugas' },
  { id: 't2', name: 'Seguros', definition: 'Pólizas' },
];
let fake: FakeApi;

async function connect(handler: Handler) {
  fake = await startFakeApi(handler);
  const server = await buildServer(createApiClient(fake.url, 'clave-secreta-1'));
  const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
  return client;
}

const textOf = (result: unknown) => (result as { content: { text: string }[] }).content[0]!.text;
const withTopics = (extras: MeExtras = {}): Handler => (r) =>
  r.url === '/v1/me' ? meWith(ALL, 'Empresa Uno', [], { topics: TOPICS, ...extras }) : { status: 200, body: [{ id: 'd1', title: 'T' }] };

afterEach(() => fake?.close());

describe('listDocuments by topic', () => {
  it('translatesTopicNameToId', async () => {
    const client = await connect(withTopics());
    await client.callTool({ name: 'listDocuments', arguments: { topic: 'agua y humedades' } });
    await client.callTool({ name: 'listDocuments', arguments: { topic: 'SEGUROS' } });
    expect(fake.requests.filter((r) => r.url.startsWith('/v1/documents')).map((r) => r.url)).toEqual([
      '/v1/documents?offset=0&limit=500&topicId=t1', '/v1/documents?offset=0&limit=500&topicId=t2',
    ]);
  });

  it('unknownTopicListsValidOnes', async () => {
    const client = await connect(withTopics());
    const result = await client.callTool({ name: 'listDocuments', arguments: { topic: 'electricidad' } });
    expect(result.isError).toBe(true);
    expect(textOf(result)).toContain('electricidad');
    expect(textOf(result)).toContain('Agua y humedades; Seguros');
    expect(fake.requests.filter((r) => r.url.startsWith('/v1/documents'))).toEqual([]);
  });

  it('reportsTaggingPending', async () => {
    const client = await connect(withTopics({ pendingDocuments: 3 }));
    const byTopic = JSON.parse(textOf(await client.callTool({ name: 'listDocuments', arguments: { topic: 'Seguros' } })));
    expect(byTopic.taggingPending).toBe(3);
    const plain = JSON.parse(textOf(await client.callTool({ name: 'listDocuments', arguments: {} })));
    expect(plain).not.toHaveProperty('taggingPending');
  });

  it('descriptionCarriesTheTopicPhrases', async () => {
    const client = await connect(withTopics());
    const tool = (await client.listTools()).tools.find((t) => t.name === 'listDocuments');
    expect(tool?.description).toContain('Los temas de origen AI los asignó una IA al ingresar el documento; preséntalos como tales.');
    expect(tool?.description).toContain('Una lista por tema puede estar incompleta: si taggingPending es mayor que 0, dilo.');
    expect(tool?.description).toContain(
      'Para una lista completa, combina el filtro por tema con la búsqueda, porque el etiquetado puede dejarse alguno.');
    const topic = (tool?.inputSchema.properties as Record<string, { description?: string }>).topic;
    expect(topic?.description).toContain('nombre');
  });
});
