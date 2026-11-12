import { Field } from '../ui/Field';

export const monthKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
export const currentMonth = () => monthKey(new Date());

/** The current month and the 11 before it: the API answers for no more. */
function lastTwelveMonths(): { value: string; label: string }[] {
  const now = new Date();
  return Array.from({ length: 12 }, (_, back) => {
    const date = new Date(now.getFullYear(), now.getMonth() - back, 1);
    return { value: monthKey(date), label: date.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' }) };
  });
}

export function MonthPicker({ value, onChange }: { value: string; onChange: (month: string) => void }) {
  return (
    <Field label="Mes">
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        {lastTwelveMonths().map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
      </select>
    </Field>
  );
}
