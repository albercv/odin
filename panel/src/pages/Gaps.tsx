import { useState } from 'react';
import { api } from '../api/client';
import { MonthPicker, currentMonth } from '../components/MonthPicker';
import { Notice } from '../components/Notice';
import { Withheld } from '../components/Withheld';
import { useLoad } from '../components/useLoad';
import { EmptyState } from '../ui/EmptyState';
import { PageHeader } from '../ui/PageHeader';
import { Section } from '../ui/Section';
import { Table, Td, Tr } from '../ui/Table';
import { formatCalendarDay } from './dates';

const KINDS: Record<string, string> = { SEARCH: 'Búsqueda', SIMILAR_CASES: 'Casos parecidos' };
const NOTE = 'Consultas que hizo la IA de un trabajador y que no encontraron nada en los documentos. Pueden indicar documentación que falta.';

export function Gaps({ companyId }: { companyId: string }) {
  const [month, setMonth] = useState(currentMonth);
  const { data, error } = useLoad(() => api.gaps(companyId, month), [companyId, month]);
  return (
    <div>
      <PageHeader title="Huecos" description={NOTE} actions={<MonthPicker value={month} onChange={setMonth} />} />
      <Notice message={error} />
      {data && (
        <Section title="Consultas sin resultados">
          {data.withheld && <Withheld />}
          {!data.withheld && data.gaps.length === 0 && <EmptyState title="Este mes no hay consultas sin resultados" />}
          {!data.withheld && data.gaps.length > 0 && (
            <Table columns={['Consulta', 'Tipo', 'Veces', 'Última vez']} actions={false}>
              {data.gaps.map((g) => (
                <Tr key={`${g.kind}-${g.text}`}>
                  <Td label="Consulta">{g.text}</Td><Td label="Tipo">{KINDS[g.kind] ?? g.kind}</Td>
                  <Td label="Veces">{g.times}</Td><Td label="Última vez">{formatCalendarDay(g.lastAskedOn)}</Td>
                </Tr>
              ))}
            </Table>
          )}
        </Section>
      )}
    </div>
  );
}
