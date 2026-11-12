import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer, INSTRUCTIONS } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi, type Handler } from './fakeApi.js';

const P1 = { id: 'p1', name: 'Comunidad Sol' };
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
const toolNames = async (caps: string[]) => (await (await connect(() => meWith(caps))).listTools()).tools.map((t) => t.name);
const descriptionOf = async (caps: string[], name: string) =>
  (await (await connect(() => meWith(caps))).listTools()).tools.find((t) => t.name === name)?.description ?? '';

afterEach(() => fake?.close());

describe('listMyKnowledgeCards', () => {
  it('registeredOnlyWithCapability', async () => {
    expect(await toolNames(['SAVE_KNOWLEDGE_CARD'])).toContain('listMyKnowledgeCards');
    await fake.close();
    expect(await toolNames(['SEARCH_KNOWLEDGE'])).not.toContain('listMyKnowledgeCards');
  });

  it('sendsStatusFilterAndMapsCards', async () => {
    const card = { id: 'k1', title: 'Junta', body: 'Texto corregido', originalBody: 'Texto enviado', projectId: 'p1',
      createdAt: '2026-01-01T00:00:00Z', status: 'APPROVED', review: { decision: 'CORRECT', note: 'Ajustada', reviewedAt: '2026-01-02T00:00:00Z' } };
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(['SAVE_KNOWLEDGE_CARD', 'SEARCH_KNOWLEDGE'], 'E', [P1]) : { status: 200, body: [card] }));
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'listMyKnowledgeCards', arguments: { status: 'REJECTED' } })));
    expect(fake.requests.at(-1)!.url).toBe('/v1/me/cards?status=REJECTED');
    expect(parsed.cards[0]).toEqual({ cardId: 'k1', title: 'Junta', body: 'Texto corregido', originalBody: 'Texto enviado',
      projectId: 'p1', projectName: 'Comunidad Sol', createdAt: '2026-01-01T00:00:00Z', status: 'APPROVED', review: card.review });
    await client.callTool({ name: 'listMyKnowledgeCards', arguments: {} });
    expect(fake.requests.at(-1)!.url).toBe('/v1/me/cards');
  });

  it('descriptionCarriesTheReviewPhrases', async () => {
    const description = await descriptionOf(['SAVE_KNOWLEDGE_CARD'], 'listMyKnowledgeCards');
    for (const phrase of [
      'Lista las fichas que has guardado y en qué estado está su revisión. Si una está rechazada, enseña la nota del revisor tal cual.',
      'Si una ficha tuya tiene la decisión CORRECT, preséntala como corregida por el administrador y enseña los dos textos: el que enviaste y el corregido.',
      'Si una ficha tuya está rechazada, preséntala como rechazada y enseña la nota del revisor tal cual.',
    ]) expect(description).toContain(phrase);
  });

  it('saveKnowledgeCardPointsToTheList', async () => {
    expect(await descriptionOf(['SAVE_KNOWLEDGE_CARD'], 'saveKnowledgeCard')).toContain('Puedes ver su estado con listMyKnowledgeCards.');
  });

  it('instructionsStayUnchangedAndUnderBudget', () => {
    expect(INSTRUCTIONS.length).toBeLessThanOrEqual(2200);
    expect(INSTRUCTIONS).not.toContain('CORRECT');
  });
});

describe('listMyQuestions', () => {
  it('registeredOnlyWithCapability', async () => {
    expect(await toolNames(['SEARCH_KNOWLEDGE'])).toContain('listMyQuestions');
    await fake.close();
    expect(await toolNames(['SAVE_KNOWLEDGE_CARD'])).not.toContain('listMyQuestions');
  });

  it('sendsLimitAndReturnsQuestions', async () => {
    const questions = [{ text: 'franquicia', kind: 'SEARCH', hitCount: 2, askedAt: '2026-01-01T00:00:00Z' }];
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(['SEARCH_KNOWLEDGE']) : { status: 200, body: questions }));
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'listMyQuestions', arguments: { limit: 5 } })));
    expect(fake.requests.at(-1)!.url).toBe('/v1/me/questions?limit=5');
    expect(parsed).toEqual({ questions });
    await client.callTool({ name: 'listMyQuestions', arguments: {} });
    expect(fake.requests.at(-1)!.url).toBe('/v1/me/questions');
  });

  it('descriptionSaysWhoseTextItIs', async () => {
    const description = await descriptionOf(['SEARCH_KNOWLEDGE'], 'listMyQuestions');
    expect(description).toContain('Lista las consultas que tu IA ha hecho al cerebro con tu clave.');
    expect(description).toContain('no lo que escribiste tú');
  });
});
