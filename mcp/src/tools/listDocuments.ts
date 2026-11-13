import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';
import type { ApiClient, WorkerDocumentView } from '../api/client.js';
import type { ProjectNames } from '../projects.js';
import { guarded, jsonResult } from './result.js';

const PAGE_SIZE = 500;
export const MAX_DOCUMENTS = 2000;

const normalize = (text: string) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();

export async function collectDocuments(api: ApiClient, projectId?: string, topicId?: string, caseDocument?: boolean) {
  const documents: WorkerDocumentView[] = [];
  while (documents.length <= MAX_DOCUMENTS) {
    const page = await api.listDocuments(projectId, documents.length, PAGE_SIZE, topicId, caseDocument);
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
        'projectName es el proyecto: una de las unidades con las que la empresa organiza su trabajo en Odín. ' +
        'Los temas de origen AI los asignó una IA al ingresar el documento; preséntalos como tales. ' +
        'Una lista por tema puede estar incompleta: si taggingPending es mayor que 0, dilo. ' +
        'Para una lista completa, combina el filtro por tema con la búsqueda, porque el etiquetado puede dejarse alguno.',
      inputSchema: {
        projectId: z.string().optional().describe('Limita la lista a un proyecto'),
        topic: z.string().optional().describe('Limita la lista a un tema de la empresa, por su nombre'),
        caseDocument: z.boolean().optional().describe('true lista solo las fichas de caso; false, solo lo que no es caso'),
      },
    },
    ({ projectId, topic, caseDocument }) =>
      guarded(async () => {
        if (topic === undefined) return jsonResult(await listed(api, projects, projectId, undefined, caseDocument));
        // GET /v1/me al momento: los temas y los pendientes cambian durante una sesión larga.
        const me = await api.me();
        const found = me.topics.find((t) => normalize(t.name) === normalize(topic));
        if (!found) return unknownTopic(topic, me.topics.map((t) => t.name));
        const result = await listed(api, projects, projectId, found.id, caseDocument);
        return jsonResult({ ...result, taggingPending: me.tagging.pendingDocuments });
      }),
  );
}

async function listed(api: ApiClient, projects: ProjectNames, projectId?: string, topicId?: string, caseDocument?: boolean) {
  const { documents, truncated } = await collectDocuments(api, projectId, topicId, caseDocument);
  return { documents: documents.map((d) => ({ ...d, projectName: projects.nameOf(d.projectId) })), truncated };
}

function unknownTopic(topic: string, valid: string[]): CallToolResult {
  const list = valid.length > 0 ? valid.join('; ') : 'ninguno: la empresa aún no tiene temas';
  return { content: [{ type: 'text', text: `No existe el tema «${topic}». Temas válidos: ${list}.` }], isError: true };
}
