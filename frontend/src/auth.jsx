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

function writeUser(user) {
  try {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    else localStorage.removeItem(USER_KEY);
  } catch {
    /* storage unavailable */
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => (tokenStore.get() ? readUser() : null));
  const [sessionExpired, setSessionExpired] = useState(false);

  const store = useCallback((response) => {
    tokenStore.set(response.accessToken);
    writeUser(response.user);
    setSessionExpired(false);
    setUser(response.user);
    return response.user;
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    writeUser(null);
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
      role: user?.role,
      isAdmin: user?.role === "ADMIN",
      isMerchant: user?.role === "MERCHANT",
      isCustomer: user?.role === "CUSTOMER",
      sessionExpired,
      /** accountType CUSTOMER or MERCHANT: each portal only accepts its own kind of account. */
      login: async (username, password, accountType) =>
        store(await api("/api/auth/login", { method: "POST", body: { username, password, accountType }, auth: false })),
      register: async (form) => store(await api("/api/auth/register", { method: "POST", body: form, auth: false })),
      refresh: async () => {
        const me = await api("/api/users/me");
        writeUser(me);
        setUser(me);
        return me;
      },
      logout,
    }),
    [user, sessionExpired, store, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react/only-export-components
export const useAuth = () => useContext(AuthContext);
