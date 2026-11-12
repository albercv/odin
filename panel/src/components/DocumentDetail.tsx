import type { S } from '../api/client';
import { STATUS } from '../pages/documentStatus';
import { formatDate, projectName } from '../pages/reviewNames';
import { FormDialog } from '../ui/FormDialog';
import { StatusPill } from '../ui/StatusPill';
import styles from './DocumentDetail.module.css';

type Props = { document: S['DocumentView']; projects: S['ProjectView'][]; onClose: () => void };

export function DocumentDetail({ document: d, projects, onClose }: Props) {
  const [label, tone] = STATUS[d.status] ?? [d.status, 'off' as const];
  return (
    <FormDialog title={d.title} open onClose={onClose}>
      <dl className={styles.facts}>
        <div><dt>Estado</dt><dd><StatusPill tone={tone}>{label}</StatusPill></dd></div>
        <div><dt>Proyecto</dt><dd>{projectName(d.projectId, projects)}</dd></div>
        <div><dt>Fecha de alta</dt><dd>{formatDate(d.uploadedAt)}</dd></div>
        {d.supersededBy && (
          <div>
            <dt>Sustitución</dt>
            <dd>{`Sustituido por ${d.supersededBy.title}`}</dd>
            {d.supersededAt && <dd>{`Marcado el ${formatDate(d.supersededAt)}`}</dd>}
          </div>
        )}
      </dl>
    </FormDialog>
  );
}
