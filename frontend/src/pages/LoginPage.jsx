import { useState } from "react";
import { Boxes, LogIn, UserPlus } from "lucide-react";
import { useAuth } from "../auth.jsx";
import { Alert, Field } from "../components/ui.jsx";

export default function LoginPage() {
  const { login, register, sessionExpired } = useAuth();
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ username: "", email: "", password: "", fullName: "" });
  const [error, setError] = useState(sessionExpired ? "Your session has expired. Please log in again." : "");
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState(false);

  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      if (mode === "login") await login(form.username, form.password);
      else await register(form);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
    } finally {
      setBusy(false);
    }
  };

  const demo = (username, password) => setForm({ ...form, username, password });

  return (
    <div className="auth-page">
      <section className="auth-hero">
        <div className="brand brand-large">
          <Boxes size={30} /> Nexora Commerce
        </div>
        <h1>Enterprise order &amp; inventory platform</h1>
        <p>
          Spring Boot microservices behind an API gateway: JWT (RS256) security, atomic stock reservations,
          saga-orchestrated payments, Redis caching, PostgreSQL, MySQL and Oracle.
        </p>
        <ul className="hero-points">
          <li>Real-time stock with no overselling</li>
          <li>Card, UPI and cash-on-delivery payments</li>
          <li>Order lifecycle with full audit trail</li>
        </ul>
      </section>

      <section className="auth-card">
        <div className="tabs">
          <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>
            Log in
          </button>
          <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>
            Create account
          </button>
        </div>
        <form onSubmit={submit} className="stack">
          <Alert onClose={() => setError("")}>{error}</Alert>
          <Field label={mode === "login" ? "Username or email" : "Username"} error={fieldErrors.username}>
            <input value={form.username} onChange={set("username")} autoComplete="username" required />
          </Field>
          {mode === "register" && (
            <>
              <Field label="Email" error={fieldErrors.email}>
                <input type="email" value={form.email} onChange={set("email")} autoComplete="email" required />
              </Field>
              <Field label="Full name" error={fieldErrors.fullName}>
                <input value={form.fullName} onChange={set("fullName")} autoComplete="name" />
              </Field>
            </>
          )}
          <Field
            label="Password"
            error={fieldErrors.password}
            hint={mode === "register" ? "At least 8 characters with a letter and a digit" : null}
          >
            <input
              type="password"
              value={form.password}
              onChange={set("password")}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              required
            />
          </Field>
          <button className="btn btn-primary btn-block" disabled={busy}>
            {mode === "login" ? <LogIn size={16} /> : <UserPlus size={16} />}
            {busy ? "Please wait..." : mode === "login" ? "Log in" : "Create account"}
          </button>
        </form>
        {mode === "login" && (
          <div className="demo-accounts">
            <span>Demo accounts:</span>
            <button className="link" onClick={() => demo("customer", "Customer@12345")}>
              customer
            </button>
            <button className="link" onClick={() => demo("admin", "Admin@12345")}>
              admin
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
