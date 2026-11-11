import { useId } from 'react';
import type { S } from '../api/client';
import styles from '../ui/Form.module.css';

export type RoleChoice = { admin: boolean; worker: boolean };
type Role = S['MemberView']['roles'][number];

export const choiceOf = (roles: Role[]): RoleChoice => ({ admin: roles.includes('COMPANY_ADMIN'), worker: roles.includes('WORKER') });
export const rolesOf = ({ admin, worker }: RoleChoice): Role[] => [...(admin ? ['COMPANY_ADMIN' as const] : []), ...(worker ? ['WORKER' as const] : [])];

type Props = { choice: RoleChoice; onChange: (choice: RoleChoice) => void; canGiveAdmin: boolean; error?: string };

/** The two role checkboxes; «Administra la empresa» only shows to whoever may give it. */
export function RolesFields({ choice, onChange, canGiveAdmin, error }: Props) {
  const errorId = useId();
  const invalid = Boolean(error);
  return (
    <fieldset className={styles.group} aria-describedby={invalid ? errorId : undefined}>
      <legend>Papeles</legend>
      {canGiveAdmin && (
        <label className={styles.check}>
          <input type="checkbox" aria-invalid={invalid} checked={choice.admin} onChange={(e) => onChange({ ...choice, admin: e.target.checked })} />Administra la empresa
        </label>
      )}
      <label className={styles.check}>
        <input type="checkbox" aria-invalid={invalid} checked={choice.worker} onChange={(e) => onChange({ ...choice, worker: e.target.checked })} />Trabaja con su IA
      </label>
      {error && <p id={errorId} role="alert" className={styles.error}>{error}</p>}
    </fieldset>
  );
}
