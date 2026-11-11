import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Table, tableStyles } from '../ui/Table';
import { NameDialog } from './NameDialog';
import { Notice } from './Notice';
import { RenameForm } from './RenameForm';
import { useAction, useLoad } from './useLoad';

function ProjectRow({ project, companyId, onChanged }: { project: S['ProjectView']; companyId: string; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const { error, act } = useAction();
  const confirm = useConfirm();
  const remove = async () => {
    if (!(await confirm({ message: `¿Borrar el proyecto ${project.name}? Sus documentos se conservan.`, confirmLabel: 'Borrar', danger: true }))) return;
    if (await act(() => api.deleteProject(companyId, project.id))) onChanged();
  };
  const done = () => { setEditing(false); onChanged(); };
  return (
    <>
      <tr>
        <td>{project.name}</td>
        <td className={tableStyles.actions}>
          <RowActions label={`Más acciones de ${project.name}`}>
            <Button variant="ghost" onClick={() => setEditing(true)}>Renombrar {project.name}</Button>
            <Button variant="danger" onClick={remove}>Borrar {project.name}</Button>
          </RowActions>
        </td>
      </tr>
      {(editing || error) && (
        <tr className={tableStyles.panelRow}>
          <td colSpan={2}>{editing
            ? <RenameForm name={project.name} label="Nombre" onSave={(n) => api.renameProject(companyId, project.id, n)} onSaved={done} onCancel={() => setEditing(false)} />
            : <Notice message={error} />}</td>
        </tr>
      )}
    </>
  );
}

export function Projects({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.projects(companyId), [companyId]);
  const [adding, setAdding] = useState(false);
  return (
    <Section title="Proyectos" actions={<Button icon="plus" onClick={() => setAdding(true)}>Nuevo proyecto</Button>}>
      <Notice message={list.error} />
      <Table columns={['Proyecto']}>
        {list.data?.map((p) => <ProjectRow key={p.id} project={p} companyId={companyId} onChanged={list.reload} />)}
      </Table>
      <NameDialog title="Nuevo proyecto" label="Nombre del proyecto" submitLabel="Crear proyecto" open={adding}
        onSubmit={(name) => api.createProject(companyId, name)} onClose={() => setAdding(false)} onDone={list.reload} />
    </Section>
  );
}
