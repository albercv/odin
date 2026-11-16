import type { components } from './types.js';

export type Me = components['schemas']['Me'];
export type WorkerDocumentView = components['schemas']['WorkerDocumentView'];
export type Page = components['schemas']['Page'];
export type SavedCard = components['schemas']['SavedCard'];
export type SimilarCasesRequest = components['schemas']['SimilarCasesRequestBody'];
export type SimilarCasesResponse = components['schemas']['SimilarCasesResponse'];
export type Hit = components['schemas']['Hit'];
export type SearchRequest = components['schemas']['SearchRequest'];
export type SearchResponse = components['schemas']['SearchResponse'];
export type VerifyCitationsRequest = components['schemas']['VerifyCitations'];
export type Verifications = components['schemas']['Verifications'];
export type MyCard = components['schemas']['MyCardView'];
export type MyQuestion = components['schemas']['MyQuestionView'];
export type CaseFile = components['schemas']['CaseFileResponse'];
export type CaseFileText = components['schemas']['CaseFileTextResponse'];
export type CaseFileAdded = components['schemas']['CaseFileAdded'];
export type IssuedLink = components['schemas']['IssuedLink'];
export type ActivityPage = components['schemas']['ActivityPageView'];
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
    caseDocument?: boolean,
  ): Promise<WorkerDocumentView[]>;
  similarCases(body: SimilarCasesRequest): Promise<SimilarCasesResponse>;
  readPage(documentId: string, pageNumber: number): Promise<Page>;
  verifyCitations(body: VerifyCitationsRequest): Promise<Verifications>;
  saveCard(body: SaveCardRequest): Promise<SavedCard>;
  myCards(status?: string): Promise<MyCard[]>;
  myQuestions(limit?: number): Promise<MyQuestion[]>;
  caseFile(caseId: string): Promise<CaseFile>;
  caseFileText(caseId: string, cursor?: string): Promise<CaseFileText>;
  addToCaseFile(caseId: string, documentIds: string[]): Promise<CaseFileAdded>;
  removeFromCaseFile(caseId: string, documentId: string): Promise<void>;
  requestUploadLink(caseId: string): Promise<IssuedLink>;
  requestProjectUploadLink(projectId: string): Promise<IssuedLink>;
  originalLink(documentId: string, page?: number): Promise<IssuedLink>;
  activity(query: string): Promise<ActivityPage>;
}

export function createApiClient(baseUrl: string, accessKey: string): ApiClient {
  const base = baseUrl.replace(/\/+$/, '');
  const call = async <T>(method: string, path: string, body?: unknown): Promise<T> => {
    const response = await send(base + path, method, accessKey, body);
    if (!response.ok) throw new ApiError(response.status, await problemDetail(response));
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  };
  const enc = encodeURIComponent;
  return {
    me: () => call('GET', '/v1/me'),
    search: (body) => call('POST', '/v1/search', body),
    listDocuments: (projectId, offset, limit, topicId, caseDocument) =>
      call(
        'GET',
        `/v1/documents?offset=${offset}&limit=${limit}${projectId ? `&projectId=${enc(projectId)}` : ''}` +
          (topicId ? `&topicId=${enc(topicId)}` : '') +
          (caseDocument === undefined ? '' : `&caseDocument=${caseDocument}`),
      ),
    similarCases: (body) => call('POST', '/v1/similar-cases', body),
    readPage: (documentId, pageNumber) => call('GET', `/v1/documents/${enc(documentId)}/pages/${pageNumber}`),
    verifyCitations: (body) => call('POST', '/v1/citations/verify-batch', body),
    saveCard: (body) => call('POST', '/v1/cards', body),
    myCards: (status) => call('GET', `/v1/me/cards${status ? `?status=${enc(status)}` : ''}`),
    myQuestions: (limit) => call('GET', `/v1/me/questions${limit ? `?limit=${limit}` : ''}`),
    activity: (query) => call('GET', `/v1/activity?${query}`),
    ...caseFileCalls(call),
    originalLink: (documentId, page) => call('POST', `/v1/documents/${enc(documentId)}/original-links`, page === undefined ? {} : { page }),
  };
}

type Call = <T>(method: string, path: string, body?: unknown) => Promise<T>;

function caseFileCalls(call: Call): Pick<ApiClient, 'caseFile' | 'caseFileText' | 'addToCaseFile' | 'removeFromCaseFile' | 'requestUploadLink' | 'requestProjectUploadLink'> {
  const enc = encodeURIComponent;
  return {
    caseFile: (caseId) => call('GET', `/v1/cases/${enc(caseId)}/file`),
    caseFileText: (caseId, cursor) =>
      call('GET', `/v1/cases/${enc(caseId)}/file/text${cursor ? `?cursor=${enc(cursor)}` : ''}`),
    addToCaseFile: (caseId, documentIds) => call('POST', `/v1/cases/${enc(caseId)}/file/documents`, { documentIds }),
    removeFromCaseFile: (caseId, documentId) =>
      call('DELETE', `/v1/cases/${enc(caseId)}/file/documents/${enc(documentId)}`),
    requestUploadLink: (caseId) => call('POST', `/v1/cases/${enc(caseId)}/upload-links`, {}),
    requestProjectUploadLink: (projectId) => call('POST', `/v1/projects/${enc(projectId)}/upload-links`, {}),
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
