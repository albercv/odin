import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

export function registerSaveKnowledgeCard(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'saveKnowledgeCard',
    {
      description: 'Guarda una ficha de conocimiento. Queda pendiente de revisión por el administrador. Puedes ver su estado con listMyKnowledgeCards.',
      inputSchema: {
        title: z.string().min(1).describe('Título de la ficha'),
        body: z.string().min(1).describe('Contenido de la ficha'),
        projectId: z.string().optional().describe('Proyecto al que pertenece'),
      },
    },
    (args) => guarded(async () => jsonResult(await api.saveCard(args))),
  );
}
