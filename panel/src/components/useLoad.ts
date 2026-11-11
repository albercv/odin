import { useCallback, useEffect, useState } from 'react';

export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string>();
  const run = useCallback(load, deps);
  const reload = useCallback(() => {
    run().then(setData, (e: Error) => setError(e.message));
  }, [run]);
  useEffect(reload, [reload]);
  return { data, error, reload };
}

export function useAction() {
  const [error, setError] = useState<string>();
  const act = async (fn: () => Promise<unknown>) => {
    setError(undefined);
    try {
      await fn();
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    }
  };
  return { error, act };
}
