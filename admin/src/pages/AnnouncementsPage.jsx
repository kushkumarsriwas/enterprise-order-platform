import { useState } from "react";
import { CircleAlert, Megaphone, Pencil, Plus, Trash2 } from "lucide-react";
import { api } from "../api.js";
import { PageHead } from "../components/common.jsx";
import {
  Button,
  Confirm,
  DataTable,
  Empty,
  ErrorState,
  Field,
  Input,
  Loader,
  Modal,
  Notice,
  Panel,
  PillToggle,
  Select,
  StatusBadge,
  Switch,
  Textarea,
} from "../components/ui.jsx";
import { formatDate } from "../format.js";
import { useLoader, useToast } from "../hooks.jsx";

const AUDIENCES = [
  { value: "EVERYONE", label: "Everyone" },
  { value: "CUSTOMERS", label: "Customers" },
  { value: "SELLERS", label: "Stores" },
];

const LEVELS = [
  { value: "INFO", label: "Information" },
  { value: "SUCCESS", label: "Good news" },
  { value: "WARNING", label: "Warning" },
];

/** Live, scheduled, ended or switched off, from the dates and the on/off switch. */
export function announcementState(a, now = Date.now()) {
  if (!a.active) return "OFF";
  if (a.startsAt && new Date(a.startsAt).getTime() > now) return "SCHEDULED";
  if (a.endsAt && new Date(a.endsAt).getTime() <= now) return "ENDED";
  return "LIVE";
}

// <input type="datetime-local"> works in local time without a zone; the API stores instants.
const toLocalInput = (iso) => {
  if (!iso) return "";
  const date = new Date(iso);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};
const fromLocalInput = (value) => (value ? new Date(value).toISOString() : null);

function AnnouncementDialog({ announcement, onClose, onSaved }) {
  const toast = useToast();
  const editing = !!announcement?.id;
  const [form, setForm] = useState(() => ({
    title: announcement?.title || "",
    message: announcement?.message || "",
    audience: announcement?.audience || "EVERYONE",
    level: announcement?.level || "INFO",
    startsAt: toLocalInput(announcement?.startsAt),
    endsAt: toLocalInput(announcement?.endsAt),
    active: announcement ? announcement.active : true,
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const change = (key) => (event) => setForm({ ...form, [key]: event.target.value });

  const save = async () => {
    if (!form.title.trim() || !form.message.trim()) {
      setError("Give the announcement a title and a message.");
      return;
    }
    if (form.startsAt && form.endsAt && form.endsAt <= form.startsAt) {
      setError("The end has to be after the start.");
      return;
    }
    setBusy(true);
    setError(null);
    const body = {
      ...form,
      title: form.title.trim(),
      message: form.message.trim(),
      startsAt: fromLocalInput(form.startsAt),
      endsAt: fromLocalInput(form.endsAt),
    };
    try {
      await api(editing ? `/api/admin/announcements/${announcement.id}` : "/api/admin/announcements", { method: editing ? "PUT" : "POST", body });
      toast(editing ? "Announcement saved" : "Announcement published");
      onSaved();
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      title={editing ? "Edit announcement" : "New announcement"}
      sub="Shown as a banner at the top of the marketplace (and of Seller Central when stores are included), between the start and end you set."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} onClick={save}>
            {editing ? "Save changes" : form.active ? "Publish" : "Save switched off"}
          </Button>
        </>
      }
    >
      <div className="stack">
        <Field label="Title" hint="Bold, at the start of the banner">
          <Input value={form.title} onChange={change("title")} maxLength={120} autoFocus />
        </Field>
        <Field label="Message">
          <Textarea value={form.message} onChange={change("message")} maxLength={500} rows={3} />
        </Field>
        <div className="form-grid">
          <Field label="Who sees it">
            <PillToggle label="Audience" options={AUDIENCES} value={form.audience} onChange={(audience) => setForm({ ...form, audience })} />
          </Field>
          <Field label="Kind">
            <Select value={form.level} options={LEVELS} onChange={change("level")} />
          </Field>
          <Field label="Starts (optional)" hint="Empty: right away">
            <Input type="datetime-local" value={form.startsAt} onChange={change("startsAt")} />
          </Field>
          <Field label="Ends (optional)" hint="Empty: until you switch it off">
            <Input type="datetime-local" value={form.endsAt} onChange={change("endsAt")} min={form.startsAt || undefined} />
          </Field>
        </div>
        <Switch checked={form.active} onChange={(active) => setForm({ ...form, active })}>
          Switched on
        </Switch>
        <div>
          <div className="field-label" style={{ marginBottom: 6 }}>
            Preview
          </div>
          <div className={`banner-preview ${form.level}`}>
            <strong>{form.title || "Title"}</strong>
            {form.message || "The message appears here."}
          </div>
        </div>
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

export function AnnouncementsPage() {
  const toast = useToast();
  const list = useLoader(() => api("/api/admin/announcements"), []);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);
  const rows = (list.data || []).map((a) => ({ ...a, state: announcementState(a) }));

  const remove = async () => {
    setBusy(true);
    try {
      await api(`/api/admin/announcements/${deleting.id}`, { method: "DELETE" });
      toast("Announcement deleted");
      setDeleting(null);
      list.reload();
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHead
        title="Announcements"
        sub="Banners for everyone on Nexora, only customers or only stores: delivery news, holiday hours, planned maintenance."
        actions={
          <Button variant="primary" icon={Plus} onClick={() => setEditing({})}>
            New announcement
          </Button>
        }
      />
      <Panel flush>
        {list.loading && !list.data ? (
          <Loader />
        ) : list.error && !list.data ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : (
          <DataTable
            rows={rows}
            onRowClick={(a) => setEditing(a)}
            empty={
              <Empty
                icon={Megaphone}
                title="No announcements yet"
                action={
                  <Button variant="primary" icon={Plus} onClick={() => setEditing({})}>
                    New announcement
                  </Button>
                }
              />
            }
            columns={[
              {
                key: "a",
                header: "Announcement",
                render: (a) => (
                  <div style={{ maxWidth: 520 }}>
                    <div className="cell-title">{a.title}</div>
                    <div className="cell-sub clamp-2">{a.message}</div>
                  </div>
                ),
              },
              { key: "w", header: "Who sees it", render: (a) => AUDIENCES.find((x) => x.value === a.audience)?.label || a.audience },
              { key: "k", header: "Kind", render: (a) => LEVELS.find((x) => x.value === a.level)?.label || a.level },
              {
                key: "when",
                header: "Shown",
                render: (a) => (
                  <div className="small">
                    <div>From {a.startsAt ? formatDate(a.startsAt) : formatDate(a.createdAt)}</div>
                    <div className="cell-sub">{a.endsAt ? `until ${formatDate(a.endsAt)}` : "until switched off"}</div>
                  </div>
                ),
              },
              { key: "s", header: "Status", render: (a) => <StatusBadge status={a.state} /> },
              {
                key: "x",
                header: "",
                align: "right",
                render: (a) => (
                  <span className="row" style={{ gap: 4, justifyContent: "flex-end" }} onClick={(event) => event.stopPropagation()}>
                    <Button size="sm" variant="ghost" icon={Pencil} onClick={() => setEditing(a)}>
                      Edit
                    </Button>
                    <Button size="sm" variant="ghost" icon={Trash2} onClick={() => setDeleting(a)} aria-label={`Delete ${a.title}`} />
                  </span>
                ),
              },
            ]}
          />
        )}
      </Panel>
      {editing && (
        <AnnouncementDialog
          announcement={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            list.reload();
          }}
        />
      )}
      <Confirm
        open={!!deleting}
        title={`Delete "${deleting?.title}"?`}
        confirmLabel="Delete"
        danger
        busy={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      >
        The banner disappears for everyone right away. To show it again later, switch it off instead of deleting it.
      </Confirm>
    </>
  );
}
