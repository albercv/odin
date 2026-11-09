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

async function openSession(url: string, key: string) {
  const transport = new StreamableHTTPClientTransport(new URL(url), { requestInit: { headers: { Authorization: `Bearer ${key}` } } });
  const client = new Client({ name: 'test', version: '1' });
  await client.connect(transport);
  return { client, sessionId: transport.sessionId ?? '' };
}

const post = (url: string, body: string, headers: Record<string, string> = {}) =>
  fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream', ...headers }, body });

const verifyCall = JSON.stringify({ jsonrpc: '2.0', id: 9, method: 'tools/call',
  params: { name: 'verifyCitation', arguments: { documentId: 'd', pageNumber: 1, quote: 'q' } } });

async function startWithFakeApi() {
  fake = await startFakeApi((r) => (r.url === '/v1/me' ? meWith(['SEARCH_KNOWLEDGE']) : { status: 200, body: { verified: true } }));
  mcp = await startHttpServer(fake.url, 0);
  return `http://127.0.0.1:${(mcp.address() as AddressInfo).port}/mcp`;
}

describe('http transport', () => {
  it('httpRejectsOtherKeyOrNoKeyOnOpenSession', async () => {
    const url = await startWithFakeApi();
    const { client, sessionId } = await openSession(url, 'key-A');
    const otherKey = await post(url, verifyCall, { 'mcp-session-id': sessionId, Authorization: 'Bearer key-B' });
    const noKey = await post(url, verifyCall, { 'mcp-session-id': sessionId });
    expect([otherKey.status, noKey.status]).toEqual([401, 401]);
    const verifyCalls = () => fake.requests.filter((r) => r.url === '/v1/citations/verify');
    expect(verifyCalls()).toHaveLength(0);
    await client.callTool({ name: 'verifyCitation', arguments: { documentId: 'd', pageNumber: 1, quote: 'q' } });
    expect(verifyCalls().map((r) => r.authorization)).toEqual(['Bearer key-A']);
  });

  it('httpMalformedJsonIs400', async () => {
    const url = await startWithFakeApi();
    const res = await post(url, '{"jsonrpc":', { Authorization: 'Bearer key-A' });
    expect(res.status).toBe(400);
    expect(((await res.json()) as { error: { code: number } }).error.code).toBe(-32700);
  });

  it('httpBodyOverOneMegabyteIs413', async () => {
    const url = await startWithFakeApi();
    const big = JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { pad: 'x'.repeat(1024 * 1024) } });
    expect((await post(url, big, { Authorization: 'Bearer key-A' })).status).toBe(413);
    expect(fake.requests).toHaveLength(0);
  });

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

  it('httpBindsToLoopbackByDefault', async () => {
    delete process.env.MCP_HOST;
    fake = await startFakeApi(() => ({ status: 200, body: {} }));
    mcp = await startHttpServer(fake.url, 0);
    expect((mcp.address() as AddressInfo).address).toBe('127.0.0.1');
  });
});
