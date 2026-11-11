import { useState } from 'react';
import { api, type S } from '../api/client';
import { Button } from '../ui/Button';
import form from '../ui/Form.module.css';
import { useConfirm } from '../ui/ConfirmDialog';
import { Field } from '../ui/Field';
import { FormDialog } from '../ui/FormDialog';
import { RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Table, tableStyles } from '../ui/Table';
import { Notice } from './Notice';
import { useAction, useLoad } from './useLoad';

function AddProjectDialog({ companyId, open, onClose, onDone }: { companyId: string; open: boolean; onClose: () => void; onDone: () => void }) {
  const [name, setName] = useState('');
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(await act(() => api.createProject(companyId, name)))) return;
    setName('');
    onClose();
    onDone();
  };
  return (
    <FormDialog title="Nuevo proyecto" open={open} onClose={onClose}>
      <form className={form.form} onSubmit={submit}>
        <Notice message={error} />
        <Field label="Nombre del proyecto"><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <div><Button type="submit" variant="primary">Crear proyecto</Button></div>
      </form>
    </FormDialog>
  );
}

function RenameForm({ project, companyId, onDone }: { project: S['ProjectView']; companyId: string; onDone: () => void }) {
  const [name, setName] = useState(project.name);
  const { error, act } = useAction();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await act(() => api.renameProject(companyId, project.id, name))) onDone();
  };
  return (
    <form className={form.form} onSubmit={submit}>
      <Notice message={error} />
      <Field label="Nombre"><input required value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <div className={form.buttons}>
        <Button type="submit" variant="primary">Guardar {project.name}</Button>
        <Button onClick={onDone}>Cancelar {project.name}</Button>
      </div>
    </form>
  );
}

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
          <td colSpan={2}>{editing ? <RenameForm project={project} companyId={companyId} onDone={done} /> : <Notice message={error} />}</td>
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
      <AddProjectDialog companyId={companyId} open={adding} onClose={() => setAdding(false)} onDone={list.reload} />
    </Section>
  );
}
