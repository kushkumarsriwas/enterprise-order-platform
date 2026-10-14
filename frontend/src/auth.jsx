import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api, setUnauthorizedHandler, tokenStore } from "./api.js";

const AuthContext = createContext(null);
const USER_KEY = "nexora_user";

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || "null");
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => (tokenStore.get() ? readUser() : null));
  const [sessionExpired, setSessionExpired] = useState(false);

  const store = useCallback((response) => {
    tokenStore.set(response.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    setSessionExpired(false);
    setUser(response.user);
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout();
      setSessionExpired(true);
    });
  }, [logout]);

  const value = useMemo(
    () => ({
      user,
      isAdmin: user?.role === "ADMIN",
      sessionExpired,
      login: async (username, password) =>
        store(await api("/api/auth/login", { method: "POST", body: { username, password }, auth: false })),
      register: async (form) => store(await api("/api/auth/register", { method: "POST", body: form, auth: false })),
      logout,
    }),
    [user, sessionExpired, store, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react/only-export-components
export const useAuth = () => useContext(AuthContext);
