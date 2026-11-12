import { describe, expect, it } from 'vitest';
import { createProjectNames, DELETED_PROJECT, NO_PROJECT } from '../src/projects.js';
import { meWith } from './fakeApi.js';

const names = createProjectNames(meWith([], 'E', [{ id: 'p1', name: 'Comunidad Sol' }]).body as never);

describe('createProjectNames', () => {
  it('givesNameOfKnownProject', () => expect(names.nameOf('p1')).toBe('Comunidad Sol'));
  it('givesDeletedProjectForUnknownId', () => expect(names.nameOf('p9')).toBe(DELETED_PROJECT));
  it('givesNoProjectWhenTheFieldIsMissing', () => expect(names.nameOf(undefined)).toBe(NO_PROJECT));
  it('givesNoProjectForNullAsTheApiSendsIt', () => expect(names.nameOf(null)).toBe(NO_PROJECT));
});
