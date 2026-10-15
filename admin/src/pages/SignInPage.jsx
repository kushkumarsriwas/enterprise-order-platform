import { useState } from "react";
import { CircleAlert, Clock, Eye, EyeOff, KeyRound, ShieldCheck } from "lucide-react";
import { useSession } from "../auth.jsx";
import { Mark } from "../components/brand.jsx";
import { Button, Field, Input, Notice } from "../components/ui.jsx";
import { useTheme, useTitle } from "../hooks.jsx";

const ENDED = {
  expired: "Your session ended. Sign in again to continue where you left off.",
  "signed-out": "You signed out of the Control Center.",
};

/** The administrator's own sign-in. The marketplace sign-in never accepts the administrator account. */
export function SignInPage() {
  useTitle("Sign in");
  useTheme();
  const { signIn, ended } = useSession();
  const [form, setForm] = useState({ username: "", password: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const submit = async (event) => {
    event.preventDefault();
    if (!form.username.trim() || !form.password) {
      setError("Enter the administrator username (or e-mail) and password.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await signIn(form.username.trim(), form.password);
    } catch (e) {
      setError(e.status === 401 ? "That username and password do not match the administrator account." : e.message);
      setBusy(false);
    }
  };

  return (
    <div className="cc-signin">
      <aside className="cc-signin-side">
        <div className="console-logo">
          <Mark size={30} />
          <span className="console-logo-word">nexora</span>
          <span className="console-logo-area">Control Center</span>
        </div>
        <div>
          <h1>Run the marketplace from one desk</h1>
          <p>Stores, customers, orders, payments and stock across every Nexora service, with the tools to act on them.</p>
          <ul>
            <li>
              <ShieldCheck /> Only the platform administrator can sign in here; stores and customers use the marketplace.
            </li>
            <li>
              <KeyRound /> Every attempt, successful or not, is written to the security log with its IP address.
            </li>
            <li>
              <Clock /> Sessions last two hours and end when you close this tab.
            </li>
          </ul>
        </div>
      </aside>
      <main className="cc-signin-main">
        <form className="cc-signin-card" onSubmit={submit} noValidate>
          <h2>Administrator sign-in</h2>
          <p>Nexora Control Center</p>
          <div className="stack">
            {ended && !error && <Notice tone={ended === "expired" ? "warn" : "info"}>{ENDED[ended]}</Notice>}
            <Field label="Username or e-mail">
              <Input
                value={form.username}
                onChange={(event) => setForm({ ...form, username: event.target.value })}
                autoComplete="username"
                autoFocus
                spellCheck={false}
              />
            </Field>
            <Field label="Password">
              <div className="input-affix input-affix-end">
                <Input
                  type={showPassword ? "text" : "password"}
                  value={form.password}
                  onChange={(event) => setForm({ ...form, password: event.target.value })}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="affix-btn"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  onClick={() => setShowPassword((v) => !v)}
                >
                  {showPassword ? <EyeOff /> : <Eye />}
                </button>
              </div>
            </Field>
            {error && (
              <Notice tone="bad" icon={CircleAlert}>
                {error}
              </Notice>
            )}
            <Button type="submit" variant="primary" block loading={busy}>
              Sign in
            </Button>
          </div>
        </form>
      </main>
    </div>
  );
}
