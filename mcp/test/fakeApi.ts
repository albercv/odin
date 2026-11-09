import { createServer, type IncomingMessage, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';

export interface Recorded {
  method: string;
  url: string;
  authorization: string | undefined;
  body: string;
}
export type Handler = (request: Recorded) => { status: number; body: unknown };

export interface FakeApi {
  url: string;
  requests: Recorded[];
  handler: Handler;
  close(): Promise<void>;
}

const read = async (req: IncomingMessage) => {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  return Buffer.concat(chunks).toString('utf8');
};

export async function startFakeApi(handler: Handler): Promise<FakeApi> {
  const fake = { requests: [] as Recorded[], handler } as FakeApi;
  const server: Server = createServer(async (req, res) => {
    const recorded = { method: req.method ?? '', url: req.url ?? '', authorization: req.headers.authorization, body: await read(req) };
    fake.requests.push(recorded);
    const { status, body } = fake.handler(recorded);
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(body));
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  fake.url = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  fake.close = () => new Promise((resolve) => server.close(() => resolve()));
  return fake;
}

export const meWith = (capabilities: string[], companyName = 'Empresa Uno') => ({
  status: 200,
  body: { userId: 'u1', displayName: 'Ana', role: 'WORKER', company: { id: 'c1', name: companyName, capabilities }, projects: [] },
});
