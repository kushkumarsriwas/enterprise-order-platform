import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { api, setUnauthorizedHandler, tokenStore } from "./api.js";

// The administrator's session: the token from /api/admin/auth/login (valid for two hours) and who signed in.
// Both live in sessionStorage, so closing the tab ends the session; the console also signs out when the token expires.
const USER_KEY = "nexora_admin_user";
const EXPIRES_KEY = "nexora_admin_expires";

const SessionContext = createContext(null);

function readSession() {
  try {
    const token = tokenStore.get();
    const user = JSON.parse(sessionStorage.getItem(USER_KEY) || "null");
    const expiresAt = Number(sessionStorage.getItem(EXPIRES_KEY) || 0);
    if (!token || !user || (expiresAt && expiresAt <= Date.now())) return null;
    return { user, expiresAt };
  } catch {
    return null;
  }
}

function clearStored() {
  tokenStore.clear();
  try {
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(EXPIRES_KEY);
  } catch {
    /* storage unavailable */
  }
}

export function AdminSessionProvider({ children }) {
  const [session, setSession] = useState(readSession);
  // why the last session ended, shown on the sign-in page ("expired", "signed-out")
  const [ended, setEnded] = useState(null);

  const signOut = useCallback((reason = "signed-out") => {
    clearStored();
    setSession(null);
    setEnded(reason);
  }, []);

  const signIn = useCallback(async (username, password) => {
    const auth = await api("/api/admin/auth/login", { method: "POST", body: { username, password }, auth: false });
    const expiresAt = Date.now() + Number(auth.expiresIn || 7200) * 1000;
    tokenStore.set(auth.accessToken);
    try {
      sessionStorage.setItem(USER_KEY, JSON.stringify(auth.user));
      sessionStorage.setItem(EXPIRES_KEY, String(expiresAt));
    } catch {
      /* the session lasts while the page is open */
    }
    setEnded(null);
    setSession({ user: auth.user, expiresAt });
    return auth.user;
  }, []);

  // any 401 from the API (token expired or revoked) ends the session
  useEffect(() => {
    setUnauthorizedHandler(() => signOut("expired"));
  }, [signOut]);

  // sign out exactly when the token expires, even if the console is left open without traffic
  useEffect(() => {
    if (!session?.expiresAt) return undefined;
    const timer = setTimeout(() => signOut("expired"), Math.max(0, session.expiresAt - Date.now()));
    return () => clearTimeout(timer);
  }, [session, signOut]);

  return <SessionContext.Provider value={{ session, admin: session?.user || null, signIn, signOut, ended }}>{children}</SessionContext.Provider>;
}

export const useSession = () => useContext(SessionContext);
