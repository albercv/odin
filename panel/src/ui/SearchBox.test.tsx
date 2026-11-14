import { act, fireEvent, render, screen } from '@testing-library/react';
import { SearchBox } from './SearchBox';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

const box = () => screen.getByRole('searchbox');
const type = (value: string) => fireEvent.change(box(), { target: { value } });
const wait = (ms: number) => act(() => { vi.advanceTimersByTime(ms); });

function setup(resultCount?: number) {
  const onSearch = vi.fn();
  render(<SearchBox label="Buscar documentos" onSearch={onSearch} resultCount={resultCount} />);
  return onSearch;
}

it('searchBoxHasSearchboxRoleWithAccessibleName', () => {
  setup();
  expect(screen.getByRole('searchbox', { name: 'Buscar documentos' })).toBeInTheDocument();
  expect(screen.getByRole('search')).toBeInTheDocument();
});

it('searchBoxIgnoresTwoLettersAndSearchesOnThirdAfterDelay', () => {
  const onSearch = setup();
  type('ab');
  wait(300);
  expect(onSearch).not.toHaveBeenCalledWith('ab');
  type('abc');
  wait(299);
  expect(onSearch).not.toHaveBeenCalledWith('abc');
  wait(1);
  expect(onSearch).toHaveBeenLastCalledWith('abc');
});

it('searchBoxDeliversEmptyWhenShorterThanMinimum', () => {
  const onSearch = setup();
  type('abc');
  wait(300);
  type('ab');
  wait(300);
  expect(onSearch).toHaveBeenLastCalledWith('');
});

it('searchBoxEscapeClearsText', () => {
  const onSearch = setup();
  type('abc');
  wait(300);
  fireEvent.keyDown(box(), { key: 'Escape' });
  expect(box()).toHaveValue('');
  expect(onSearch).toHaveBeenLastCalledWith('');
});

it('searchBoxCountIsLivePoliteWithPluralAndEmptyTexts', () => {
  const { rerender } = render(<SearchBox label="Buscar" onSearch={() => {}} resultCount={1} />);
  type('xyz');
  wait(300);
  expect(screen.getByText('1 resultado')).toHaveAttribute('aria-live', 'polite');
  rerender(<SearchBox label="Buscar" onSearch={() => {}} resultCount={3} />);
  expect(screen.getByText('3 resultados')).toBeInTheDocument();
  rerender(<SearchBox label="Buscar" onSearch={() => {}} resultCount={0} />);
  expect(screen.getByText('Ningún resultado para «xyz»')).toBeInTheDocument();
});
