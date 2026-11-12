import { useState, type ReactNode } from 'react';
import { api, type S } from '../api/client';
import { MonthPicker, currentMonth } from '../components/MonthPicker';
import { Notice } from '../components/Notice';
import { Withheld } from '../components/Withheld';
import { useLoad } from '../components/useLoad';
import { PageHeader } from '../ui/PageHeader';
import { Section } from '../ui/Section';
import { Table, Td, Tr } from '../ui/Table';
import styles from './Usage.module.css';

const TOOLS: Record<string, string> = {
  DESCRIBE_ME: 'Perfil', SEARCH: 'Búsqueda', SIMILAR_CASES: 'Casos parecidos', READ_PAGE: 'Leer página', LIST_DOCUMENTS: 'Listar documentos',
  VERIFY_CITATION: 'Verificar cita', SAVE_CARD: 'Guardar ficha', MY_CARDS: 'Mis fichas', MY_QUESTIONS: 'Mis preguntas',
};
const DELIVERED_TEXT = 'Odín no usa ninguna IA externa. La IA de cada trabajador es suya, y Odín no ve lo que gasta. Esto es el volumen de texto que Odín le entrega: no son tokens ni un coste.';

function cardRows(cards: S['CardCountsView']): [string, ReactNode][] {
  const rows: [string, ReactNode][] = [['Fichas aportadas', cards.saved], ['Fichas pendientes de revisión', cards.pending]];
  if (cards.withheld) return [...rows, ['Fichas aprobadas, corregidas y rechazadas', <Withheld key="w" />]];
  return [...rows, ['Fichas aprobadas', cards.approved], ['Fichas corregidas', cards.corrected], ['Fichas rechazadas', cards.rejected]];
}

function companyRows(c: S['CompanyUsageView']): [string, ReactNode][] {
  const tagging: [string, ReactNode][] = c.tagging ? [['Documentos etiquetados', c.tagging.tagged], ['Documentos pendientes de etiquetar', c.tagging.pending]] : [];
  return [
    ['Preguntas de búsqueda', c.questions.search], ['Preguntas de casos parecidos', c.questions.similarCases], ['Preguntas sin resultados', c.questionsWithoutResults],
    ...cardRows(c.cards),
    ['Documentos subidos', c.documents.uploaded], ['Documentos listos', c.documents.ready], ['Documentos que fallaron', c.documents.failed],
    ['Documentos esperando al lector', c.documents.waitingForReader], ...tagging,
  ];
}

function CompanyCounts({ company }: { company: S['CompanyUsageView'] }) {
  return (
    <Section title="Empresa">
      <Table columns={['Concepto', 'Cantidad']} actions={false}>
        {companyRows(company).map(([label, value]) => <Tr key={label}><Td label="Concepto">{label}</Td><Td label="Cantidad">{value}</Td></Tr>)}
      </Table>
    </Section>
  );
}

/** Rows come in the API's order (by name): nothing here sorts, totals or highlights anyone. */
function PeopleTable({ people }: { people: S['PersonUsageView'][] }) {
  return (
    <Section title="Por persona">
      <Table columns={['Persona', 'Código', 'Preguntas', 'Fichas aportadas']} actions={false}>
        {people.map((p) => (
          <Tr key={p.userId ?? p.displayName}>
            <Td label="Persona">{p.displayName}</Td>
            <Td label="Código">{p.memberCode && <code>{p.memberCode}</code>}</Td>
            <Td label="Preguntas">{p.questions}</Td>
            <Td label="Fichas aportadas">{p.cardsSaved}</Td>
          </Tr>
        ))}
      </Table>
      <p className={styles.note}>Recuentos descriptivos. No sirven para valorar a nadie.</p>
    </Section>
  );
}

function DeliveredVolume({ totals }: { totals: S['DeliveryTotalView'][] }) {
  return (
    <Section title="Volumen entregado a la IA del trabajador">
      <p className={styles.note}>{DELIVERED_TEXT}</p>
      <Table columns={['Herramienta', 'Llamadas', 'Bytes']} actions={false}>
        {totals.map((t) => <Tr key={t.tool}><Td label="Herramienta">{TOOLS[t.tool] ?? t.tool}</Td><Td label="Llamadas">{t.calls}</Td><Td label="Bytes">{t.bytes}</Td></Tr>)}
      </Table>
    </Section>
  );
}

export function Usage({ companyId }: { companyId: string }) {
  const [month, setMonth] = useState(currentMonth);
  const stats = useLoad(() => api.usageStats(companyId, month), [companyId, month]);
  const volume = useLoad(() => api.deliveredVolume(companyId, month), [companyId, month]);
  return (
    <div>
      <PageHeader title="Uso" description="Cuánto se ha usado Odín en la empresa durante el mes." actions={<MonthPicker value={month} onChange={setMonth} />} />
      <Notice message={stats.error ?? volume.error} />
      {stats.data && <CompanyCounts company={stats.data.company} />}
      {stats.data && <PeopleTable people={stats.data.people} />}
      {volume.data && <DeliveredVolume totals={volume.data.totals} />}
    </div>
  );
}
