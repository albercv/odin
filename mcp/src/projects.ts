import type { Me } from './api/client.js';

export const UNAVAILABLE_PROJECT = 'Proyecto no disponible';
export const NO_PROJECT = 'Sin proyecto (general de la empresa)';

export interface ProjectNames {
  nameOf(projectId: string | null | undefined): string;
  refreshFor(projectIds: Iterable<string | null | undefined>): Promise<void>;
}

// Los nombres salen de GET /v1/me. Un id que no está se vuelve a pedir una vez (un proyecto añadido con el MCP en marcha);
// si sigue sin estar, es «Proyecto no disponible»: nunca se devuelve el id ni el nombre de otro proyecto.
// Sin id (la API manda null) es lo general de la empresa, que ve todo el que la ve.
export function createProjectNames(me: Me, reload: () => Promise<Me>): ProjectNames {
  let byId = namesOf(me);
  return {
    nameOf: (projectId) => (projectId == null ? NO_PROJECT : (byId.get(projectId) ?? UNAVAILABLE_PROJECT)),
    refreshFor: async (projectIds) => {
      if ([...projectIds].some((id) => id != null && !byId.has(id))) byId = namesOf(await reload());
    },
  };
}

const namesOf = (me: Me) => new Map(me.projects.map((p) => [p.id, p.name]));
