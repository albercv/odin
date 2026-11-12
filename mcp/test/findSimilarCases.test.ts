import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi, type Handler } from './fakeApi.js';

const WITH_CASES = ['SEARCH_KNOWLEDGE', 'SIMILAR_CASES'];
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
const caseView = {
  sourceType: 'ORIGINAL_DOCUMENT', sourceId: 'd1', title: 'Filtración azotea', projectId: 'p1', addedAt: '2026-01-01T00:00:00Z',
  pageCount: 1, pages: [{ number: 1, text: 'Se selló la azotea.' }], truncated: false, excerpt: 'azotea', origin: 'ORIGINAL_DOCUMENT',
  superseded: true, replacement: { documentId: 'd2', title: 'Nuevo' },
};

afterEach(() => fake?.close());

describe('findSimilarCases', () => {
  it('registeredOnlyWithCapability', async () => {
    const names = async (caps: string[]) => (await (await connect(() => meWith(caps))).listTools()).tools.map((t) => t.name);
    expect(await names(WITH_CASES)).toContain('findSimilarCases');
    await fake.close();
    expect(await names(['SEARCH_KNOWLEDGE'])).not.toContain('findSimilarCases');
    await fake.close();
    expect(await names(['SAVE_KNOWLEDGE_CARD', 'SIMILAR_CASES'])).not.toContain('findSimilarCases');
  });

  it('sendsQueriesProjectAndLimit', async () => {
    const client = await connect((r) => r.url === '/v1/me' ? meWith(WITH_CASES, 'E', [P1]) : { status: 200, body: { cases: [caseView], noSource: false } });
    const parsed = JSON.parse(textOf(await client.callTool({
      name: 'findSimilarCases', arguments: { queries: ['filtración azotea'], projectId: 'p1', limit: 3 } })));
    const call = fake.requests.at(-1)!;
    expect(call.url).toBe('/v1/similar-cases');
    expect(JSON.parse(call.body)).toEqual({ queries: ['filtración azotea'], projectId: 'p1', limit: 3 });
    expect(parsed.cases[0]).toMatchObject({ documentId: 'd1', projectName: 'Comunidad Sol', pages: caseView.pages,
      superseded: true, replacement: { documentId: 'd2', title: 'Nuevo' } });
    expect(parsed.cases[0]).not.toHaveProperty('authorName');
  });

  it('noSourceSaysNoConstan', async () => {
    const client = await connect((r) => r.url === '/v1/me' ? meWith(WITH_CASES) : { status: 200, body: { cases: [], noSource: true } });
    const result = await client.callTool({ name: 'findSimilarCases', arguments: { queries: ['x'] } });
    expect(textOf(result)).toBe('No constan casos parecidos en los documentos de tu empresa.');
    expect(result.isError).toBeFalsy();
  });

  it('descriptionAsksForProblemTermsAndCarriesTheSevenPhrases', async () => {
    const client = await connect(() => meWith(WITH_CASES));
    const description = (await client.listTools()).tools.find((t) => t.name === 'findSimilarCases')?.description ?? '';
    for (const phrase of [
      'Pasa en queries los términos del problema (qué falla, dónde, la causa) y sus reformulaciones. No pongas nombres de personas.',
      'De cada caso parecido, presenta su resolución y su resultado con la cita del documento y la página.',
      'Si dos casos se resolvieron de forma distinta, enséñalos los dos con sus citas y su resultado, y sugiere solo el que acabó bien, citando ese resultado.',
      'Sugiere una resolución solo si su resultado está escrito en el documento; si ninguno de los casos tiene el resultado escrito, enseña las resoluciones sin sugerir ninguna.',
      'Nunca compares ni menciones a las personas que resolvieron cada caso.',
      'Una ficha aportada es una ficha redactada con IA por un trabajador: preséntala como ficha, no como caso resuelto, y no le atribuyas un resultado que su texto no diga.',
      'addedAt es cuándo entró el documento en Odín, no la fecha del caso; esa fecha está en el texto.',
      'Si un caso no tiene resolución o resultado escritos, dilo; si es el mismo caso nuevo que te han dado, descártalo.',
    ]) expect(description).toContain(phrase);
  });
});

describe('listDocuments caseDocument', () => {
  it('passesCaseDocumentFilter', async () => {
    const client = await connect((r) => r.url === '/v1/me' ? meWith(WITH_CASES) : { status: 200, body: [] });
    await client.callTool({ name: 'listDocuments', arguments: { caseDocument: true } });
    await client.callTool({ name: 'listDocuments', arguments: { caseDocument: false } });
    expect(fake.requests.filter((r) => r.url.startsWith('/v1/documents')).map((r) => r.url)).toEqual([
      '/v1/documents?offset=0&limit=500&caseDocument=true', '/v1/documents?offset=0&limit=500&caseDocument=false']);
  });
});

describe('instructions for cards', () => {
  it('cardsAreWrittenWithAi', async () => {
    const instructions = (await connect(() => meWith(WITH_CASES))).getInstructions() ?? '';
    expect(instructions).toContain('fichas aportadas por trabajadores redactadas con IA');
    expect(instructions).not.toContain('resumen hecho por IA');
  });
});
