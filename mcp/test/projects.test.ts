import { describe, expect, it } from 'vitest';
import type { Me } from '../src/api/client.js';
import { createProjectNames, } from '../src/projects.js';
import { DEFAULT_TERMS, noProject, unavailableProject } from '../src/terms.js';
import { meWith } from './fakeApi.js';

const me = (projects: { id: string; name: string }[]) => meWith([], 'E', projects).body as unknown as Me;

function withReloads(...lists: { id: string; name: string }[][]) {
  let calls = 0;
  const names = createProjectNames(me(lists[0]!), async () => me(lists[Math.min(++calls, lists.length - 1)]!), DEFAULT_TERMS);
  return { names, calls: () => calls };
}

describe('createProjectNames', () => {
  it('givesNameOfKnownProject', () => expect(withReloads([{ id: 'p1', name: 'Comunidad Sol' }]).names.nameOf('p1')).toBe('Comunidad Sol'));
  it('givesUnavailableProjectForUnknownId', () => expect(withReloads([]).names.nameOf('p9')).toBe(unavailableProject(DEFAULT_TERMS)));
  it('givesNoProjectWhenTheFieldIsMissing', () => expect(withReloads([]).names.nameOf(undefined)).toBe(noProject(DEFAULT_TERMS)));
  it('givesNoProjectForNullAsTheApiSendsIt', () => expect(withReloads([]).names.nameOf(null)).toBe(noProject(DEFAULT_TERMS)));
  it('saysUnavailableNotDeleted', () => expect(unavailableProject(DEFAULT_TERMS)).toBe('Proyecto no disponible'));

  it('doesNotReloadWhenEveryIdIsKnown', async () => {
    const r = withReloads([{ id: 'p1', name: 'Comunidad Sol' }]);
    await r.names.refreshFor(['p1', undefined, null]);
    expect(r.calls()).toBe(0);
  });

  it('reloadsOnceForAProjectAddedAfterStart', async () => {
    const r = withReloads([{ id: 'p1', name: 'Sol' }], [{ id: 'p1', name: 'Sol' }, { id: 'p3', name: 'Mirador' }]);
    await r.names.refreshFor(['p3', 'p3', 'p1']);
    expect(r.calls()).toBe(1);
    expect(r.names.nameOf('p3')).toBe('Mirador');
  });

  it('staysUnavailableIfTheReloadDoesNotHaveIt', async () => {
    const r = withReloads([{ id: 'p1', name: 'Sol' }]);
    await r.names.refreshFor(['p9']);
    expect(r.calls()).toBe(1);
    expect(r.names.nameOf('p9')).toBe(unavailableProject(DEFAULT_TERMS));
  });
});
