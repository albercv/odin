import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

// Solo lo que GET /v1/me da al trabajador: un proyecto cerrado del que no es miembro no aparece (D-047).
export function registerListProjects(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'listProjects',
    {
      description:
        'Lista los proyectos a los que tiene acceso el trabajador, con su projectId. Cada proyecto es una de las unidades con las que la empresa organiza su trabajo en Odín. ' +
        'Odín no sabe a qué se dedica la empresa ni quiénes son sus clientes, salvo lo que digan sus documentos, así que no lo afirmes. ' +
        'Si preguntan por clientes, empresas o proyectos, lista los proyectos y di que son las unidades con las que la empresa trabaja en Odín, sin decir qué son y no pongas ejemplos de qué pueden ser.',
    },
    () =>
      guarded(async () => {
        const me = await api.me();
        return jsonResult({ projects: me.projects.map((p) => ({ projectId: p.id, name: p.name })) });
      }),
  );
}
