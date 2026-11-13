import type { Me } from './api/client.js';

export const UNAVAILABLE_PROJECT = 'Proyecto no disponible';

export interface ProjectNames {
  nameOf(projectId: string | null | undefined): string | null;
  refreshFor(projectIds: Iterable<string | null | undefined>): Promise<void>;
}

// Los nombres salen de GET /v1/me. Un id que no está se vuelve a pedir una vez (un proyecto añadido con el MCP en marcha);
// si sigue sin estar, es «Proyecto no disponible»: nunca se devuelve el id ni el nombre de otro proyecto.
export function createProjectNames(me: Me, reload: () => Promise<Me>): ProjectNames {
  let byId = namesOf(me);
  return {
    nameOf: (projectId) => (projectId == null ? null : (byId.get(projectId) ?? UNAVAILABLE_PROJECT)),
    refreshFor: async (projectIds) => {
      if ([...projectIds].some((id) => id != null && !byId.has(id))) byId = namesOf(await reload());
    },
  };
}

const namesOf = (me: Me) => new Map(me.projects.map((p) => [p.id, p.name]));
