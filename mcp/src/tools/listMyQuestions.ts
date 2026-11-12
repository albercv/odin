import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

const DESCRIPTION =
  'Lista las consultas que tu IA ha hecho al cerebro con tu clave. ' +
  'El texto es lo que envió tu IA como consulta, no lo que escribiste tú.';

export function registerListMyQuestions(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'listMyQuestions',
    {
      description: DESCRIPTION,
      inputSchema: { limit: z.number().int().positive().optional().describe('Máximo de consultas') },
    },
    ({ limit }) => guarded(async () => jsonResult({ questions: await api.myQuestions(limit) })),
  );
}
