import { useState } from "react";
import { BellRing, CircleAlert, Palmtree, Save, Store } from "lucide-react";
import { api } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { Button, Field, Input, Notice, Panel, Select, Switch, Textarea } from "../../components/ui.jsx";
import { EMIRATES, formatDay } from "../../format.js";
import { useToast } from "../../hooks.jsx";
import { PasswordPanel, fieldErrorsOf } from "../account/ProfilePage.jsx";
import { PageHead } from "./common.jsx";
import { useSeller } from "./SellerLayout.jsx";

function StoreProfile() {
  const { user, refresh } = useAuth();
  const toast = useToast();
  const store = user.store || {};
  const [form, setForm] = useState({
    fullName: user.fullName || "",
    phone: store.phone || user.phone || "",
    storeDescription: store.description || "",
    city: store.city || "Dubai",
    supportEmail: store.supportEmail || "",
    vatNumber: store.vatNumber || "",
  });
  const [errors, setErrors] = useState({});
  const [problem, setProblem] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const save = async (event) => {
    event.preventDefault();
    const found = {};
    if (!/^[+0-9 ()-]{7,30}$/.test(form.phone.trim())) found.phone = "Enter the store's phone number";
    if (form.supportEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.supportEmail.trim())) found.supportEmail = "Enter a valid email address";
    if (form.vatNumber.trim() && !/^\d{15}$/.test(form.vatNumber.trim())) found.vatNumber = "The TRN has exactly 15 digits";
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    setProblem(null);
    try {
      await api("/api/users/me", {
        method: "PUT",
        body: {
          fullName: form.fullName.trim() || null,
          phone: form.phone.trim(),
          storeDescription: form.storeDescription.trim() || null,
          city: form.city,
          supportEmail: form.supportEmail.trim() || null,
          vatNumber: form.vatNumber.trim() || null,
        },
      });
      await refresh();
      toast("Store profile saved");
    } catch (e) {
      setErrors(fieldErrorsOf(e));
      setProblem(e.fieldErrors ? "Some fields need your attention." : e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Panel title="Store profile" sub="Shown on your store page, on every product and on your tax invoices.">
      <form className="stack" onSubmit={save}>
        <div className="form-grid">
          <Field label="Store name" hint="Contact Nexora support to rename your store">
            <Input value={store.storeName || ""} disabled />
          </Field>
          <Field label="Ships from">
            <Select value={form.city} onChange={set("city")} options={EMIRATES.map((e) => ({ value: e, label: e }))} />
          </Field>
          <Field label="About the store" className="span-2" hint="A sentence or two about what you sell">
            <Textarea value={form.storeDescription} onChange={set("storeDescription")} maxLength={500} rows={3} />
          </Field>
          <Field label="Business phone" error={errors.phone}>
            <Input value={form.phone} onChange={set("phone")} autoComplete="tel" />
          </Field>
          <Field label="Customer support email (optional)" error={errors.supportEmail}>
            <Input type="email" value={form.supportEmail} onChange={set("supportEmail")} maxLength={120} />
          </Field>
          <Field label="VAT TRN (optional)" error={errors.vatNumber} hint="15 digits; printed on your tax invoices">
            <Input value={form.vatNumber} onChange={set("vatNumber")} inputMode="numeric" maxLength={15} />
          </Field>
          <Field label="Trade licence">
            <Input value={store.tradeLicenseNumber || "Not provided"} disabled />
          </Field>
          <Field label="Owner's name" error={errors.fullName}>
            <Input value={form.fullName} onChange={set("fullName")} autoComplete="name" maxLength={120} />
          </Field>
          <Field label="Sign-in">
            <Input value={`${user.username} (${user.email})`} disabled />
          </Field>
        </div>
        {problem && (
          <Notice tone="bad" icon={CircleAlert}>
            {problem}
          </Notice>
        )}
        <div className="row">
          <Button type="submit" variant="primary" icon={Save} loading={busy}>
            Save store profile
          </Button>
          <a className="btn btn-ghost" href={`#/store/${user.id}`}>
            <Store /> View my store
          </a>
        </div>
      </form>
    </Panel>
  );
}

function StoreStatus() {
  const toast = useToast();
  const { settings, setSettings } = useSeller();
  const [message, setMessage] = useState(settings?.vacationMessage || "");
  const [busy, setBusy] = useState(false);
  if (!settings) return null;
  const save = async (changes, done) => {
    setBusy(true);
    try {
      const updated = await api("/api/seller/store/settings", {
        method: "PUT",
        body: { vacationMode: settings.vacationMode, vacationMessage: message.trim() || null, lowStockAlerts: settings.lowStockAlerts, ...changes },
      });
      setSettings(updated);
      toast(done);
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Panel title="Store status">
      <div className="stack">
        {settings.suspended && (
          <Notice tone="bad" icon={CircleAlert}>
            Nexora support has suspended this store, so your products are hidden. Contact support to restore it.
          </Notice>
        )}
        <Switch
          checked={settings.vacationMode}
          disabled={busy}
          onChange={(on) =>
            save({ vacationMode: on }, on ? "Vacation mode is on: your listings are hidden" : "Vacation mode is off: your listings are visible")
          }
        >
          <span>
            <span className="strong row" style={{ gap: 6 }}>
              <Palmtree size={16} /> Vacation mode
            </span>
            <span className="tiny muted">Hides every listing while you are away. Orders already placed are not affected.</span>
          </span>
        </Switch>
        <Field
          label="Message for customers (optional)"
          hint={settings.vacationMode ? "Shown on your store page while you are away" : "Write it before you switch vacation mode on"}
        >
          <div className="row">
            <Input
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              maxLength={255}
              placeholder="Back on 20 October. Thank you for your patience!"
            />
            {settings.vacationMode && (
              <Button size="sm" disabled={busy || message === (settings.vacationMessage || "")} onClick={() => save({}, "Message saved")}>
                Save
              </Button>
            )}
          </div>
        </Field>
        <hr />
        <Switch
          checked={settings.lowStockAlerts}
          disabled={busy}
          onChange={(on) => save({ lowStockAlerts: on }, on ? "Low-stock alerts on" : "Low-stock alerts off")}
        >
          <span>
            <span className="strong row" style={{ gap: 6 }}>
              <BellRing size={16} /> Low-stock alerts
            </span>
            <span className="tiny muted">A notification when a product's available stock reaches its reorder point.</span>
          </span>
        </Switch>
      </div>
    </Panel>
  );
}

export function SettingsPage() {
  const { user } = useAuth();
  return (
    <>
      <PageHead title="Settings" sub={`Store account since ${formatDay(user.store?.memberSince || user.createdAt)}.`} />
      <div className="sc-grid-main">
        <StoreProfile />
        <div className="stack">
          <StoreStatus />
          <PasswordPanel />
        </div>
      </div>
    </>
  );
}
