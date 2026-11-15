import { projectTerms, type ProjectTerms } from './projectTerms';

/** «Además ve/verá los N proyectos visibles para toda la empresa.»; `verb` is the conjugation that fits the screen. */
export function widelyVisible(count: number, verb: 've' | 'verá', t: ProjectTerms = projectTerms()) {
  return count === 1
    ? `Además ${verb} 1 ${t.one} visible para toda la empresa.`
    : `Además ${verb} ${t.these} ${count} ${t.many} visibles para toda la empresa.`;
}
