import { useCallback, useEffect, useRef, useState } from "react";

/** Loads data with loading / error state and a reload function. Keeps the previous data while reloading. */
export function useLoader(loader, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const loaderRef = useRef(loader);
  useEffect(() => {
    loaderRef.current = loader;
  });
  const sequence = useRef(0);
  const reload = useCallback(async () => {
    const current = ++sequence.current;
    setState((previous) => ({ ...previous, loading: true, error: null }));
    try {
      const data = await loaderRef.current();
      if (current === sequence.current) setState({ data, loading: false, error: null });
    } catch (error) {
      if (current === sequence.current) setState((previous) => ({ ...previous, loading: false, error: error.message }));
    }
  }, []);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => void reload(), deps);
  return { ...state, reload };
}

export function useInterval(callback, ms, enabled = true) {
  const saved = useRef(callback);
  useEffect(() => {
    saved.current = callback;
  });
  useEffect(() => {
    if (!enabled) return undefined;
    const timer = setInterval(() => saved.current(), ms);
    return () => clearInterval(timer);
  }, [ms, enabled]);
}

export function useDebounced(value, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

export function useToast() {
  const [toast, setToast] = useState(null);
  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);
  const show = useCallback((message, kind = "success") => setToast({ message, kind }), []);
  const node = toast ? (
    <div className={`toast toast-${toast.kind}`} role="status">
      {toast.message}
    </div>
  ) : null;
  return { show, node };
}

export function useTheme() {
  const [dark, setDark] = useState(() => {
    try {
      return localStorage.getItem("nexora_theme") === "dark";
    } catch {
      return false;
    }
  });
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    try {
      localStorage.setItem("nexora_theme", dark ? "dark" : "light");
    } catch {
      /* ignore */
    }
  }, [dark]);
  return [dark, setDark];
}
