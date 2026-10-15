import { useState } from "react";
import { CircleAlert, KeyRound, MapPin, UserRound } from "lucide-react";
import { api } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { AddressForm, addressBody, emptyAddress } from "../../components/commerce.jsx";
import { Button, Field, Input, Notice, Panel } from "../../components/ui.jsx";
import { formatDay } from "../../format.js";
import { useTitle, useToast } from "../../hooks.jsx";

const PHONE = /^$|^[+0-9 ()-]{7,30}$/;

/** Turns a 400 response with field errors into { field: message } (keys like "address.phone" lose their prefix). */
export const fieldErrorsOf = (error) => Object.fromEntries(Object.entries(error?.fieldErrors || {}).map(([key, message]) => [key.split(".").pop(), message]));

/** Password change, used on the customer account page and in Seller Central settings. */
export function PasswordPanel() {
  const toast = useToast();
  const [form, setForm] = useState({ currentPassword: "", newPassword: "", confirm: "" });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const save = async (event) => {
    event.preventDefault();
    const found = {};
    if (!form.currentPassword) found.currentPassword = "Enter your current password";
    if (form.newPassword.length < 8 || !/[A-Za-z]/.test(form.newPassword) || !/\d/.test(form.newPassword)) {
      found.newPassword = "Use at least 8 characters with a letter and a digit";
    }
    if (form.confirm !== form.newPassword) found.confirm = "The two passwords are not the same";
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      await api("/api/users/me/password", { method: "PUT", body: { currentPassword: form.currentPassword, newPassword: form.newPassword } });
      toast("Password changed");
      setForm({ currentPassword: "", newPassword: "", confirm: "" });
    } catch (e) {
      setErrors({ ...fieldErrorsOf(e), form: e.fieldErrors ? null : e.message });
    } finally {
      setBusy(false);
    }
  };
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  return (
    <Panel title="Password" sub="You stay signed in on this device after changing it.">
      <form className="stack" onSubmit={save}>
        <div className="form-grid">
          <Field label="Current password" className="span-2" error={errors.currentPassword}>
            <Input type="password" value={form.currentPassword} onChange={set("currentPassword")} autoComplete="current-password" />
          </Field>
          <Field label="New password" error={errors.newPassword} hint="At least 8 characters with a letter and a digit">
            <Input type="password" value={form.newPassword} onChange={set("newPassword")} autoComplete="new-password" />
          </Field>
          <Field label="Repeat the new password" error={errors.confirm}>
            <Input type="password" value={form.confirm} onChange={set("confirm")} autoComplete="new-password" />
          </Field>
        </div>
        {errors.form && (
          <Notice tone="bad" icon={CircleAlert}>
            {errors.form}
          </Notice>
        )}
        <div>
          <Button type="submit" icon={KeyRound} loading={busy}>
            Change password
          </Button>
        </div>
      </form>
    </Panel>
  );
}

export function ProfilePage() {
  useTitle("Account details");
  const { user, refresh } = useAuth();
  const toast = useToast();
  const [contact, setContact] = useState({ fullName: user.fullName || "", phone: user.phone || "" });
  const [contactErrors, setContactErrors] = useState({});
  const [savingContact, setSavingContact] = useState(false);
  const [address, setAddress] = useState(user.address ? { ...emptyAddress, ...user.address } : { ...emptyAddress, recipient: user.fullName || "" });
  const [addressErrors, setAddressErrors] = useState({});
  const [savingAddress, setSavingAddress] = useState(false);

  const saveContact = async (event) => {
    event.preventDefault();
    if (!PHONE.test(contact.phone.trim())) {
      setContactErrors({ phone: "Enter a phone number like +971 50 123 4567" });
      return;
    }
    setSavingContact(true);
    setContactErrors({});
    try {
      await api("/api/users/me", { method: "PUT", body: { fullName: contact.fullName, phone: contact.phone } });
      await refresh();
      toast("Contact details saved");
    } catch (e) {
      setContactErrors({ ...fieldErrorsOf(e), form: e.fieldErrors ? null : e.message });
    } finally {
      setSavingContact(false);
    }
  };

  const saveAddress = async (event) => {
    event.preventDefault();
    setSavingAddress(true);
    setAddressErrors({});
    try {
      await api("/api/users/me/address", { method: "PUT", body: addressBody(address) });
      await refresh();
      toast("Delivery address saved");
    } catch (e) {
      setAddressErrors({ ...fieldErrorsOf(e), form: e.fieldErrors ? null : e.message });
    } finally {
      setSavingAddress(false);
    }
  };

  return (
    <div className="stack">
      <div className="page-title">
        <div>
          <h1>Account details</h1>
          <p>
            Signed in as {user.username} ({user.email}). Customer since {formatDay(user.createdAt)}.
          </p>
        </div>
      </div>

      <Panel title="Contact details" sub="Stores see your name and number on orders so the courier can reach you.">
        <form className="stack" onSubmit={saveContact}>
          <div className="form-grid">
            <Field label="Full name" error={contactErrors.fullName}>
              <Input value={contact.fullName} onChange={(e) => setContact({ ...contact, fullName: e.target.value })} autoComplete="name" maxLength={120} />
            </Field>
            <Field label="Mobile number" error={contactErrors.phone}>
              <Input
                value={contact.phone}
                onChange={(e) => setContact({ ...contact, phone: e.target.value })}
                autoComplete="tel"
                placeholder="+971 50 123 4567"
              />
            </Field>
            <Field label="Email" hint="Your sign-in email cannot be changed here">
              <Input value={user.email} disabled />
            </Field>
            <Field label="Username">
              <Input value={user.username} disabled />
            </Field>
          </div>
          {contactErrors.form && (
            <Notice tone="bad" icon={CircleAlert}>
              {contactErrors.form}
            </Notice>
          )}
          <div>
            <Button type="submit" variant="primary" icon={UserRound} loading={savingContact}>
              Save contact details
            </Button>
          </div>
        </form>
      </Panel>

      <Panel title="Default delivery address" sub="Filled in for you at checkout. You can still change it for each order.">
        <form className="stack" onSubmit={saveAddress}>
          <AddressForm value={address} onChange={setAddress} errors={addressErrors} />
          {addressErrors.form && (
            <Notice tone="bad" icon={CircleAlert}>
              {addressErrors.form}
            </Notice>
          )}
          <div>
            <Button type="submit" icon={MapPin} loading={savingAddress}>
              Save address
            </Button>
          </div>
        </form>
      </Panel>

      <PasswordPanel />
    </div>
  );
}
