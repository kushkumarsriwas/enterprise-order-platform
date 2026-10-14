import { useState } from "react";
import { ExternalLink } from "lucide-react";
import { api } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { formatDay } from "../../format.js";
import { Alert, Avatar, Field, PageHeader } from "../../components/ui.jsx";

const CITIES = ["Dubai", "Abu Dhabi", "Sharjah", "Ajman", "Ras Al Khaimah", "Fujairah", "Umm Al Quwain", "Al Ain"];

export default function MerchantStoreProfile({ user, toast }) {
  const { refresh } = useAuth();
  const store = user.store || {};
  const [form, setForm] = useState({ fullName: user.fullName || "", phone: store.phone || user.phone || "", city: store.city || "Dubai", storeDescription: store.description || "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      await api("/api/users/me", { method: "PUT", body: form });
      await refresh();
      toast.show("Store profile saved");
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page" style={{ maxWidth: 900 }}>
      <PageHeader
        title="Store profile"
        subtitle="How customers see your store on the marketplace."
        actions={
          <a className="btn" href={`#/store/${user.id}`}>
            <ExternalLink size={15} /> View your storefront
          </a>
        }
      />
      <div className="card row wrap" style={{ gap: 16 }}>
        <Avatar name={store.storeName || user.username} large />
        <div className="grow">
          <h2>{store.storeName}</h2>
          <p className="muted">
            {store.city}, {store.country} - on Nexora since {formatDay(store.memberSince || user.createdAt)}
          </p>
          <p className="muted small">
            Trade licence: {store.tradeLicenseNumber || "not provided"} - Account {user.username} ({user.email})
          </p>
        </div>
      </div>
      <form className="card stack" onSubmit={save}>
        <h3>Store details</h3>
        <div className="form-grid">
          <Field label="Owner's full name" error={fieldErrors.fullName}>
            <input value={form.fullName} onChange={set("fullName")} maxLength={120} />
          </Field>
          <Field label="Business phone" error={fieldErrors.phone}>
            <input value={form.phone} onChange={set("phone")} placeholder="+971 50 123 4567" />
          </Field>
          <Field label="City" error={fieldErrors.city}>
            <select value={form.city} onChange={set("city")}>
              {CITIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </Field>
          <Field label="About your store" error={fieldErrors.storeDescription} className="span-2" hint="Shown on your store page">
            <textarea rows={4} value={form.storeDescription} onChange={set("storeDescription")} maxLength={500} />
          </Field>
        </div>
        <p className="muted small">The store name cannot be changed because customers and past orders refer to it.</p>
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
