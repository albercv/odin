import type { components } from './types.js';

export type Me = components['schemas']['Me'];
export type WorkerDocumentView = components['schemas']['WorkerDocumentView'];
export type Page = components['schemas']['Page'];
export type SavedCard = components['schemas']['SavedCard'];
export type Hit = components['schemas']['Hit'];
export type SearchRequest = components['schemas']['SearchRequest'];
export type SearchResponse = components['schemas']['SearchResponse'];
export type VerifyCitationRequest = components['schemas']['VerifyCitation'];
export type Verification = components['schemas']['Verification'];
export type SaveCardRequest = components['schemas']['SaveCard'];

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
  ) {
    super(detail);
  }
}

export interface ApiClient {
  me(): Promise<Me>;
  search(body: SearchRequest): Promise<SearchResponse>;
  listDocuments(
    projectId: string | undefined,
    offset: number,
    limit: number,
    topicId?: string,
  ): Promise<WorkerDocumentView[]>;
  readPage(documentId: string, pageNumber: number): Promise<Page>;
  verifyCitation(body: VerifyCitationRequest): Promise<Verification>;
  saveCard(body: SaveCardRequest): Promise<SavedCard>;
}

export function createApiClient(baseUrl: string, accessKey: string): ApiClient {
  const base = baseUrl.replace(/\/+$/, '');
  const call = async <T>(method: string, path: string, body?: unknown): Promise<T> => {
    const response = await send(base + path, method, accessKey, body);
    if (!response.ok) throw new ApiError(response.status, await problemDetail(response));
    return (await response.json()) as T;
  };
  const enc = encodeURIComponent;
  return {
    me: () => call('GET', '/v1/me'),
    search: (body) => call('POST', '/v1/search', body),
    listDocuments: (projectId, offset, limit, topicId) =>
      call(
        'GET',
        `/v1/documents?offset=${offset}&limit=${limit}${projectId ? `&projectId=${enc(projectId)}` : ''}` +
          (topicId ? `&topicId=${enc(topicId)}` : ''),
      ),
    readPage: (documentId, pageNumber) => call('GET', `/v1/documents/${enc(documentId)}/pages/${pageNumber}`),
    verifyCitation: (body) => call('POST', '/v1/citations/verify', body),
    saveCard: (body) => call('POST', '/v1/cards', body),
  };
}

async function send(url: string, method: string, accessKey: string, body?: unknown): Promise<Response> {
  try {
    return await fetch(url, {
      method,
      headers: { Authorization: `Bearer ${accessKey}`, 'Content-Type': 'application/json', Accept: 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'No se puede conectar con Odín.');
  }
}

async function problemDetail(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string };
    return problem.detail ?? '';
  } catch {
    return '';
  }
}
