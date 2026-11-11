import type { S } from '../api/client';

export function authorName(authorId: string, members: S['MemberView'][]): string {
  return members.find((m) => m.userId === authorId)?.displayName ?? 'una persona que ya no está en la empresa';
}

export function projectName(projectId: string | undefined, projects: S['ProjectView'][]): string {
  return projects.find((p) => p.id === projectId)?.name ?? 'Sin proyecto';
}
