const LONG = { day: 'numeric', month: 'long', year: 'numeric' } as const;

/** «10 de noviembre de 2026», in the installation's time zone when given. */
export const formatLongDay = (iso: string, timeZone?: string) => new Date(iso).toLocaleDateString('es-ES', { ...LONG, timeZone });

/** A calendar day «YYYY-MM-DD» has no time zone: it is written as it comes. */
export const formatCalendarDay = (day: string) => {
  const [year = 0, month = 1, date = 1] = day.split('-').map(Number);
  return new Date(year, month - 1, date).toLocaleDateString('es-ES', LONG);
};
