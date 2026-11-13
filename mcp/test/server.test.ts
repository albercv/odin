import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi, type Handler } from './fakeApi.js';

const ALL = ['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD', 'CASE_FILES', 'SIMILAR_CASES'];
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
    expect((await onlySave.listTools()).tools.map((t) => t.name)).toEqual(['saveKnowledgeCard', 'listMyKnowledgeCards']);
    await fake.close();
    const all = await connect(() => meWith(ALL));
    expect((await all.listTools()).tools.map((t) => t.name).sort()).toEqual(
      ['addToCaseFile', 'findSimilarCases', 'getCaseFile', 'listDocuments', 'listMyKnowledgeCards', 'listMyQuestions', 'listProjects', 'openOriginal', 'readCaseFile', 'readPage',
        'removeFromCaseFile', 'requestUploadLink', 'saveKnowledgeCard', 'searchKnowledge', 'verifyCitation'],
    );
    expect(all.getInstructions()).toContain('verifyCitation');
  });

  it('registersListProjectsWithSearchCapability', async () => {
    const search = await connect(() => meWith(['SEARCH_KNOWLEDGE']));
    expect((await search.listTools()).tools.map((t) => t.name)).toContain('listProjects');
    await fake.close();
    const save = await connect(() => meWith(['SAVE_KNOWLEDGE_CARD']));
    expect((await save.listTools()).tools.map((t) => t.name)).not.toContain('listProjects');
  });

  it('listProjectsReturnsIdAndName', async () => {
    const projects = [{ id: 'p1', name: 'Comunidad Sol' }, { id: 'p2', name: 'Obra Norte' }];
    const client = await connect(() => meWith(ALL, 'Empresa Uno', projects));
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'listProjects', arguments: {} })));
    expect(parsed).toEqual({ projects: [{ projectId: 'p1', name: 'Comunidad Sol' }, { projectId: 'p2', name: 'Obra Norte' }] });
  });

  it('listProjectsOnlyShowsMyProjects', async () => {
    // El proyecto cerrado p9 existe en la empresa, pero /v1/me no lo devuelve porque el trabajador no es miembro.
    const client = await connect((r) =>
      r.url === '/v1/me' ? meWith(ALL, 'Empresa Uno', [{ id: 'p1', name: 'Comunidad Sol' }]) : { status: 500, body: {} });
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'listProjects', arguments: {} })));
    expect(parsed.projects.map((p: { projectId: string }) => p.projectId)).toEqual(['p1']);
    expect(fake.requests.map((r) => r.url).filter((u) => u !== '/v1/me')).toEqual([]);
  });

  const P1 = { id: 'p1', name: 'Comunidad Sol' };
  const P2 = { id: 'p2', name: 'Obra Norte' };
  const docHit = (projectId?: string) => ({ sourceType: 'ORIGINAL_DOCUMENT', sourceId: 'd1', title: 'Manual', pageNumber: 1,
    paginated: true, projectId, authorName: 'Ana', date: '2026-01-01T00:00:00Z', excerpt: 'x', rank: 1, superseded: false });
  const search = async (hits: unknown[], projects = [P1, P2]) => {
    const client = await connect((r) =>
      r.url === '/v1/me' ? meWith(ALL, 'Empresa Uno', projects) : { status: 200, body: { hits, noSource: false } });
    return JSON.parse(textOf(await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['x'] } })));
  };

  it('searchHitsCarryProjectName', async () => {
    const card = { ...docHit('p2'), sourceType: 'KNOWLEDGE_CARD', paginated: false };
    const parsed = await search([docHit('p1'), card]);
    expect(parsed.hits.map((h: { projectName: string }) => h.projectName)).toEqual(['Comunidad Sol', 'Obra Norte']);
  });

  it('deletedProjectShowsDeletedProjectName', async () => {
    const parsed = await search([docHit('p-borrado')]);
    expect(parsed.hits[0].projectName).toBe('Proyecto borrado');
  });

  it('hitWithoutProjectHasNullProjectName', async () => {
    const parsed = await search([docHit(undefined)]);
    expect(parsed.hits[0].projectName).toBeNull();
  });

  it('searchResultStillHasProjectId', async () => {
    const parsed = await search([docHit('p1')]);
    expect(parsed.hits[0]).toMatchObject({ projectId: 'p1', projectName: 'Comunidad Sol' });
  });

  it('projectNameNeverRevealsAnotherProject', async () => {
    const parsed = await search([docHit('p1')], [P1, P2]);
    expect(JSON.stringify(parsed)).not.toContain('Obra Norte');
    const notMember = await search([docHit('p2')], [P1]);
    expect(JSON.stringify(notMember)).not.toContain('Obra Norte');
    expect(notMember.hits[0].projectName).toBe('Proyecto borrado');
  });

  it('listDocumentsCarriesProjectName', async () => {
    const client = await connect((r) => r.url === '/v1/me' ? meWith(ALL, 'Empresa Uno', [P1])
      : { status: 200, body: [{ id: 'd1', projectId: 'p1', title: 'T' }, { id: 'd2', title: 'S' }] });
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'listDocuments', arguments: {} })));
    expect(parsed.documents.map((d: { projectName: string | null }) => d.projectName)).toEqual(['Comunidad Sol', null]);
  });

  it('instructionsTellToFilterByProject', async () => {
    const client = await connect(() => meWith(ALL));
    expect(client.getInstructions()).toContain(
      'Si la pregunta nombra un proyecto (un cliente, un contrato, una obra…), búscalo con listProjects y filtra la búsqueda con su projectId.');
  });

  it('searchMapsSupersededAndReplacementForDocumentsOnly', async () => {
    const replacement = { documentId: 'd2', title: 'Póliza 2024' };
    const card = { sourceType: 'KNOWLEDGE_CARD', sourceId: 'k1', title: 'Truco', paginated: false, authorName: 'Ana',
      date: '2026-01-01T00:00:00Z', excerpt: 'x', rank: 1, superseded: false };
    const parsed = await search([{ ...docHit('p1'), superseded: true, replacement },
      { ...docHit('p1'), superseded: true }, docHit('p1'), card]);
    expect(parsed.hits[0]).toMatchObject({ superseded: true, replacement });
    expect(parsed.hits[1]).toMatchObject({ superseded: true, replacement: null });
    expect(parsed.hits[2]).toMatchObject({ superseded: false, replacement: null });
    expect(parsed.hits[3]).not.toHaveProperty('superseded');
    expect(parsed.hits[3]).not.toHaveProperty('replacement');
  });

  it('searchDescriptionExplainsProjectFilterIncludesCompanyWide', async () => {
    const client = await connect(() => meWith(ALL));
    const tool = (await client.listTools()).tools.find((t) => t.name === 'searchKnowledge');
    const projectId = (tool?.inputSchema.properties as Record<string, { description?: string }>).projectId;
    expect(projectId?.description).toContain('también incluye lo que no tiene proyecto');
  });

  it('readPageAndListDocumentsPassSupersededAndReplacement', async () => {
    const replacement = { documentId: 'd2', title: 'Póliza 2024' };
    const client = await connect((r) => {
      if (r.url === '/v1/me') return meWith(ALL, 'Empresa Uno', [P1]);
      if (r.url.startsWith('/v1/documents/d1/pages')) {
        return { status: 200, body: { documentId: 'd1', title: 'T', pageNumber: 1, paginated: true, text: 'x', superseded: true, replacement } };
      }
      return { status: 200, body: [{ id: 'd1', projectId: 'p1', title: 'T', superseded: true, replacement }] };
    });
    const page = JSON.parse(textOf(await client.callTool({ name: 'readPage', arguments: { documentId: 'd1', pageNumber: 1 } })));
    expect(page).toMatchObject({ superseded: true, replacement });
    const listed = JSON.parse(textOf(await client.callTool({ name: 'listDocuments', arguments: {} })));
    expect(listed.documents[0]).toMatchObject({ superseded: true, replacement, projectName: 'Comunidad Sol' });
  });

  it('readPagePassesReaderNameAndInstructionsExplainOcrAndEmptyPages', async () => {
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(ALL)
      : { status: 200, body: { documentId: 'd1', title: 'T', pageNumber: 1, paginated: true, text: '', superseded: false, readerName: 'unlimited-ocr-2' } }));
    const page = JSON.parse(textOf(await client.callTool({ name: 'readPage', arguments: { documentId: 'd1', pageNumber: 1 } })));
    expect(page.readerName).toBe('unlimited-ocr-2');
    const instructions = client.getInstructions() ?? '';
    expect(instructions).toContain(
      'Si readerName es unlimited-ocr-2, la página es un escaneo leído por OCR y puede tener errores: cítala tal cual.');
    expect(instructions).toContain('Una página con el texto vacío no se ha leído: no concluyas nada de ella.');
  });

  it('instructionsTellHowToTreatSupersededAndConflictingSources', async () => {
    const instructions = (await connect(() => meWith(ALL))).getInstructions() ?? '';
    expect(instructions).toContain(
      'Si un resultado viene marcado como sustituido, no lo presentes como vigente: di que está sustituido y por cuál, y busca o lee el que lo sustituye antes de responder. Si no puedes ver el que lo sustituye, di solo que está sustituido.');
    expect(instructions).toContain(
      'Si dos fuentes no cuadran y ninguna está marcada como sustituida: si una dice expresamente que cambia a la otra (un anexo que modifica una cláusula), o es un acuerdo posterior del órgano que decide (la junta o el consejo) sobre lo mismo, da lo que vale ahora, di qué documento lo cambia y cita las dos; si el acuerdo no cita la norma que cambia, dilo, y no juzgues si es válido. Que una sea más reciente no basta. Si no se da ninguno de esos casos, enseña las dos, cada una con su cita y su fecha, y no elijas ninguna. La fecha es la que conste en el texto del documento; si no consta, da la fecha en que entró en Odín y di que es esa.');
  });

  it('searchMapsHitsWithCitation', async () => {
    const hit = { sourceType: 'ORIGINAL_DOCUMENT', sourceId: 'd1', title: 'Manual', pageNumber: 3, paginated: true,
      authorName: 'Ana', date: '2026-01-01T00:00:00Z', excerpt: 'el <b>plazo</b>', rank: 0.5 };
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(ALL) : { status: 200, body: { hits: [hit], noSource: false } }));
    const result = await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['plazo'], limit: 5 } });
    const parsed = JSON.parse(textOf(result));
    expect(parsed.hits[0]).toMatchObject({ documentId: 'd1', title: 'Manual', pageNumber: 3, addedAt: '2026-01-01T00:00:00Z', excerpt: 'el <b>plazo</b>' });
    expect(parsed.hits[0]).not.toHaveProperty('date');
    expect(JSON.parse(fake.requests.at(-1)!.body)).toEqual({ queries: ['plazo'], limit: 5 });
  });

  it('searchPassesTheGeneralScope', async () => {
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(ALL) : { status: 200, body: { hits: [], noSource: true } }));
    await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['convocatoria'], scope: 'general' } });
    expect(JSON.parse(fake.requests.at(-1)!.body)).toEqual({ queries: ['convocatoria'], scope: 'general' });
    const rejected = await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['x'], scope: 'project' } });
    expect(rejected.isError).toBe(true);
  });

  it('searchMapsCardsAsCardsWithOriginAndWithoutPage', async () => {
    const card = { sourceType: 'KNOWLEDGE_CARD', sourceId: 'k1', title: 'Truco', paginated: false,
      authorName: 'Ana', date: '2026-01-01T00:00:00Z', excerpt: 'se hace así', rank: 1 };
    const client = await connect((r) => (r.url === '/v1/me' ? meWith(ALL) : { status: 200, body: { hits: [card], noSource: false } }));
    const parsed = JSON.parse(textOf(await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['truco'] } })));
    expect(parsed.hits[0]).toEqual({ sourceType: 'KNOWLEDGE_CARD', cardId: 'k1', title: 'Truco', authorName: 'Ana',
      addedAt: '2026-01-01T00:00:00Z', projectId: null, projectName: null, excerpt: 'se hace así',
      origin: 'Ficha aportada por Ana, redactada con IA' });
  });

  it('searchCardsCarryReviewStatusAndDescriptionExplainsPending', async () => {
    const card = { sourceType: 'KNOWLEDGE_CARD', sourceId: 'k1', title: 'Truco', paginated: false, authorName: 'Ana',
      date: '2026-01-01T00:00:00Z', excerpt: 'x', rank: 1, superseded: false, reviewStatus: 'PENDING' };
    const parsed = await search([card, docHit('p1')]);
    expect(parsed.hits[0]).toMatchObject({ cardId: 'k1', reviewStatus: 'PENDING' });
    expect(parsed.hits[1]).not.toHaveProperty('reviewStatus');
    const tools = (await (await connect(() => meWith(ALL))).listTools()).tools;
    expect(tools.find((t) => t.name === 'searchKnowledge')?.description).toContain(
      'Una ficha con reviewStatus PENDING es tuya y está pendiente de revisión: dilo al citarla y recuerda que tus compañeros todavía no la ven.');
  });

  it('pendingCardRuleLivesInToolDescriptionsNotInInstructions', async () => {
    const instructions = (await connect(() => meWith(ALL))).getInstructions() ?? '';
    expect(instructions).not.toContain('reviewStatus');
  });

  it('searchDescriptionExplainsAddedAt', async () => {
    const client = await connect(() => meWith(ALL));
    const search = (await client.listTools()).tools.find((t) => t.name === 'searchKnowledge');
    expect(search?.description).toContain('addedAt es la fecha en que entró en Odín, no la del documento.');
  });

  it.each([
    'Cada consulta, una idea, en 1 a 3 palabras específicas.',
    'Si una búsqueda no trae lo que buscas, repite en otra llamada con una sola consulta corta y específica: las consultas de una misma llamada se suman y una genérica puede tapar a la buena.',
    'Si el documento da una fecha o un plazo concreto, dalo con su cita y no te abstengas.',
    'Si una ficha dice algo distinto que un documento, cita las dos.',
    'Para normas, procedimientos o políticas de la propia empresa, busca también con scope: "general" (documentos sin proyecto).',
  ])('searchDescriptionGuidesTheModel: %s', async (sentence) => {
    const client = await connect(() => meWith(ALL));
    const search = (await client.listTools()).tools.find((t) => t.name === 'searchKnowledge');
    expect(search?.description).toContain(sentence);
  });

  it('instructionsKeepCardCitationRule', async () => {
    const client = await connect(() => meWith(ALL));
    const instructions = client.getInstructions() ?? '';
    expect(instructions).toContain('con su título, autor y fecha de alta');
    expect(instructions).toContain('no tienen página');
    expect(instructions).toContain('verifyCitation es solo para documentos');
    expect(instructions).toContain('si verifyCitation devuelve false, no uses esa cita');
  });

  it('instructionsRequireLiteralQuotes', async () => {
    const instructions = (await connect(() => meWith(ALL))).getInstructions() ?? '';
    expect(instructions).toContain('Copia cada fragmento literal por separado; no unas trozos con puntos suspensivos.');
    expect(instructions).toContain('De una ficha, cita solo su texto, sin el título, el autor ni la fecha.');
    expect(instructions).toContain('preséntalas con su título, autor y fecha');
  });

  it('instructionsAskForPartialSourceWarning', async () => {
    const instructions = (await connect(() => meWith(ALL))).getInstructions() ?? '';
    expect(instructions).toContain(
      'Afirma solo lo que dicen las citas. Si la fuente es parcial, porque falta el documento que lo regula o el que tienes no lo dice todo, dilo y no completes la conclusión.');
  });

  it('instructionsStayUnderLengthBudget', async () => {
    expect((await connect(() => meWith(ALL))).getInstructions()?.length).toBeLessThanOrEqual(2200);
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
