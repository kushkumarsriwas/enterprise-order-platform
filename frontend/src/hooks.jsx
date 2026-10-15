import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { CircleCheck, CircleX, Info } from "lucide-react";

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
      return data;
    } catch (error) {
      if (current === sequence.current) setState((previous) => ({ ...previous, loading: false, error: error.message }));
      return null;
    }
  }, []);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => void reload(), deps);
  const setData = useCallback(
    (updater) => setState((previous) => ({ ...previous, data: typeof updater === "function" ? updater(previous.data) : updater })),
    [],
  );
  return { ...state, reload, setData };
}

/** Browser tab title for the current page ("Orders | Nexora Seller Central"). */
export function useTitle(title, area = "Nexora") {
  useEffect(() => {
    document.title = title ? `${title} | ${area}` : "Nexora - shop independent stores across the UAE";
  }, [title, area]);
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

export function useClickOutside(ref, onOutside, enabled = true) {
  useEffect(() => {
    if (!enabled) return undefined;
    const handler = (event) => {
      if (ref.current && !ref.current.contains(event.target)) onOutside();
    };
    const onKey = (event) => event.key === "Escape" && onOutside();
    document.addEventListener("mousedown", handler);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", handler);
      document.removeEventListener("keydown", onKey);
    };
  }, [ref, onOutside, enabled]);
}

export function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const list = window.matchMedia(query);
    const onChange = () => setMatches(list.matches);
    list.addEventListener("change", onChange);
    return () => list.removeEventListener("change", onChange);
  }, [query]);
  return matches;
}

/** Light / dark theme: follows the device until the visitor picks one with the header switch. */
export function useTheme() {
  const prefersDark = useMediaQuery("(prefers-color-scheme: dark)");
  const [choice, setChoice] = useState(() => {
    try {
      return localStorage.getItem("nexora_theme");
    } catch {
      return null;
    }
  });
  const dark = choice ? choice === "dark" : prefersDark;
  useEffect(() => {
    if (choice) document.documentElement.dataset.theme = choice;
    else delete document.documentElement.dataset.theme;
  }, [choice]);
  const toggle = useCallback(() => {
    const next = dark ? "light" : "dark";
    try {
      localStorage.setItem("nexora_theme", next);
    } catch {
      /* the choice lasts for this visit */
    }
    setChoice(next);
  }, [dark]);
  return { dark, toggle };
}

// ---------------------------------------------------------------- toasts

const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const show = useCallback((message, kind = "good") => {
    const id = Math.random().toString(36).slice(2);
    setToasts((current) => [...current.slice(-2), { id, message, kind }]);
    setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), kind === "bad" ? 6500 : 4200);
  }, []);
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.kind}`} role={toast.kind === "bad" ? "alert" : "status"}>
            {toast.kind === "good" ? <CircleCheck /> : toast.kind === "bad" ? <CircleX /> : <Info />}
            <span>{toast.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/** toast("Saved") / toast("Could not save", "bad") */
export const useToast = () => useContext(ToastContext);
