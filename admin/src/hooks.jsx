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

/** Browser tab title for the current page ("Orders | Nexora Control Center"). */
export function useTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} | Nexora Control Center` : "Nexora Control Center";
  }, [title]);
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

/** Light / dark theme: follows the device until the administrator picks one in the command bar. */
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

const NUMBER = /-?\d[\d,]*(\.\d+)?/;
const reducedMotion = () => typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

/** The number inside a figure ("AED 12,480.50"), with the text around it and how it is formatted. */
function parseFigure(value) {
  const text = typeof value === "number" ? String(value) : value;
  const match = typeof text === "string" ? NUMBER.exec(text) : null;
  if (!match) return null;
  const decimals = match[1] ? match[1].length - 1 : 0;
  return {
    target: parseFloat(match[0].replace(/,/g, "")),
    before: text.slice(0, match.index),
    after: text.slice(match.index + match[0].length),
    format: new Intl.NumberFormat("en-US", { minimumFractionDigits: decimals, maximumFractionDigits: decimals, useGrouping: match[0].includes(",") }),
  };
}

/**
 * Counts a figure up to its value when it first appears or changes, keeping the text around the number and its
 * formatting. Anything without a number is returned as it is.
 */
export function useCountUp(value, duration = 900) {
  const [frame, setFrame] = useState(() => {
    const figure = parseFigure(value);
    return figure && figure.target !== 0 && !reducedMotion() ? { value, text: `${figure.before}${figure.format.format(0)}${figure.after}` } : null;
  });
  const previous = useRef(null);
  useEffect(() => {
    const figure = parseFigure(value);
    const from = previous.current ?? 0;
    previous.current = figure ? figure.target : null;
    if (!figure || from === figure.target || reducedMotion()) return undefined;
    const started = performance.now();
    let request;
    const step = (now) => {
      const progress = Math.min(1, (now - started) / duration);
      const current = from + (figure.target - from) * (1 - Math.pow(1 - progress, 3));
      setFrame(progress < 1 ? { value, text: `${figure.before}${figure.format.format(current)}${figure.after}` } : null);
      if (progress < 1) request = requestAnimationFrame(step);
    };
    request = requestAnimationFrame(step);
    return () => cancelAnimationFrame(request);
  }, [value, duration]);
  return frame && frame.value === value ? frame.text : value;
}
