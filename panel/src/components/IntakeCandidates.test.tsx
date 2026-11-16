import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { IntakeCandidates } from './IntakeCandidates';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const base = '/v1/admin/companies/c1/intake-batches/b1/project-candidates';
const candidate = (name: string, state = 'PROPOSED') => ({ name, origin: 'DISCOVERED', documentCount: 3, state });
const list = { candidates: [candidate('Residencial Mirador del Puerto'), candidate('Residencial Mirador'), candidate('Calle Haya 2', 'REJECTED')] };

const show = () => {
  const calls = mockFetch({ [`GET ${base}`]: () => list, [`POST ${base}/rename`]: () => list, [`POST ${base}/merge`]: () => list });
  renderWithProviders(<IntakeCandidates companyId="c1" batchId="b1" projects={[]} onChanged={() => undefined} />);
  return calls;
};

test('renamesACandidateWithTheEditedName', async () => {
  const calls = show();
  const input = await screen.findByLabelText('Nuevo nombre de Residencial Mirador');
  expect(screen.getByRole('button', { name: 'Renombrar Residencial Mirador' })).toBeDisabled();
  await userEvent.clear(input);
  await userEvent.type(input, 'Residencial Mirador del Puerto');
  await userEvent.click(screen.getByRole('button', { name: 'Renombrar Residencial Mirador' }));
  await waitFor(() => expect(calls.find((c) => c.key === `POST ${base}/rename`)?.body)
    .toEqual({ name: 'Residencial Mirador', newName: 'Residencial Mirador del Puerto' }));
});

test('joinsACandidateIntoAnotherProposedOneAndNeverOffersADecidedOne', async () => {
  const calls = show();
  const select = await screen.findByLabelText('Residencial Mirador es el mismo que');
  expect(Array.from((select as HTMLSelectElement).options).map((o) => o.text)).toEqual(['Es el mismo que…', 'Residencial Mirador del Puerto']);
  await userEvent.selectOptions(select, 'Residencial Mirador del Puerto');
  await userEvent.click(screen.getByRole('button', { name: 'Juntar Residencial Mirador' }));
  await waitFor(() => expect(calls.find((c) => c.key === `POST ${base}/merge`)?.body)
    .toEqual({ names: ['Residencial Mirador'], into: 'Residencial Mirador del Puerto' }));
});
