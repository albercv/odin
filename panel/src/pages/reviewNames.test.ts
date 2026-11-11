import type { S } from '../api/client';
import { authorName, projectName } from './reviewNames';

const members: S['MemberView'][] = [{ userId: 'u1', displayName: 'Jorge Salas', roles: ['WORKER'], active: true }];
const projects: S['ProjectView'][] = [{ id: 'p1', companyId: 'c1', name: 'Garajes Plaza Sol', restricted: true }];

test('authorNameFallsBackWhenTheAuthorLeft', () => {
  expect(authorName('u1', members)).toBe('Jorge Salas');
  expect(authorName('u9', members)).toBe('una persona que ya no está en la empresa');
});

test('projectNameWithoutProjectIsSinProyecto', () => {
  expect(projectName('p1', projects)).toBe('Garajes Plaza Sol');
  expect(projectName(undefined, projects)).toBe('Sin proyecto');
});
