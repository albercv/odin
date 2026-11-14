import styles from './PageSizeSelect.module.css';

export function PageSizeSelect({ value, options = [10, 25, 50], onChange }: {
  value: number; options?: number[]; onChange: (size: number) => void;
}) {
  return (
    <label className={styles.label}>
      Por página
      <select value={value} onChange={(e) => onChange(Number(e.target.value))}>
        {options.map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
    </label>
  );
}
