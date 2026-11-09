import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient, Hit } from '../api/client.js';
import { NO_SOURCE_MESSAGE, guarded, jsonResult, textResult } from './result.js';

export function registerSearchKnowledge(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'searchKnowledge',
    {
      description:
        'Busca en los documentos y fichas aprobadas de la empresa. Los documentos traen documentId, página y extracto para citar; ' +
        'las fichas (KNOWLEDGE_CARD) traen cardId, autor, fecha y su origen, y no tienen página.',
      inputSchema: {
        queries: z.array(z.string()).min(1).describe('Consultas de búsqueda, con palabras clave'),
        projectId: z.string().optional().describe('Limita la búsqueda a un proyecto'),
        limit: z.number().int().positive().optional().describe('Máximo de resultados'),
      },
    },
    (args) =>
      guarded(async () => {
        const result = await api.search(args);
        if (result.noSource || !result.hits.length) return textResult(NO_SOURCE_MESSAGE);
        return jsonResult({ hits: result.hits.map(toCitation) });
      }),
  );
}

// Una ficha nunca se presenta como documento: lleva cardId y su marca de origen (spec 6.8, D-024).
const toCitation = (hit: Hit) =>
  hit.sourceType === 'KNOWLEDGE_CARD'
    ? {
        sourceType: hit.sourceType,
        cardId: hit.sourceId,
        title: hit.title,
        authorName: hit.authorName,
        date: hit.date,
        projectId: hit.projectId ?? null,
        excerpt: hit.excerpt,
        origin: `Ficha aportada por ${hit.authorName}; resumen hecho por IA`,
      }
    : {
        sourceType: hit.sourceType,
        documentId: hit.sourceId,
        title: hit.title,
        pageNumber: hit.pageNumber ?? null,
        paginated: hit.paginated,
        projectId: hit.projectId ?? null,
        authorName: hit.authorName,
        date: hit.date,
        excerpt: hit.excerpt,
      };
