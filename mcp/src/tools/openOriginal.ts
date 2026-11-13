import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

export function registerOpenOriginal(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'openOriginal',
    {
      description:
        'Pide un enlace para abrir el fichero original de un documento, en una página si se indica. ' +
        'Si el trabajador quiere ver el original, da el enlace tal cual, sin cambiarlo, y di cuándo caduca (expiresAt).',
      inputSchema: {
        documentId: z.string().describe('Identificador del documento'),
        page: z.number().int().positive().optional().describe('Página en la que abre; si falta, la primera'),
      },
    },
    (args) => guarded(async () => jsonResult(await api.originalLink(args.documentId, args.page))),
  );
}
