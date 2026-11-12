/** Confirmation texts for actions on the whole person, shared by the members table and the group admins page. */
export const confirmDeactivatePerson = (name: string) =>
  `¿Desactivar a ${name} en todo el grupo? No podrá usar Odín en ninguna empresa hasta que se reactive. Sus claves y sus proyectos se conservan.`;

export const confirmDeletePerson = (name: string) =>
  `¿Borrar a ${name}? Desaparece de todas las empresas y de sus proyectos, y sus claves dejan de valer. No se puede deshacer.`;
