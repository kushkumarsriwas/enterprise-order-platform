import { useCallback, useEffect, useRef, useState } from "react";

/** Loads data with loading / error state and a reload function. */
export function useLoader(loader, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const reload = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      setState({ data: await loaderRef.current(), loading: false, error: null });
    } catch (error) {
      setState((current) => ({ ...current, loading: false, error: error.message }));
    }
  }, []);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => void reload(), deps);
  return { ...state, reload };
}

export function useToast() {
  const [toast, setToast] = useState(null);
  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);
  const node = toast ? <div className={`toast toast-${toast.kind}`}>{toast.message}</div> : null;
  return { show: (message, kind = "success") => setToast({ message, kind }), node };
}
