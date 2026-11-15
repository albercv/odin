import { createContext, useContext } from 'react';
import type { S } from '../api/client';

export type ProjectLabel = S['ProjectLabelView'];

export const DEFAULT_LABEL: ProjectLabel = { singular: 'proyecto', plural: 'proyectos', feminine: false };
const capital = (word: string) => word.charAt(0).toUpperCase() + word.slice(1);

/** The company's word for «project» in every form the screens need, with its grammatical gender. */
export function projectTerms(label: ProjectLabel = DEFAULT_LABEL) {
  const agree = <T extends string>(masculine: T, feminine: T) => (label.feminine ? feminine : masculine);
  return {
    one: label.singular, many: label.plural, One: capital(label.singular), Many: capital(label.plural),
    aOne: `${agree('un', 'una')} ${label.singular}`, AOne: `${agree('Un', 'Una')} ${label.singular}`, newOne: `${agree('Nuevo', 'Nueva')} ${label.singular}`, noneOne: `Sin ${label.singular}`,
    the: agree('el', 'la'), these: agree('los', 'las'), agree,
  };
}

export type ProjectTerms = ReturnType<typeof projectTerms>;

export const ProjectTermsContext = createContext<ProjectTerms>(projectTerms());

export const useProjectTerms = () => useContext(ProjectTermsContext);
