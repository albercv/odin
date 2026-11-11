import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Table, tableStyles } from '../ui/Table';
import { NameDialog } from './NameDialog';
import { Notice } from './Notice';
import { ProjectMembers, whoSees } from './ProjectMembers';
import { RenameForm } from './RenameForm';
import { useAction, useLoad } from './useLoad';

type Panel = 'rename' | 'access' | undefined;
type RowProps = { project: S['ProjectView']; companyId: string; onChanged: () => void };

function ProjectActions({ project, companyId, onChanged, panel, show }: RowProps & { panel: Panel; show: (p: Panel) => void }) {
  const { error, act } = useAction();
  const confirm = useConfirm();
  const remove = async () => {
    if (!(await confirm({ message: `¿Borrar el proyecto ${project.name}? Sus documentos se conservan.`, confirmLabel: 'Borrar', danger: true }))) return;
    if (await act(() => api.deleteProject(companyId, project.id))) onChanged();
  };
  return (
    <>
      <RowActions label={`Más acciones de ${project.name}`}>
        <RowAction variant="ghost" text="Renombrar" target={project.name} onClick={() => show('rename')} />
        <RowAction variant="ghost" text="Miembros" label="Miembros de" target={project.name} aria-expanded={panel === 'access'} onClick={() => show('access')} />
        <RowAction variant="danger" text="Borrar" target={project.name} onClick={remove} />
      </RowActions>
      <Notice message={error} />
    </>
  );
}

function ProjectRow(props: RowProps) {
  const { project, companyId, onChanged } = props;
  const [panel, setPanel] = useState<Panel>();
  const show = (p: Panel) => setPanel(panel === p ? undefined : p);
  return (
    <>
      <tr>
        <td data-label="Proyecto">{project.name}</td>
        <td data-label="Quién lo ve">{whoSees(project)}</td>
        <td className={tableStyles.actions}><ProjectActions {...props} panel={panel} show={show} /></td>
      </tr>
      {panel && (
        <tr className={tableStyles.panelRow}>
          <td colSpan={3}>{panel === 'rename'
            ? <RenameForm name={project.name} label="Nombre" onSave={(name) => api.updateProject(companyId, project.id, { name })} onSaved={() => { setPanel(undefined); onChanged(); }} onCancel={() => setPanel(undefined)} />
            : <ProjectMembers companyId={companyId} project={project} onChanged={onChanged} />}</td>
        </tr>
      )}
    </>
  );
}

export function Projects({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.projects(companyId), [companyId]);
  const [adding, setAdding] = useState(false);
  return (
    <Section title="Proyectos" description="Un proyecto nuevo nace cerrado: solo lo ven sus miembros." actions={<Button icon="plus" onClick={() => setAdding(true)}>Nuevo proyecto</Button>}>
      <Notice message={list.error} />
      <Table columns={['Proyecto', 'Quién lo ve']}>
        {list.data?.map((p) => <ProjectRow key={p.id} project={p} companyId={companyId} onChanged={list.reload} />)}
      </Table>
      <NameDialog title="Nuevo proyecto" label="Nombre del proyecto" submitLabel="Crear proyecto" open={adding}
        onSubmit={(name) => api.createProject(companyId, name)} onClose={() => setAdding(false)} onDone={list.reload} />
    </Section>
  );
}
