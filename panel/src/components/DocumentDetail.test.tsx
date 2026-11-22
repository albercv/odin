import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, vi } from 'vitest';
import { DocumentDetail } from './DocumentDetail';
import { mockFetch } from '../mockApi';
import { renderWithProviders } from '../ui/renderWithProviders';

afterEach(() => vi.restoreAllMocks());

const doc = { id: 'd1', companyId: 'c1', title: 'Póliza 2022', status: 'READY', uploadedAt: '2026-11-01T10:00:00Z', scanCopies: [] };

test('theDetailOpensTheSupersedeDialogFromItsOwnButton', async () => {
  mockFetch({
    'GET /v1/admin/documents/d1/context': () => ({ versions: [], links: [] }),
    'GET /v1/admin/documents/d1/topics': () => ({ topics: [], tagging: { status: 'DONE', topicListVersion: 1 } }),
    'GET /v1/admin/companies/c1/topics': () => [],
    'GET /v1/admin/companies/c1/documents?offset=0&limit=50': () => ({ documents: [], total: 0 }),
  });
  renderWithProviders(<DocumentDetail document={doc as never} projects={[]} onClose={() => {}} />);
  await userEvent.click(await screen.findByRole('button', { name: 'Marcar como sustituido…' }));
  expect(await screen.findByRole('heading', { name: 'Marcar como sustituido' })).toBeInTheDocument();
});
