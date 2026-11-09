import { vi } from 'vitest';

type Reply = unknown | { status: number; body?: unknown };
export type Routes = Record<string, (body: unknown) => Reply>;

export function mockFetch(routes: Routes) {
  const calls: { key: string; body: unknown }[] = [];
  const fn = vi.fn(async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`;
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body;
    calls.push({ key, body });
    const handler = routes[key];
    if (!handler) return new Response(JSON.stringify({ detail: `sin ruta ${key}` }), { status: 500 });
    const reply = handler(body) as { status?: number; body?: unknown };
    const status = reply?.status ?? 200;
    return new Response(status === 204 ? null : JSON.stringify(reply?.status ? reply.body : reply), { status });
  });
  vi.stubGlobal('fetch', fn);
  return calls;
}
