/** «Además ve/verá los N proyectos visibles para toda la empresa.»; `verb` is the conjugation that fits the screen. */
export function widelyVisible(count: number, verb: 've' | 'verá') {
  return count === 1
    ? `Además ${verb} 1 proyecto visible para toda la empresa.`
    : `Además ${verb} los ${count} proyectos visibles para toda la empresa.`;
}
