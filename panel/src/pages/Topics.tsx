import { useState } from 'react';
import { api, type S } from '../api/client';
import { Notice } from '../components/Notice';
import { TaggingBacklog } from '../components/TaggingBacklog';
import { TopicDialog } from '../components/TopicDialog';
import { useAction, useLoad } from '../components/useLoad';
import { Button } from '../ui/Button';
import { useConfirm } from '../ui/ConfirmDialog';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import { RowAction, RowActions } from '../ui/RowActions';
import { Section } from '../ui/Section';
import { Table, Td, Tr, tableStyles } from '../ui/Table';
import { useToast } from '../ui/Toast';

type Dialog = { topic?: S['TopicView'] } | undefined;

function TopicRow({ topic, onEdit, onDelete }: { topic: S['TopicView']; onEdit: () => void; onDelete: () => void }) {
  return (
    <Tr>
      <Td label="Nombre">{topic.name}</Td>
      <Td label="Definición">{topic.definition}</Td>
      <Td className={tableStyles.actions}>
        <RowActions label={`Más acciones de ${topic.name}`}>
          <RowAction variant="ghost" text="Editar" target={topic.name} onClick={onEdit} />
          <RowAction variant="danger" text="Borrar" target={topic.name} onClick={onDelete} />
        </RowActions>
      </Td>
    </Tr>
  );
}

function useTopicChanges(companyId: string, reload: () => void) {
  const [version, setVersion] = useState(0);
  const { error, act } = useAction();
  const confirm = useConfirm();
  const toast = useToast();
  const changed = () => { reload(); setVersion((v) => v + 1); toast('Los documentos de esta empresa se volverán a etiquetar'); };
  const remove = async (topic: S['TopicView']) => {
    if (!(await confirm({ message: `¿Borrar el tema ${topic.name}? Dejará de verse en los documentos.`, confirmLabel: 'Borrar', danger: true }))) return;
    if (await act(() => api.deleteTopic(companyId, topic.id))) changed();
  };
  return { version, error, changed, remove };
}

export function Topics({ companyId }: { companyId: string }) {
  const list = useLoad(() => api.topics(companyId), [companyId]);
  const [dialog, setDialog] = useState<Dialog>();
  const { version, error, changed, remove } = useTopicChanges(companyId, list.reload);
  return (
    <div>
      <PageHeader title="Temas" description="La IA etiqueta cada documento con estos temas. Puedes corregirlos en el detalle de cada documento."
        actions={<Button variant="primary" icon="plus" onClick={() => setDialog({})}>Añadir tema</Button>} />
      <TaggingBacklog companyId={companyId} version={version} />
      <Notice message={error ?? list.error} />
      <Section title="Temas de la empresa">
        {list.data?.length === 0 && <EmptyState title="Sin temas no se etiqueta ningún documento" description="Añade el primero con «Añadir tema»." />}
        {list.data && list.data.length > 0 && (
          <Table columns={['Nombre', 'Definición']}>
            {list.data.map((t) => <TopicRow key={t.id} topic={t} onEdit={() => setDialog({ topic: t })} onDelete={() => remove(t)} />)}
          </Table>
        )}
      </Section>
      {dialog && (
        <TopicDialog key={dialog.topic?.id ?? 'new'} title={dialog.topic ? 'Editar tema' : 'Añadir tema'} topic={dialog.topic}
          onSubmit={(b) => (dialog.topic ? api.updateTopic(companyId, dialog.topic.id, b) : api.createTopic(companyId, b))}
          onClose={() => setDialog(undefined)} onDone={changed} />
      )}
    </div>
  );
}
