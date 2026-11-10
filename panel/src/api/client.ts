import type { components } from './types';

export type S = components['schemas'];
export type Capability = NonNullable<S['Capabilities']['capabilities']>[number];

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

let onUnauthorized = () => {};
export const setUnauthorizedHandler = (fn: () => void) => {
  onUnauthorized = fn;
};

async function request<T>(method: string, path: string, body?: unknown, quiet401 = false): Promise<T> {
  const isForm = body instanceof FormData;
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: body === undefined || isForm ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
  });
  if (res.status === 401 && !quiet401) onUnauthorized();
  if (!res.ok) {
    const problem = (await res.json().catch(() => ({}))) as { detail?: string };
    throw new ApiError(res.status, problem.detail ?? 'Algo ha fallado. Inténtalo de nuevo.');
  }
  return (res.status === 204 ? undefined : await res.json().catch(() => undefined)) as T;
}

const a = '/v1/admin';
const co = (id: string) => `${a}/companies/${id}`;

const PAGE_SIZE = 500;
export const MAX_DOCUMENTS = 2000;

// Recorre las páginas de la API con offset, como collectDocuments en el MCP.
async function allDocuments(companyId: string) {
  const documents: S['DocumentView'][] = [];
  while (documents.length <= MAX_DOCUMENTS) {
    const page = await request<S['DocumentView'][]>('GET', `${co(companyId)}/documents?offset=${documents.length}&limit=${PAGE_SIZE}`);
    documents.push(...page);
    if (page.length < PAGE_SIZE) break;
  }
  return { documents: documents.slice(0, MAX_DOCUMENTS), truncated: documents.length > MAX_DOCUMENTS };
}

export const api = {
  me: () => request<S['AdminMe']>('GET', `${a}/me`, undefined, true),
  login: (b: S['Login']) => request<S['UserRef']>('POST', `${a}/session`, b, true),
  logout: () => request<void>('DELETE', `${a}/session`, undefined, true),
  companies: () => request<S['CompanyView'][]>('GET', `${a}/companies`),
  createCompany: (name: string) => request<S['CompanyView']>('POST', `${a}/companies`, { name }),
  renameCompany: (id: string, name: string) => request<S['CompanyView']>('PATCH', co(id), { name }),
  setCapabilities: (id: string, capabilities: Capability[]) =>
    request<S['CompanyView']>('PUT', `${co(id)}/capabilities`, { capabilities }),
  projects: (id: string) => request<S['ProjectView'][]>('GET', `${co(id)}/projects`),
  createProject: (id: string, name: string) => request<S['ProjectView']>('POST', `${co(id)}/projects`, { name }),
  renameProject: (id: string, projectId: string, name: string) =>
    request<S['ProjectView']>('PATCH', `${co(id)}/projects/${projectId}`, { name }),
  deleteProject: (id: string, projectId: string) => request<void>('DELETE', `${co(id)}/projects/${projectId}`),
  users: (id: string) => request<S['UserView'][]>('GET', `${co(id)}/users`),
  createUser: (id: string, b: S['CreateUser']) => request<S['UserView']>('POST', `${co(id)}/users`, b),
  updateUser: (userId: string, changes: S['UpdateUser']) => request<S['UserView']>('PATCH', `${a}/users/${userId}`, changes),
  setActive: (userId: string, active: boolean) => api.updateUser(userId, { active }),
  deleteUser: (userId: string) => request<void>('DELETE', `${a}/users/${userId}`),
  setPassword: (userId: string, b: S['SetPassword']) => request<void>('PUT', `${a}/users/${userId}/password`, b),
  keys: (userId: string) => request<S['KeyView'][]>('GET', `${a}/users/${userId}/keys`),
  issueKey: (userId: string, label: string) => request<S['IssuedKeyView']>('POST', `${a}/users/${userId}/keys`, { label }),
  revokeKey: (keyId: string) => request<void>('DELETE', `${a}/keys/${keyId}`),
  documents: allDocuments,
  upload: (id: string, file: File, title: string, projectId: string) => {
    const q = new URLSearchParams();
    if (title) q.set('title', title);
    if (projectId) q.set('projectId', projectId);
    const form = new FormData();
    form.append('file', file);
    return request<S['Upload']>('POST', `${co(id)}/documents?${q}`, form);
  },
  retry: (documentId: string) => request<S['DocumentView']>('POST', `${a}/documents/${documentId}/retry`),
  pendingCards: (id: string) => request<S['CardView'][]>('GET', `${co(id)}/cards?status=PENDING`),
  review: (cardId: string, b: S['Review']) => request<S['CardView']>('POST', `${a}/cards/${cardId}/review`, b),
};
