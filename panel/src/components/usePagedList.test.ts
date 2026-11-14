import { act, renderHook } from '@testing-library/react';
import { includesText, usePagedList } from './usePagedList';

const matches = includesText;
const nums = Array.from({ length: 25 }, (_, i) => `item${i + 1}`);

it('usePagedListNewSearchGoesBackToPageOne', () => {
  const { result } = renderHook(() => usePagedList(nums, matches, 10));
  act(() => result.current.setPage(3));
  expect(result.current.page).toBe(3);
  act(() => result.current.setQuery('item1'));
  expect(result.current.page).toBe(1);
  expect(result.current.total).toBe(11);
});

it('usePagedListStaysOnPreviousPageWhenLastPageEmpties', () => {
  const { result, rerender } = renderHook(({ items }) => usePagedList(items, matches, 10), {
    initialProps: { items: nums },
  });
  act(() => result.current.setPage(3));
  rerender({ items: nums.slice(0, 20) });
  expect(result.current.page).toBe(2);
  expect(result.current.visible).toHaveLength(10);
});

it('usePagedListIgnoresCaseAndAccents', () => {
  const items = ['Ángel Núñez', 'otro'];
  const { result } = renderHook(() => usePagedList(items, matches, 10));
  act(() => result.current.setQuery('ANGEL nunez'));
  expect(result.current.visible).toEqual(['Ángel Núñez']);
});

it('usePagedListEmptyQueryShowsAll', () => {
  const { result } = renderHook(() => usePagedList(nums, matches, 10));
  expect(result.current.visible).toHaveLength(10);
  expect(result.current.pageCount).toBe(3);
});

it('usePagedListSetPageClampsToValidRange', () => {
  const { result } = renderHook(() => usePagedList(nums, matches, 10));
  act(() => result.current.setPage(99));
  expect(result.current.page).toBe(3);
  act(() => result.current.setPage(0));
  expect(result.current.page).toBe(1);
});

it('usePagedListClampedPageDoesNotComeBackWhenListGrows', () => {
  const { result, rerender } = renderHook(({ items }) => usePagedList(items, matches, 10), {
    initialProps: { items: nums },
  });
  act(() => result.current.setPage(3));
  rerender({ items: nums.slice(0, 20) });
  act(() => result.current.setPage(2));
  rerender({ items: nums });
  expect(result.current.page).toBe(2);
});

it('includesTextIgnoresCaseAndAccentsOfText', () => {
  expect(includesText('Ángel', 'angel')).toBe(true);
});
