import { useState } from "react";
import { Activity, CircleAlert, Clock, Eye, EyeOff, KeyRound, LockKeyhole, ShieldCheck, ShoppingBag, UserRound } from "lucide-react";
import { useSession } from "../auth.jsx";
import { Aurora, Mark } from "../components/brand.jsx";
import { Button, Field, Input, Notice } from "../components/ui.jsx";
import { useTheme, useTilt, useTitle } from "../hooks.jsx";

const ENDED = {
  expired: "Your session ended. Sign in again to continue where you left off.",
  "signed-out": "You signed out of the Control Center.",
};

/** Floating previews of the console (an illustration, hidden from screen readers). */
function Showcase() {
  return (
    <div className="cc-showcase" aria-hidden="true">
      <div className="show-card show-a">
        <span className="show-title">
          <Activity /> Service health
        </span>
        <b>6 of 6 services up</b>
        <span className="show-dots">
          <i />
          <i />
          <i />
          <i />
          <i />
          <i />
        </span>
        <span className="show-note">Gateway, sign-in, catalogue, orders, payments, notifications</span>
      </div>
      <div className="show-card show-b">
        <span className="show-title">
          <ShoppingBag /> Orders today
        </span>
        <b className="show-figure">42</b>
        <span className="show-bars">
          <i style={{ height: "40%" }} />
          <i style={{ height: "65%" }} />
          <i style={{ height: "50%" }} />
          <i style={{ height: "85%" }} />
          <i style={{ height: "70%" }} />
          <i style={{ height: "100%" }} />
        </span>
      </div>
      <div className="show-card show-c">
        <span className="show-title">
          <ShieldCheck /> Security log
        </span>
        <span className="show-note">No failed sign-ins in the last hour</span>
      </div>
    </div>
  );
}

/** The administrator's own sign-in. The marketplace sign-in never accepts the administrator account. */
export function SignInPage() {
  useTitle("Sign in");
  useTheme();
  const tilt = useTilt();
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
      <aside className="cc-signin-side" ref={tilt}>
        <Aurora />
        <div className="console-logo">
          <Mark size={30} />
          <span className="console-logo-word">nexora</span>
          <span className="console-logo-area">Control Center</span>
        </div>
        <div className="cc-pitch">
          <h1>Run the marketplace from one desk</h1>
          <p>Stores, customers, orders, payments and stock across every Nexora service, with the tools to act on them.</p>
          <ul>
            <li>
              <span className="tick">
                <ShieldCheck />
              </span>
              Only the platform administrator can sign in here; stores and customers use the marketplace.
            </li>
            <li>
              <span className="tick">
                <KeyRound />
              </span>
              Every attempt, successful or not, is written to the security log with its IP address.
            </li>
            <li>
              <span className="tick">
                <Clock />
              </span>
              Sessions last two hours and end when you close this tab.
            </li>
          </ul>
        </div>
        <Showcase />
      </aside>
      <main className="cc-signin-main">
        <form className="cc-signin-card" onSubmit={submit} noValidate>
          <span className="cc-signin-mark">
            <Mark size={40} />
          </span>
          <h2>Administrator sign-in</h2>
          <p>Nexora Control Center</p>
          <div className="stack">
            {ended && !error && <Notice tone={ended === "expired" ? "warn" : "info"}>{ENDED[ended]}</Notice>}
            <Field label="Username or e-mail">
              <span className="input-affix">
                <UserRound />
                <Input
                  value={form.username}
                  onChange={(event) => setForm({ ...form, username: event.target.value })}
                  autoComplete="username"
                  autoFocus
                  spellCheck={false}
                />
              </span>
            </Field>
            <Field label="Password">
              <span className="input-affix input-affix-end">
                <LockKeyhole />
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
              </span>
            </Field>
            {error && (
              <Notice tone="bad" icon={CircleAlert}>
                {error}
              </Notice>
            )}
            <Button type="submit" variant="primary" size="lg" block loading={busy} className="btn-shine">
              Sign in
            </Button>
          </div>
        </form>
      </main>
    </div>
  );
}
