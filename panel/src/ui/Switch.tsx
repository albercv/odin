import styles from './Switch.module.css';

type Props = { label: string; checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean };

export function Switch({ label, checked, onChange, disabled }: Props) {
  return (
    <label className={styles.switch}>
      <input type="checkbox" role="switch" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      {label}
    </label>
  );
}
