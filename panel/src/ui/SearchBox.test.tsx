import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
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
  expect(onSearch).not.toHaveBeenCalledWith(expect.stringMatching(/./));
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

it('searchBoxDoesNotReEmitSameValue', () => {
  const onSearch = setup();
  type('abc');
  wait(300);
  type('abc ');
  wait(300);
  expect(onSearch).toHaveBeenCalledTimes(1);
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

it('searchBoxUnmountCancelsPendingTimer', () => {
  const onSearch = setup();
  type('abc');
  cleanup();
  wait(300);
  expect(onSearch).not.toHaveBeenCalled();
});

it('searchBoxEscapeCancelsPendingTimerAndHidesCount', () => {
  const onSearch = vi.fn();
  render(<SearchBox label="Buscar" onSearch={onSearch} resultCount={2} />);
  type('abc');
  wait(300);
  expect(screen.getByText('2 resultados')).toBeInTheDocument();
  type('abcd');
  fireEvent.keyDown(box(), { key: 'Escape' });
  wait(300);
  expect(onSearch).toHaveBeenLastCalledWith('');
  expect(onSearch).toHaveBeenCalledTimes(2);
  expect(screen.queryByText('2 resultados')).not.toBeInTheDocument();
});

it('searchBoxCountStaysHiddenBelowMinLength', () => {
  render(<SearchBox label="Buscar" onSearch={() => {}} resultCount={0} />);
  type('ab');
  wait(300);
  expect(screen.queryByText(/resultado/)).not.toBeInTheDocument();
});

it('searchBoxPendingTimerCallsLatestCallback', () => {
  const first = vi.fn();
  const second = vi.fn();
  const { rerender } = render(<SearchBox label="Buscar" onSearch={first} />);
  type('abc');
  rerender(<SearchBox label="Buscar" onSearch={second} />);
  wait(300);
  expect(first).not.toHaveBeenCalled();
  expect(second).toHaveBeenCalledWith('abc');
});

test('escapeWithTextClearsAndStopsPropagationButEmptyLetsItThrough', () => {
  const outer = vi.fn();
  render(<div onKeyDown={outer}><SearchBox label="Buscar" onSearch={() => {}} /></div>);
  const box = screen.getByRole('searchbox', { name: 'Buscar' });
  fireEvent.change(box, { target: { value: 'abc' } });
  fireEvent.keyDown(box, { key: 'Escape' });
  expect(box).toHaveValue('');
  expect(outer).not.toHaveBeenCalled();
  fireEvent.keyDown(box, { key: 'Escape' });
  expect(outer).toHaveBeenCalledTimes(1);
});
