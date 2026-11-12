import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient, WorkerDocumentView } from '../api/client.js';
import type { ProjectNames } from '../projects.js';
import { guarded, jsonResult } from './result.js';

const PAGE_SIZE = 500;
export const MAX_DOCUMENTS = 2000;

export async function collectDocuments(api: ApiClient, projectId?: string) {
  const documents: WorkerDocumentView[] = [];
  while (documents.length <= MAX_DOCUMENTS) {
    const page = await api.listDocuments(projectId, documents.length, PAGE_SIZE);
    documents.push(...page);
    if (page.length < PAGE_SIZE) break;
  }
  return { documents: documents.slice(0, MAX_DOCUMENTS), truncated: documents.length > MAX_DOCUMENTS };
}

export function registerListDocuments(server: McpServer, api: ApiClient, projects: ProjectNames): void {
  server.registerTool(
    'listDocuments',
    {
      description:
        'Lista los documentos de la empresa (hasta 2.000; si hay más, truncated es true). ' +
        'projectName es el proyecto (comunidad, cliente u obra) del documento.',
      inputSchema: { projectId: z.string().optional().describe('Limita la lista a un proyecto') },
    },
    ({ projectId }) =>
      guarded(async () => {
        const { documents, truncated } = await collectDocuments(api, projectId);
        const named = documents.map((d) => ({ ...d, projectName: projects.nameOf(d.projectId) }));
        return jsonResult({ documents: named, truncated });
      }),
  );
}
