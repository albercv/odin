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

export const documentPage = (companyId: string, { q = '', offset = 0, limit = PAGE_SIZE, withdrawn = false } = {}) => {
  const query = new URLSearchParams({ offset: String(offset), limit: String(limit) });
  if (q.trim()) query.set('q', q.trim());
  if (withdrawn) query.set('withdrawn', 'true');
  return request<S['DocumentPage']>('GET', `${co(companyId)}/documents?${query}`);
};

// Recorre las páginas de la API con offset, como collectDocuments en el MCP.
async function allDocuments(companyId: string, withdrawn = false) {
  const documents: S['DocumentView'][] = [];
  while (documents.length <= MAX_DOCUMENTS) {
    const page = await documentPage(companyId, { offset: documents.length, limit: PAGE_SIZE, withdrawn });
    documents.push(...page.documents);
    if (page.documents.length < PAGE_SIZE) break;
  }
  return { documents: documents.slice(0, MAX_DOCUMENTS), truncated: documents.length > MAX_DOCUMENTS };
}

export const originalUrl = (documentId: string) => `${a}/documents/${documentId}/original`;

export const uploadLinks = {
  info: (token: string) => request<S['UploadLinkInfo']>('GET', `/v1/upload-links/${encodeURIComponent(token)}`, undefined, true),
  upload: (token: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<S['UploadReceipt']>('POST', `/v1/upload-links/${encodeURIComponent(token)}/documents`, form, true);
  },
};

export const api = {
  me: () => request<S['AdminMe']>('GET', `${a}/me`, undefined, true),
  login: (b: S['Login']) => request<S['UserRef']>('POST', `${a}/session`, b, true),
  logout: () => request<void>('DELETE', `${a}/session`, undefined, true),
  companies: () => request<S['CompanyView'][]>('GET', `${a}/companies`),
  createCompany: (name: string, code: string) => request<S['CompanyView']>('POST', `${a}/companies`, { name, code }),
  updateCompany: (id: string, changes: S['UpdateCompany']) => request<S['CompanyView']>('PATCH', co(id), changes),
  setCapabilities: (id: string, capabilities: Capability[]) =>
    request<S['CompanyView']>('PUT', `${co(id)}/capabilities`, { capabilities }),
  projects: (id: string) => request<S['ProjectView'][]>('GET', `${co(id)}/projects`),
  createProject: (id: string, name: string) => request<S['ProjectView']>('POST', `${co(id)}/projects`, { name }),
  updateProject: (id: string, projectId: string, changes: S['UpdateProject']) =>
    request<S['ProjectView']>('PATCH', `${co(id)}/projects/${projectId}`, changes),
  projectMembers: (id: string, projectId: string) => request<S['ProjectMemberView'][]>('GET', `${co(id)}/projects/${projectId}/members`),
  addProjectMember: (id: string, projectId: string, userId: string) =>
    request<void>('PUT', `${co(id)}/projects/${projectId}/members/${userId}`),
  removeProjectMember: (id: string, projectId: string, userId: string) =>
    request<void>('DELETE', `${co(id)}/projects/${projectId}/members/${userId}`),
  deleteProject: (id: string, projectId: string) => request<void>('DELETE', `${co(id)}/projects/${projectId}`),
  members: (id: string) => request<S['MemberView'][]>('GET', `${co(id)}/members`),
  addMember: (id: string, b: S['AddMember']) => request<S['MemberAdded']>('POST', `${co(id)}/members`, b),
  memberProjects: (id: string, userId: string) => request<S['ProjectView'][]>('GET', `${co(id)}/members/${userId}/projects`),
  updateMember: (id: string, userId: string, changes: S['UpdateMember']) =>
    request<S['MemberView']>('PATCH', `${co(id)}/members/${userId}`, changes),
  setActive: (id: string, userId: string, active: boolean) => api.updateMember(id, userId, { active }),
  removeMember: (id: string, userId: string) => request<void>('DELETE', `${co(id)}/members/${userId}`),
  groupAdmins: () => request<S['GroupAdminView'][]>('GET', `${a}/group-admins`),
  addGroupAdmin: (b: S['AddGroupAdmin']) => request<S['GroupAdminAdded']>('POST', `${a}/group-admins`, b),
  revokeGroupAdmin: (userId: string) => request<void>('DELETE', `${a}/group-admins/${userId}`),
  deleteUser: (userId: string) => request<void>('DELETE', `${a}/users/${userId}`),
  updateUser: (userId: string, changes: S['UpdateUser']) => request<S['UserRef']>('PATCH', `${a}/users/${userId}`, changes),
  setPassword: (userId: string, b: S['SetPassword']) => request<void>('PUT', `${a}/users/${userId}/password`, b),
  keys: (id: string, userId: string) => request<S['KeyView'][]>('GET', `${co(id)}/members/${userId}/keys`),
  issueKey: (id: string, userId: string, label: string) =>
    request<S['IssuedKeyView']>('POST', `${co(id)}/members/${userId}/keys`, { label }),
  revokeKey: (keyId: string) => request<void>('DELETE', `${a}/keys/${keyId}`),
  documents: allDocuments,
  documentPage,
  withdraw: (documentId: string) => request<S['DocumentView']>('PUT', `${a}/documents/${documentId}/withdrawal`),
  restore: (documentId: string) => request<S['DocumentView']>('DELETE', `${a}/documents/${documentId}/withdrawal`),
  caseFile: (id: string, caseId: string) => request<S['CaseFileResponse']>('GET', `${co(id)}/cases/${caseId}/file`),
  caseEvents: (id: string, caseId: string) => request<S['CaseFileEventView'][]>('GET', `${co(id)}/cases/${caseId}/events`),
  addToCase: (id: string, caseId: string, documentIds: string[]) =>
    request<S['CaseFileAdded']>('POST', `${co(id)}/cases/${caseId}/file/documents`, { documentIds }),
  removeFromCase: (id: string, caseId: string, documentId: string) =>
    request<void>('DELETE', `${co(id)}/cases/${caseId}/file/documents/${documentId}`),
  upload: (id: string, file: File, title: string, projectId: string, caseDocument = false, caseId = '') => {
    const q = new URLSearchParams();
    if (title) q.set('title', title);
    if (projectId) q.set('projectId', projectId);
    if (caseDocument) q.set('caseDocument', 'true');
    if (caseId) q.set('caseId', caseId);
    const form = new FormData();
    form.append('file', file);
    return request<S['Upload']>('POST', `${co(id)}/documents?${q}`, form);
  },
  markCase: (documentId: string, caseDocument: boolean) =>
    request<S['DocumentView']>('PATCH', `${a}/documents/${documentId}`, { caseDocument }),
  markCases: (id: string, documentIds: string[], caseDocument: boolean) =>
    request<S['CaseMarksResult']>('POST', `${co(id)}/documents/case-marks`, { documentIds, caseDocument }),
  retry: (documentId: string) => request<S['DocumentView']>('POST', `${a}/documents/${documentId}/retry`),
  supersede: (documentId: string, replacementId: string) =>
    request<S['DocumentView']>('PUT', `${a}/documents/${documentId}/superseded-by`, { replacementId }),
  unsupersede: (documentId: string) => request<void>('DELETE', `${a}/documents/${documentId}/superseded-by`),
  pendingCards: (id: string) => request<S['CardView'][]>('GET', `${co(id)}/cards?status=PENDING`),
  topics: (id: string) => request<S['TopicView'][]>('GET', `${co(id)}/topics`),
  createTopic: (id: string, b: S['TopicRequest']) => request<S['TopicView']>('POST', `${co(id)}/topics`, b),
  updateTopic: (id: string, topicId: string, b: S['TopicPatch']) => request<S['TopicView']>('PATCH', `${co(id)}/topics/${topicId}`, b),
  deleteTopic: (id: string, topicId: string) => request<void>('DELETE', `${co(id)}/topics/${topicId}`),
  taggingStatus: (id: string) => request<S['TaggingStatusView']>('GET', `${co(id)}/tagging-status`),
  documentTopics: (documentId: string) => request<S['DocumentTopicsResponse']>('GET', `${a}/documents/${documentId}/topics`),
  addDocumentTopic: (documentId: string, topicId: string) => request<void>('PUT', `${a}/documents/${documentId}/topics/${topicId}`),
  removeDocumentTopic: (documentId: string, topicId: string) => request<void>('DELETE', `${a}/documents/${documentId}/topics/${topicId}`),
  retag: (documentId: string) => request<void>('POST', `${a}/documents/${documentId}/topics/retag`),
  usageStats: (id: string, month: string) => request<S['UsageStatsView']>('GET', `${co(id)}/usage-stats?month=${month}`),
  gaps: (id: string, month: string) => request<S['GapsView']>('GET', `${co(id)}/gaps?month=${month}`),
  deliveredVolume: (id: string, month: string) => request<S['DeliveredVolumeView']>('GET', `${co(id)}/delivered-volume?month=${month}`),
  review: (cardId: string, b: S['Review']) => request<S['CardView']>('POST', `${a}/cards/${cardId}/review`, b),
};
