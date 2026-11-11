import { useId } from 'react';
import type { S } from '../api/client';
import { Icon } from '../ui/Icon';
import styles from './CompanySwitcher.module.css';

export type SwitcherProps = { companies: S['CompanyRef'][]; currentId: string; onChange: (companyId: string) => void };

/** Picks among the companies a person administers; nothing to pick with just one. */
export function CompanySwitcher({ companies, currentId, onChange }: SwitcherProps) {
  const id = useId();
  if (companies.length < 2) return null;
  return (
    <div className={styles.switcher}>
      <label htmlFor={id}>Empresa</label>
      <div className={styles.control}>
        <select id={id} value={currentId} onChange={(e) => onChange(e.target.value)}>
          {companies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <Icon name="chevronDown" />
      </div>
    </div>
  );
}
