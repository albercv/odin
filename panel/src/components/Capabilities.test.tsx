import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Capabilities } from './Capabilities';

const company = { id: 'c1', name: 'Acme', code: 'ACM', capabilities: ['SEARCH_KNOWLEDGE', 'SIMILAR_CASES'] } as never;

test('showsSimilarCasesCapability', () => {
  const { rerender } = render(<Capabilities company={company} />);
  expect(screen.getByText('Casos parecidos · activa')).toBeInTheDocument();
  rerender(<Capabilities company={company} onChange={() => undefined} />);
  expect(screen.getByRole('switch', { name: 'Casos parecidos' })).toBeChecked();
});

test('showsCaseFilesCapabilityAsExpedientes', () => {
  render(<Capabilities company={company} />);
  expect(screen.getByText('Expedientes · desactivada')).toBeInTheDocument();
});

test('uploadDocumentsIsReadOnlyAsPillAndTogglesAsSwitch', async () => {
  const onChange = vi.fn();
  const { rerender } = render(<Capabilities company={company} />);
  expect(screen.getByText('Subir documentos · desactivada')).toBeInTheDocument();
  rerender(<Capabilities company={company} onChange={onChange} />);
  await userEvent.click(screen.getByRole('switch', { name: 'Subir documentos' }));
  expect(onChange).toHaveBeenCalledWith(['SEARCH_KNOWLEDGE', 'SIMILAR_CASES', 'UPLOAD_DOCUMENTS']);
});
