import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

export function registerVerifyCitation(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'verifyCitation',
    {
      description: 'Comprueba que una cita literal aparece en la página indicada del documento.',
      inputSchema: {
        documentId: z.string().describe('Identificador del documento'),
        pageNumber: z.number().int().positive().describe('Número de página, desde 1'),
        quote: z.string().min(1).describe('Texto literal citado'),
      },
    },
    (args) => guarded(async () => jsonResult({ ...args, ...(await api.verifyCitation(args)) })),
  );
}
