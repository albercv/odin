import type { Me } from './api/client.js';

export const DELETED_PROJECT = 'Proyecto borrado';

export interface ProjectNames {
  nameOf(projectId: string | undefined): string | null;
}

// Solo conoce los proyectos de GET /v1/me: un id que no está ahí es un proyecto borrado, nunca se devuelve el id ni otro nombre.
export function createProjectNames(me: Me): ProjectNames {
  const byId = new Map(me.projects.map((p) => [p.id, p.name]));
  return { nameOf: (projectId) => (projectId === undefined ? null : (byId.get(projectId) ?? DELETED_PROJECT)) };
}
