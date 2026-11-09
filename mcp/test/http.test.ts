import type { Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { afterEach, describe, expect, it } from 'vitest';
import { startHttpServer } from '../src/http.js';
import { meWith, startFakeApi, type FakeApi } from './fakeApi.js';

let fake: FakeApi;
let mcp: Server;

afterEach(async () => {
  mcp?.close();
  await fake?.close();
});

async function open(url: string, key: string) {
  const client = new Client({ name: 'test', version: '1' });
  await client.connect(new StreamableHTTPClientTransport(new URL(url), { requestInit: { headers: { Authorization: `Bearer ${key}` } } }));
  return client;
}

describe('http transport', () => {
  it('httpTransportForwardsBearerPerSession', async () => {
    fake = await startFakeApi((r) => (r.url === '/v1/me' ? meWith(['SEARCH_KNOWLEDGE']) : { status: 200, body: { verified: true } }));
    mcp = await startHttpServer(fake.url, 0);
    const url = `http://127.0.0.1:${(mcp.address() as AddressInfo).port}/mcp`;
    const [a, b] = [await open(url, 'key-A'), await open(url, 'key-B')];
    const args = { documentId: 'd', pageNumber: 1, quote: 'q' };
    await a.callTool({ name: 'verifyCitation', arguments: args });
    await b.callTool({ name: 'verifyCitation', arguments: args });
    await a.callTool({ name: 'verifyCitation', arguments: args });
    const calls = fake.requests.filter((r) => r.url === '/v1/citations/verify').map((r) => r.authorization);
    expect(calls).toEqual(['Bearer key-A', 'Bearer key-B', 'Bearer key-A']);
    expect(fake.requests.filter((r) => r.url === '/v1/me').map((r) => r.authorization)).toEqual(['Bearer key-A', 'Bearer key-B']);
  });

  it('httpRejectsSessionWithoutValidKey', async () => {
    fake = await startFakeApi(() => ({ status: 401, body: { detail: 'x' } }));
    mcp = await startHttpServer(fake.url, 0);
    const url = `http://127.0.0.1:${(mcp.address() as AddressInfo).port}/mcp`;
    await expect(open(url, 'mala')).rejects.toThrow();
  });
});
