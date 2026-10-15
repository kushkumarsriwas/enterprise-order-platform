import { useState } from "react";
import { CircleAlert } from "lucide-react";
import { api } from "../api.js";
import { formatDate, label } from "../format.js";
import { useToast } from "../hooks.jsx";
import { Badge, Button, DataTable, Empty, Field, Modal, Notice, Panel, StatusBadge, Textarea } from "./ui.jsx";

/** Suspend or reactivate a store or customer account, always with a reason that is kept on the account. */
export function AccountStatusDialog({ user, open, onClose, onDone }) {
  const toast = useToast();
  const suspending = user.active;
  const store = user.role === "SELLER";
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const name = store ? user.store?.storeName || user.username : user.fullName || user.username;

  const submit = async () => {
    if (suspending && reason.trim().length < 5) {
      setError("Write why the account is suspended (at least a few words). It is kept with the account.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await api(`/api/admin/users/${user.id}/status`, { method: "PUT", body: { active: !suspending, reason: reason.trim() || null } });
      toast(suspending ? `${name} is suspended` : `${name} is active again`);
      onDone(updated);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={suspending ? `Suspend ${name}?` : `Reactivate ${name}?`}
      sub={
        suspending
          ? store
            ? "The store disappears from the marketplace (its products are hidden) and it can no longer sign in to Seller Central. Orders already placed stay as they are."
            : "The customer can no longer sign in to the marketplace. Orders already placed stay as they are."
          : store
            ? "The store can sign in again and its listed products come back to the marketplace."
            : "The customer can sign in and shop again."
      }
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Keep as it is
          </Button>
          <Button variant={suspending ? "danger" : "primary"} loading={busy} onClick={submit}>
            {suspending ? "Suspend the account" : "Reactivate the account"}
          </Button>
        </>
      }
    >
      <div className="stack">
        <Field label={suspending ? "Reason" : "Note (optional)"} hint="Kept with the account and shown in the Control Center">
          <Textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} maxLength={250} autoFocus />
        </Field>
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

export function AccountBadge({ user }) {
  return user.active ? <StatusBadge status="ACTIVE" /> : <StatusBadge status="SUSPENDED" />;
}

/** Why and when an account was suspended. */
export function SuspensionNotice({ user }) {
  if (user.active) return null;
  return (
    <Notice tone="bad" icon={CircleAlert}>
      <strong>Suspended{user.statusChangedAt ? ` on ${formatDate(user.statusChangedAt)}` : ""}.</strong> {user.suspensionReason || "No reason recorded."}
    </Notice>
  );
}

const PORTALS = { CUSTOMER: "Marketplace", SELLER: "Seller Central", ADMIN: "Control Center" };
export const portalName = (portal) => PORTALS[portal] || label(portal);

/** "Chrome on Windows" from a user agent, enough to tell devices apart in the security log. */
export function deviceOf(agent) {
  if (!agent) return "-";
  const browser = /Edg\//.test(agent)
    ? "Edge"
    : /OPR\//.test(agent)
      ? "Opera"
      : /Chrome\//.test(agent)
        ? "Chrome"
        : /Firefox\//.test(agent)
          ? "Firefox"
          : /Safari\//.test(agent)
            ? "Safari"
            : /curl|node|python|java|okhttp|postman/i.test(agent)
              ? "Script or API client"
              : "Browser";
  const system = /Windows/.test(agent)
    ? "Windows"
    : /Android/.test(agent)
      ? "Android"
      : /iPhone|iPad/.test(agent)
        ? "iOS"
        : /Mac OS X/.test(agent)
          ? "macOS"
          : /Linux/.test(agent)
            ? "Linux"
            : null;
  return system ? `${browser} on ${system}` : browser;
}

export function loginColumns({ user = true } = {}) {
  return [
    { key: "time", header: "Time", render: (e) => <span className="nowrap">{formatDate(e.createdAt)}</span> },
    user && { key: "user", header: "Username", render: (e) => <span className="strong">{e.username}</span> },
    { key: "portal", header: "Signed in to", render: (e) => portalName(e.portal) },
    {
      key: "result",
      header: "Result",
      render: (e) => (
        <div>
          <StatusBadge status={e.success ? "SUCCESS" : "FAILED"} />
          {e.failureReason && <div className="cell-sub">{e.failureReason}</div>}
        </div>
      ),
    },
    { key: "ip", header: "IP address", render: (e) => <span className="ref">{e.ipAddress || "-"}</span> },
    { key: "device", header: "Device", render: (e) => <span className="nowrap">{deviceOf(e.userAgent)}</span> },
  ].filter(Boolean);
}

/** The latest sign-in attempts of one account (from the account detail). */
export function RecentLogins({ detail }) {
  return (
    <Panel
      title="Recent sign-ins"
      sub={detail.failedLoginsLast30Days ? `${detail.failedLoginsLast30Days} failed in the last 30 days` : "No failed attempts in the last 30 days"}
      actions={
        <a className="btn btn-ghost btn-sm" href={`#/security?q=${encodeURIComponent(detail.user.username)}`}>
          Security log
        </a>
      }
      flush
    >
      <DataTable rows={detail.recentLogins} columns={loginColumns({ user: false })} empty={<Empty title="No sign-in yet" />} />
    </Panel>
  );
}

export const VatBadge = ({ store }) => (store?.vatNumber ? <Badge tone="teal">VAT registered</Badge> : null);
