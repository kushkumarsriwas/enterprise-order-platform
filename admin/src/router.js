import { useCallback, useEffect, useState } from "react";

// Hash routing keeps static hosting simple (no server-side rewrite rules). "#/shop?q=phone" -> path + query.
function read() {
  const raw = window.location.hash.replace(/^#/, "") || "/";
  const [path, query = ""] = raw.split("?");
  return { path: path || "/", query: Object.fromEntries(new URLSearchParams(query)) };
}

export function useRoute() {
  const [route, setRoute] = useState(read);
  useEffect(() => {
    const onChange = () => setRoute(read());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  const navigate = useCallback((to, { replace = false, keepScroll = false } = {}) => {
    if (replace) {
      window.history.replaceState(null, "", `#${to}`);
      setRoute(read());
    } else {
      window.location.hash = to;
      // the URL changes at once but "hashchange" fires later: update the route now so the next render sees it
      setRoute(read());
    }
    if (!keepScroll) window.scrollTo(0, 0);
  }, []);
  return [route, navigate];
}

/** match("/orders/:id", "/orders/12") -> { id: "12" }; null when the path does not match. */
export function match(pattern, path) {
  const expected = pattern.split("/");
  const actual = path.split("/");
  if (expected.length !== actual.length) return null;
  const params = {};
  for (let i = 0; i < expected.length; i++) {
    if (expected[i].startsWith(":")) params[expected[i].slice(1)] = decodeURIComponent(actual[i]);
    else if (expected[i] !== actual[i]) return null;
  }
  return params;
}
