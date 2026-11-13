import type { Capability, S } from '../api/client';
import { StatusPill } from '../ui/StatusPill';
import { Switch } from '../ui/Switch';
import styles from './Capabilities.module.css';

const CAPABILITIES: [Capability, string][] = [
  ['SEARCH_KNOWLEDGE', 'Buscar conocimiento'],
  ['SAVE_KNOWLEDGE_CARD', 'Guardar fichas'],
  ['SIMILAR_CASES', 'Casos parecidos'],
  ['CASE_FILES', 'Expedientes'],
];

type Props = { company: S['CompanyView']; onChange?: (capabilities: Capability[]) => void };

/** Switches when `onChange` is given, read-only pills otherwise. */
export function Capabilities({ company, onChange }: Props) {
  const has: Capability[] = company.capabilities;
  const toggle = (cap: Capability) => onChange?.(has.includes(cap) ? has.filter((x) => x !== cap) : [...has, cap]);
  return (
    <div role="group" aria-label={`Capacidades de ${company.name}`} className={styles.group}>
      {CAPABILITIES.map(([cap, text]) => onChange
        ? <Switch key={cap} label={text} checked={has.includes(cap)} onChange={() => toggle(cap)} />
        : <StatusPill key={cap} tone={has.includes(cap) ? 'ok' : 'off'}>{`${text} · ${has.includes(cap) ? 'activa' : 'desactivada'}`}</StatusPill>)}
    </div>
  );
}
