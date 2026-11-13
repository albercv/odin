import { readFileSync } from 'node:fs';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { afterEach, describe, expect, it } from 'vitest';
import { createApiClient } from '../src/api/client.js';
import { buildServer } from '../src/server.js';
import { meWith, startFakeApi, type FakeApi } from './fakeApi.js';

const readJson = (path: string) => JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));
const manifest = readJson('../extension/manifest.json');
const openApi = readJson('../../api/openapi.json');
const everyCapability: string[] = openApi.components.schemas.MeCompany.properties.capabilities.items.enum;
let fake: FakeApi;

afterEach(() => fake?.close());

async function toolsWithEveryCapability(): Promise<string[]> {
  fake = await startFakeApi(() => meWith(everyCapability));
  const server = await buildServer(createApiClient(fake.url, 'clave-secreta-1'));
  const [serverSide, clientSide] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(serverSide), client.connect(clientSide)]);
  return (await client.listTools()).tools.map((t) => t.name).sort();
}

describe('extension manifest', () => {
  it('listsExactlyTheToolsTheServerRegisters', async () => {
    expect(manifest.tools.map((t: { name: string }) => t.name).sort()).toEqual(await toolsWithEveryCapability());
  });

  it('takesItsVersionFromThePackage', () => {
    expect(manifest.version).toBe(readJson('../package.json').version);
  });

  it('asksForTheKeyAsASecretAndPassesItOnlyThroughTheEnvironment', () => {
    expect(manifest.user_config.access_key).toMatchObject({ type: 'string', sensitive: true, required: true });
    expect(manifest.server.mcp_config.env).toEqual({
      ODIN_API_URL: '${user_config.api_url}', ODIN_ACCESS_KEY: '${user_config.access_key}',
    });
    expect(JSON.stringify(manifest.server.mcp_config.args)).not.toContain('access_key');
  });

  it('namesTheProductOnlyInDisplayName', () => {
    const elsewhere = JSON.stringify({ ...manifest, display_name: undefined });
    expect(elsewhere).not.toContain(manifest.display_name);
  });
});
