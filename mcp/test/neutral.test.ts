import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer, INSTRUCTIONS } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi } from './fakeApi.js';

let fake: FakeApi;
afterEach(() => fake?.close());

async function descriptions() {
  fake = await startFakeApi(() => meWith(['SEARCH_KNOWLEDGE', 'SAVE_KNOWLEDGE_CARD', 'CASE_FILES', 'SIMILAR_CASES']));
  const server = await buildServer(createApiClient(fake.url, 'clave-secreta-1'));
  const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
  const tools = (await client.listTools()).tools;
  return { tools, texts: [INSTRUCTIONS, ...tools.map((t) => t.description ?? '')] };
}

describe('conector neutro (D-084)', () => {
  it('ningun texto abre una lista de ejemplos con «comunidad»', async () => {
    const { tools, texts } = await descriptions();
    expect(tools.length).toBeGreaterThan(5);
    for (const t of texts) expect(t).not.toMatch(/\((una )?comunidad/i);
  });

  it('listProjects no afirma qué es un proyecto ni a qué se dedica la empresa', async () => {
    const { tools } = await descriptions();
    const d = tools.find((t) => t.name === 'listProjects')!.description!;
    expect(d).toContain('no sabe a qué se dedica la empresa');
    expect(d).toContain('sin decir qué son');
  });

  it('listProjects no trae ejemplos entre paréntesis de qué son los proyectos', async () => {
    const { tools } = await descriptions();
    const d = tools.find((t) => t.name === 'listProjects')!.description!;
    expect(d).not.toContain('(un cliente');
    expect(d).not.toMatch(/comunidad/i);
    expect(d).not.toMatch(/\(/);
    expect(d).toContain('no pongas ejemplos de qué pueden ser');
  });

  it('ninguna descripción de proyecto trae ejemplos de tipo ni «comunidad» (D-091)', async () => {
    const { tools } = await descriptions();
    for (const name of ['listProjects', 'searchKnowledge', 'listDocuments']) {
      const d = tools.find((t) => t.name === name)!.description!;
      expect(d, name).not.toMatch(/comunidad/i);
      expect(d, name).not.toMatch(/\(un cliente/);
      expect(d, name).toContain('una de las unidades con las que la empresa organiza su trabajo en Odín');
    }
  });

  it('INSTRUCTIONS cabe en 2.200 caracteres', () => {
    expect(INSTRUCTIONS.length).toBeLessThanOrEqual(2200);
  });
});
