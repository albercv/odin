import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer, instructions } from '../src/server.js';
import { meWith, startFakeApi } from './fakeApi.js';
import { DEFAULT_TERMS, noProject, projectTerms, unavailableProject } from '../src/terms.js';

const COMUNIDAD = { singular: 'comunidad', plural: 'comunidades', feminine: true };

describe('projectTerms', () => {
  it('defaultLabelGivesTodaysWords', () => {
    expect(projectTerms({ singular: 'proyecto', plural: 'proyectos', feminine: false })).toEqual(DEFAULT_TERMS);
    expect([noProject(DEFAULT_TERMS), unavailableProject(DEFAULT_TERMS)]).toEqual(['Sin proyecto (general de la empresa)', 'Proyecto no disponible']);
  });

  it('feminineLabelAgrees', () => {
    const t = projectTerms(COMUNIDAD);
    expect([t.one, t.the, t.ofThe, t.none]).toEqual(['una comunidad', 'la', 'de la', 'ninguna']);
    expect([noProject(t), unavailableProject(t)]).toEqual(['Sin comunidad (general de la empresa)', 'Comunidad no disponible']);
  });

  it('keepsAllCapsAcronym', () => expect(unavailableProject(projectTerms({ singular: 'OT', plural: 'OTs', feminine: true }))).toBe('OT no disponible'));
});

describe('instructions', () => {
  it('defaultAddsNoLabelSentence', () => {
    const text = instructions(DEFAULT_TERMS);
    expect(text).not.toContain('se llaman');
    expect(text).toContain('Si la pregunta nombra un proyecto (el nombre de un cliente, un contrato, una obra…), búscalo con listProjects');
  });

  it('customLabelIsNamedAndAgrees', () => {
    const text = instructions(projectTerms(COMUNIDAD));
    expect(text).toContain('En esta empresa, los proyectos se llaman «comunidades»: usa esa palabra con el trabajador.');
    expect(text).toContain('Si la pregunta nombra una comunidad (el nombre de un cliente');
  });

  // Palabras de 2 a 30 caracteres (ProjectLabel.java): se prueban los extremos y ambos géneros.
  it.each([true, false])('fitsTheLimitWithEveryLabel (feminine=%s)', (feminine) => {
    for (const [singular, plural] of [['proyecto', 'proyectos'], ['comunidad', 'comunidades'], ['OT', 'OTs'], ['x'.repeat(30), 'x'.repeat(30)]]) {
      expect(instructions(projectTerms({ singular: singular!, plural: plural!, feminine })).length).toBeLessThanOrEqual(2200);
    }
  });
});

describe('conector con otra palabra', () => {
  const ALL = ['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD', 'CASE_FILES', 'UPLOAD_DOCUMENTS', 'SIMILAR_CASES'];

  async function connect(projectLabel?: typeof COMUNIDAD) {
    const fake = await startFakeApi((r) => (r.url === '/v1/me' ? meWith(ALL, 'E', [], { projectLabel }) : { status: 200, body: { hits: [{ projectId: null, documentId: 'd', title: 't', pageNumber: 1, excerpt: 'x' }, { projectId: 'zz', documentId: 'd', title: 't', pageNumber: 1, excerpt: 'x' }] } }));
    const server = await buildServer(createApiClient(fake.url, 'clave-secreta-1'));
    const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
    const client = new Client({ name: 'test', version: '1' });
    await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
    return { client, close: fake.close };
  }

  it('descriptionsUseTheWordAndNeverTheOldOne', async () => {
    const { client, close } = await connect(COMUNIDAD);
    const tools = (await client.listTools()).tools;
    const text = tools.map((t) => `${t.description} ${JSON.stringify(t.inputSchema)}`).join(' ');
    expect(tools.find((t) => t.name === 'listProjects')!.description).toContain('Lista las comunidades a las que');
    expect(text).toContain('Limita la búsqueda a una comunidad; también incluye lo que no tiene comunidad');
    expect(text).toContain('Sin comunidad (general de la empresa)');
    expect(text).not.toMatch(/proyecto/i);
    expect(client.getInstructions()).toContain('Si la pregunta nombra una comunidad');
    await close();
  });

  it('noProjectNameUsesTheWord', async () => {
    const { client, close } = await connect(COMUNIDAD);
    const out = await client.callTool({ name: 'searchKnowledge', arguments: { queries: ['x'] } });
    const names = JSON.parse((out.content as { text: string }[])[0]!.text).hits.map((h: { projectName: string }) => h.projectName);
    expect(names).toEqual(['Sin comunidad (general de la empresa)', 'Comunidad no disponible']);
    await close();
  });

  it('defaultKeepsTodaysWords', async () => {
    const { client, close } = await connect();
    const tools = (await client.listTools()).tools;
    expect(tools.find((t) => t.name === 'listProjects')!.description).toContain('Lista los proyectos a los que tiene acceso el trabajador');
    expect(JSON.stringify(tools)).toContain('Sin proyecto (general de la empresa)');
    await close();
  });
});
