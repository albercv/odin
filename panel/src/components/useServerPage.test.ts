import { act, renderHook, waitFor } from '@testing-library/react';
import { useServerPage } from './useServerPage';

type Resolver = (r: { items: string[]; total: number }) => void;

test('staleResponseIsDropped', async () => {
  const pending: Record<string, Resolver> = {};
  const fetchPage = (q: string) => new Promise<{ items: string[]; total: number }>((res) => { pending[q] = res; });
  const { result } = renderHook(() => useServerPage(fetchPage, []));
  act(() => result.current.setQuery('pol'));
  act(() => result.current.setQuery('poli'));
  await act(async () => pending['poli']?.({ items: ['poli'], total: 1 }));
  await act(async () => pending['pol']?.({ items: ['pol'], total: 1 }));
  await waitFor(() => expect(result.current.items).toEqual(['poli']));
});

test('reloadKeepsThePageOrFallsToTheLastOne', async () => {
  let total = 60;
  const fetchPage = vi.fn(async (_q: string, offset: number) => ({ items: [`o${offset}`], total }));
  const { result } = renderHook(() => useServerPage(fetchPage, []));
  await waitFor(() => expect(result.current.items).toEqual(['o0']));
  act(() => result.current.setPage(3));
  await waitFor(() => expect(result.current.items).toEqual(['o50']));
  act(() => result.current.reload());
  await waitFor(() => expect(fetchPage).toHaveBeenLastCalledWith('', 50, 25));
  total = 30;
  act(() => result.current.reload());
  await waitFor(() => expect(result.current.page).toBe(2));
  await waitFor(() => expect(result.current.items).toEqual(['o25']));
});

test('depsChangeResetsToPageOnePermanently', async () => {
  const fetchPage = vi.fn(async (_q: string, offset: number) => ({ items: [`o${offset}`], total: 90 }));
  const { result, rerender } = renderHook(({ w }) => useServerPage(fetchPage, [w]), { initialProps: { w: false } });
  act(() => result.current.setPage(3));
  await waitFor(() => expect(result.current.items).toEqual(['o50']));
  rerender({ w: true });
  rerender({ w: false });
  await waitFor(() => expect(result.current.page).toBe(1));
  await waitFor(() => expect(result.current.items).toEqual(['o0']));
});
