import type { Me } from './api/client.js';

export type ProjectLabel = Me['company']['projectLabel'];

// Las palabras con las que el conector nombra los proyectos de la empresa, ya concordadas en género.
export interface ProjectTerms {
  singular: string;
  plural: string;
  capital: string;
  one: string;
  the: string;
  ofThe: string;
  none: string;
  that: string;
  toWhich: string;
  thePlural: string;
  toWhichPlural: string;
}

export function projectTerms({ singular, plural, feminine }: ProjectLabel): ProjectTerms {
  const capital = singular === singular.toUpperCase() ? singular : singular.charAt(0).toUpperCase() + singular.slice(1);
  return {
    singular, plural, capital,
    one: `un${feminine ? 'a' : ''} ${singular}`,
    the: feminine ? 'la' : 'el',
    ofThe: feminine ? 'de la' : 'del',
    none: feminine ? 'ninguna' : 'ningún',
    that: `es${feminine ? 'a' : 'e'}`,
    toWhich: feminine ? 'a la que' : 'al que',
    thePlural: feminine ? 'las' : 'los',
    toWhichPlural: feminine ? 'a las que' : 'a los que',
  };
}

export const DEFAULT_TERMS = projectTerms({ singular: 'proyecto', plural: 'proyectos', feminine: false });

export const noProject = (t: ProjectTerms) => `Sin ${t.singular} (general de la empresa)`;
export const unavailableProject = (t: ProjectTerms) => `${t.capital} no disponible`;
