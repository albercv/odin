import { describe, expect, it } from 'vitest';
import { projectTerms } from './projectTerms';

describe('projectTerms', () => {
  it('uses «proyecto» when there is no label', () => {
    const t = projectTerms();
    expect([t.one, t.many, t.One, t.Many, t.aOne, t.newOne, t.noneOne]).toEqual(['proyecto', 'proyectos', 'Proyecto', 'Proyectos', 'un proyecto', 'Nuevo proyecto', 'Sin proyecto']);
    expect(t.agree('asignados', 'asignadas')).toBe('asignados');
    expect([t.the, t.these]).toEqual(['el', 'los']);
  });
  it('agrees with a feminine word', () => {
    const t = projectTerms({ singular: 'comunidad', plural: 'comunidades', feminine: true });
    expect([t.One, t.Many, t.aOne, t.newOne, t.noneOne]).toEqual(['Comunidad', 'Comunidades', 'una comunidad', 'Nueva comunidad', 'Sin comunidad']);
    expect(t.agree('asignados', 'asignadas')).toBe('asignadas');
    expect([t.the, t.these]).toEqual(['la', 'las']);
  });
  it('keeps an acronym in capitals', () => {
    const t = projectTerms({ singular: 'CCPP', plural: 'CCPP', feminine: true });
    expect([t.one, t.One, t.newOne]).toEqual(['CCPP', 'CCPP', 'Nueva CCPP']);
  });
});
