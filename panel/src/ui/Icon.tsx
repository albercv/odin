export type IconName = 'building' | 'buildings' | 'file' | 'review' | 'user' | 'logout' | 'lock' | 'people' | 'key'
  | 'more' | 'chevronDown' | 'plus' | 'spark' | 'done' | 'upload' | 'close';

const SHAPES: Record<IconName, string> = {
  building: 'M4 4.5A1.5 1.5 0 0 1 5.5 3h13A1.5 1.5 0 0 1 20 4.5v15a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5zM9 7h.01M15 7h.01M9 11h.01M15 11h.01M9 15h.01M15 15h.01M10 21v-3h4v3',
  buildings: 'M3 9.2A1.2 1.2 0 0 1 4.2 8h6.6A1.2 1.2 0 0 1 12 9.2V21H3zM12 21V4.5A1.5 1.5 0 0 1 13.5 3h6A1.5 1.5 0 0 1 21 4.5V21M6.5 12h2M6.5 16h2M15.5 7h2M15.5 11h2M15.5 15h2',
  file: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h4',
  review: 'M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zM8.5 12l2.5 2.5 4.5-5',
  user: 'M16 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0zM4 21a8 8 0 0 1 16 0',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4',
  lock: 'M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V8a4 4 0 0 1 8 0v3',
  people: 'M12.5 8a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0zM2.5 20a6.5 6.5 0 0 1 13 0M16 4.6a3.5 3.5 0 0 1 0 6.8M18.5 14.4A6.5 6.5 0 0 1 21.5 20',
  key: 'M12 15a4 4 0 1 1-8 0 4 4 0 0 1 8 0zM11 12l9-9M16 7l2.5 2.5M14 9l2 2',
  more: 'M6 12a1 1 0 1 1-2 0 1 1 0 0 1 2 0zM13 12a1 1 0 1 1-2 0 1 1 0 0 1 2 0zM20 12a1 1 0 1 1-2 0 1 1 0 0 1 2 0z',
  chevronDown: 'm7 10 5 5 5-5',
  plus: 'M12 5v14M5 12h14',
  spark: 'M12 3c.5 4.5 2.5 6.5 7 7-4.5.5-6.5 2.5-7 7-.5-4.5-2.5-6.5-7-7 4.5-.5 6.5-2.5 7-7zM19 15.5c.2 1.6.9 2.3 2.5 2.5-1.6.2-2.3.9-2.5 2.5-.2-1.6-.9-2.3-2.5-2.5 1.6-.2 2.3-.9 2.5-2.5z',
  done: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0zM8 12l3 3 5-6',
  upload: 'M12 16V4M7 9l5-5 5 5M5 20h14',
  close: 'M6 6l12 12M18 6 6 18',
};

export function Icon({ name }: { name: IconName }) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false" fill="none"
      stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ flex: 'none' }}>
      <path d={SHAPES[name]} />
    </svg>
  );
}
