import { randomUUID } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { pathToFileURL } from 'node:url';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { isInitializeRequest } from '@modelcontextprotocol/sdk/types.js';
import { ApiError, createApiClient } from './api/client.js';
import { buildServer } from './server.js';
import { errorMessage } from './tools/result.js';

type Sessions = Map<string, StreamableHTTPServerTransport>;

function reply(res: ServerResponse, status: number, message: string): void {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ jsonrpc: '2.0', error: { code: -32000, message }, id: null }));
}

async function readBody(req: IncomingMessage): Promise<unknown> {
  if (req.method !== 'POST') return undefined;
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

async function openSession(apiUrl: string, req: IncomingMessage, sessions: Sessions) {
  const accessKey = /^Bearer\s+(.+)$/i.exec(req.headers.authorization ?? '')?.[1];
  if (!accessKey) throw new ApiError(401, '');
  const server = await buildServer(createApiClient(apiUrl, accessKey));
  const transport: StreamableHTTPServerTransport = new StreamableHTTPServerTransport({
    sessionIdGenerator: randomUUID,
    onsessioninitialized: (id): void => {
      sessions.set(id, transport);
    },
  });
  transport.onclose = () => transport.sessionId && sessions.delete(transport.sessionId);
  await server.connect(transport);
  return transport;
}

async function route(apiUrl: string, sessions: Sessions, req: IncomingMessage, res: ServerResponse): Promise<void> {
  const body = await readBody(req);
  const sessionId = req.headers['mcp-session-id'];
  const existing = typeof sessionId === 'string' ? sessions.get(sessionId) : undefined;
  if (existing) return existing.handleRequest(req, res, body);
  if (sessionId || !isInitializeRequest(body)) return reply(res, 400, 'Sesión MCP no válida o no iniciada.');
  const transport = await openSession(apiUrl, req, sessions);
  return transport.handleRequest(req, res, body);
}

export function startHttpServer(apiUrl: string, port: number, host = process.env.MCP_HOST ?? '127.0.0.1'): Promise<Server> {
  const sessions: Sessions = new Map();
  const server = createServer((req, res) => {
    route(apiUrl, sessions, req, res).catch((error) => {
      const status = error instanceof ApiError && error.status === 401 ? 401 : 500;
      if (!res.headersSent) reply(res, status, status === 401 ? errorMessage(error) : 'Error interno del servidor MCP.');
    });
  });
  return new Promise((resolve) => server.listen(port, host, () => resolve(server)));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const apiUrl = process.env.ODIN_API_URL;
  if (!apiUrl) {
    console.error('Falta ODIN_API_URL en el entorno.');
    process.exit(1);
  }
  await startHttpServer(apiUrl, Number(process.env.MCP_PORT ?? 8090));
}
