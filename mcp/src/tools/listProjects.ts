import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

// Solo lo que GET /v1/me da al trabajador: un proyecto cerrado del que no es miembro no aparece (D-047).
export function registerListProjects(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'listProjects',
    { description: 'Lista los proyectos (comunidades, clientes, obras) a los que tiene acceso el trabajador, con su projectId.' },
    () =>
      guarded(async () => {
        const me = await api.me();
        return jsonResult({ projects: me.projects.map((p) => ({ projectId: p.id, name: p.name })) });
      }),
  );
}
