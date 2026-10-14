import { useState } from "react";
import { useAuth } from "../../auth.jsx";
import { api } from "../../api.js";
import { formatDay } from "../../format.js";
import { Alert, Field } from "../../components/ui.jsx";

export default function AccountPage({ user, toast }) {
  const { refresh } = useAuth();
  const [form, setForm] = useState({ fullName: user.fullName || "", phone: user.phone || "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      await api("/api/users/me", { method: "PUT", body: form });
      await refresh();
      toast.show("Your details were saved");
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page" style={{ maxWidth: 720 }}>
      <div>
        <h1>My account</h1>
        <p className="muted">
          Signed in as {user.username} ({user.email}). Customer since {formatDay(user.createdAt)}.
        </p>
      </div>
      <form className="card stack" onSubmit={save}>
        <h3>Contact details</h3>
        <Field label="Full name" error={fieldErrors.fullName}>
          <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} maxLength={120} />
        </Field>
        <Field label="Mobile number" error={fieldErrors.phone} hint="Shared with the seller for delivery">
          <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="+971 50 123 4567" />
        </Field>
        <Alert onClose={() => setError("")}>{error}</Alert>
        <div className="row end">
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Saving..." : "Save changes"}
          </button>
        </div>
      </form>
    </div>
  );
}
