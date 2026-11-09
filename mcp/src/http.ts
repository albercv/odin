import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { pathToFileURL } from 'node:url';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { isInitializeRequest } from '@modelcontextprotocol/sdk/types.js';
import { ApiError, createApiClient } from './api/client.js';
import { buildServer } from './server.js';
import { errorMessage } from './tools/result.js';

// Cada sesión recuerda la huella de la clave con la que se abrió: el id de sesión no sustituye a la clave.
type Session = { transport: StreamableHTTPServerTransport; keyHash: Buffer };
type Sessions = Map<string, Session>;

const MAX_BODY_BYTES = 1024 * 1024;

class HttpError extends Error {
  constructor(readonly status: number, message: string, readonly code = -32000) {
    super(message);
  }
}

function reply(res: ServerResponse, status: number, message: string, code = -32000): void {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ jsonrpc: '2.0', error: { code, message }, id: null }));
}

const bearer = (req: IncomingMessage) => /^Bearer\s+(.+)$/i.exec(req.headers.authorization ?? '')?.[1];
const hashOf = (key: string) => createHash('sha256').update(key).digest();

async function readBody(req: IncomingMessage): Promise<unknown> {
  if (req.method !== 'POST') return undefined;
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size <= MAX_BODY_BYTES) chunks.push(chunk as Buffer);
  }
  if (size > MAX_BODY_BYTES) throw new HttpError(413, 'La petición supera el tamaño máximo de 1 MB.');
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'El cuerpo de la petición no es JSON válido.', -32700);
  }
}

async function openSession(apiUrl: string, req: IncomingMessage, sessions: Sessions) {
  const accessKey = bearer(req);
  if (!accessKey) throw new ApiError(401, '');
  const server = await buildServer(createApiClient(apiUrl, accessKey));
  const transport: StreamableHTTPServerTransport = new StreamableHTTPServerTransport({
    sessionIdGenerator: randomUUID,
    onsessioninitialized: (id): void => {
      sessions.set(id, { transport, keyHash: hashOf(accessKey) });
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
  if (existing) {
    const key = bearer(req);
    if (!key || !timingSafeEqual(hashOf(key), existing.keyHash)) throw new ApiError(401, '');
    return existing.transport.handleRequest(req, res, body);
  }
  if (sessionId || !isInitializeRequest(body)) return reply(res, 400, 'Sesión MCP no válida o no iniciada.');
  const transport = await openSession(apiUrl, req, sessions);
  return transport.handleRequest(req, res, body);
}

function fail(res: ServerResponse, error: unknown): void {
  if (res.headersSent) return;
  if (error instanceof HttpError) return reply(res, error.status, error.message, error.code);
  if (error instanceof ApiError && error.status === 401) return reply(res, 401, errorMessage(error));
  reply(res, 500, 'Error interno del servidor MCP.');
}

export function startHttpServer(apiUrl: string, port: number, host = process.env.MCP_HOST ?? '127.0.0.1'): Promise<Server> {
  const sessions: Sessions = new Map();
  const server = createServer((req, res) => {
    route(apiUrl, sessions, req, res).catch((error) => fail(res, error));
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
