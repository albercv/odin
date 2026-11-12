import { describe, expect, it } from 'vitest';
import { createProjectNames, DELETED_PROJECT } from '../src/projects.js';
import { meWith } from './fakeApi.js';

const names = createProjectNames(meWith([], 'E', [{ id: 'p1', name: 'Comunidad Sol' }]).body as never);

describe('createProjectNames', () => {
  it('givesNameOfKnownProject', () => expect(names.nameOf('p1')).toBe('Comunidad Sol'));
  it('givesDeletedProjectForUnknownId', () => expect(names.nameOf('p9')).toBe(DELETED_PROJECT));
  it('givesNullWithoutProject', () => expect(names.nameOf(undefined)).toBeNull());
});
