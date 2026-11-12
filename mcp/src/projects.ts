import type { Me } from './api/client.js';

export const DELETED_PROJECT = 'Proyecto borrado';
export const NO_PROJECT = 'Sin proyecto (general de la empresa)';

export interface ProjectNames {
  nameOf(projectId: string | null | undefined): string;
}

// Solo conoce los proyectos de GET /v1/me: un id que no está ahí es un proyecto borrado, nunca se devuelve el id ni otro nombre.
// Sin id (la API manda null) es lo general de la empresa, que ve todo el que la ve.
export function createProjectNames(me: Me): ProjectNames {
  const byId = new Map(me.projects.map((p) => [p.id, p.name]));
  return { nameOf: (projectId) => (projectId == null ? NO_PROJECT : (byId.get(projectId) ?? DELETED_PROJECT)) };
}
