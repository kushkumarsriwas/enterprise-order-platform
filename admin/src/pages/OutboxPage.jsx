import { Bell, Mail } from "lucide-react";
import { api, qs } from "../api.js";
import { ActiveFilters, DebouncedSearch, KeyValues, PageHead, PeriodPicker, Toolbar, useFilters } from "../components/common.jsx";
import { DataTable, Drawer, Empty, ErrorState, Loader, Notice, Pagination, Panel, PillToggle, Select, StatusBadge, Tabs } from "../components/ui.jsx";
import { formatDate, label } from "../format.js";
import { useLoader } from "../hooks.jsx";

const EMAIL_STATUSES = [
  { value: "", label: "All" },
  { value: "QUEUED", label: "Queued" },
  { value: "SENT", label: "Sent" },
  { value: "FAILED", label: "Failed" },
];

const TYPES = [
  "ORDER_PLACED",
  "ORDER_CONFIRMED",
  "ORDER_SHIPPED",
  "ORDER_DELIVERED",
  "ORDER_CANCELLED",
  "PAYMENT_FAILED",
  "NEW_ORDER",
  "NEW_REVIEW",
  "REVIEW_REPLY",
  "REVIEW_REMOVED",
  "NEW_MESSAGE",
  "LOW_STOCK",
  "BUDGET_ALERT",
  "CONVERSATION_REPORTED",
];

function EmailDrawer({ email, onClose }) {
  return (
    <Drawer open title={email.subject} sub={`To ${email.toAddress}`} onClose={onClose}>
      <div className="stack">
        {email.lastError && <Notice tone="bad">{email.lastError}</Notice>}
        <Panel>
          <KeyValues
            items={[
              { label: "Status", value: <StatusBadge status={email.status} /> },
              { label: "Kind", value: label(email.notificationType) },
              { label: "Queued", value: formatDate(email.createdAt) },
              { label: "Sent", value: email.sentAt ? formatDate(email.sentAt) : "-" },
              { label: "Attempts", value: email.attempts },
            ]}
          />
        </Panel>
        <Panel title="Message">
          <div className="small" style={{ whiteSpace: "pre-wrap" }}>
            {email.body}
          </div>
        </Panel>
      </div>
    </Drawer>
  );
}

function Emails({ f, set, clear }) {
  const page = Number(f.page || 0);
  const query = { status: f.status, q: f.q };
  const emails = useLoader(() => api(`/api/admin/emails${qs({ ...query, page, size: 25 })}`), [JSON.stringify(query), page]);
  const data = emails.data;
  const open = data?.content.find((e) => String(e.id) === f.open);
  return (
    <Panel flush>
      <Toolbar>
        <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Address or subject" />
        <PillToggle label="Status" options={EMAIL_STATUSES} value={f.status || ""} onChange={(status) => set({ status })} />
      </Toolbar>
      <ActiveFilters onClear={() => clear(["view"])} chips={[{ key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) }]} />
      {emails.loading && !data ? (
        <Loader />
      ) : emails.error && !data ? (
        <ErrorState message={emails.error} onRetry={emails.reload} />
      ) : (
        <div style={{ opacity: emails.loading ? 0.6 : 1 }}>
          <DataTable
            rows={data.content}
            onRowClick={(e) => set({ open: String(e.id), page: f.page })}
            empty={<Empty icon={Mail} title={f.status === "FAILED" ? "No e-mail failed" : "No e-mail matches"} />}
            columns={[
              { key: "t", header: "Queued", render: (e) => <span className="nowrap">{formatDate(e.createdAt)}</span> },
              { key: "to", header: "To", render: (e) => e.toAddress },
              {
                key: "s",
                header: "Subject",
                render: (e) => (
                  <div>
                    <div className="cell-title">{e.subject}</div>
                    <div className="cell-sub">{label(e.notificationType)}</div>
                  </div>
                ),
              },
              {
                key: "st",
                header: "Status",
                render: (e) => (
                  <div>
                    <StatusBadge status={e.status} />
                    {e.status === "FAILED" && <div className="cell-sub">{e.attempts} attempts</div>}
                  </div>
                ),
              },
            ]}
          />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={25} onPage={(p) => set({ page: p })} />
        </div>
      )}
      {open && <EmailDrawer email={open} onClose={() => set({ open: "", page: f.page })} />}
    </Panel>
  );
}

function Notifications({ f, set, clear }) {
  const page = Number(f.page || 0);
  const query = { type: f.type, q: f.q, from: f.from, to: f.to, userId: f.userId };
  const list = useLoader(() => api(`/api/admin/notifications${qs({ ...query, page, size: 25 })}`), [JSON.stringify(query), page]);
  const data = list.data;
  return (
    <Panel flush>
      <Toolbar>
        <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Words in the title or text" />
        <Select
          className="select-sm"
          value={f.type || ""}
          options={[{ value: "", label: "Any kind" }, ...TYPES.map((t) => ({ value: t, label: label(t) }))]}
          onChange={(e) => set({ type: e.target.value })}
          aria-label="Kind"
        />
        <PeriodPicker from={f.from} to={f.to} allowEmpty onChange={(r) => set(r)} />
      </Toolbar>
      <ActiveFilters
        onClear={() => clear(["view"])}
        chips={[
          { key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) },
          { key: "type", label: label(f.type), value: f.type, onRemove: () => set({ type: "" }) },
          { key: "user", label: `Account #${f.userId}`, value: f.userId, onRemove: () => set({ userId: "" }) },
          { key: "period", label: `${f.from} to ${f.to}`, value: f.from, onRemove: () => set({ from: "", to: "" }) },
        ]}
      />
      {list.loading && !data ? (
        <Loader />
      ) : list.error && !data ? (
        <ErrorState message={list.error} onRetry={list.reload} />
      ) : (
        <div style={{ opacity: list.loading ? 0.6 : 1 }}>
          <DataTable
            rows={data.content}
            empty={<Empty icon={Bell} title="No notification matches" />}
            columns={[
              { key: "t", header: "When", render: (n) => <span className="nowrap">{formatDate(n.createdAt)}</span> },
              {
                key: "w",
                header: "For",
                render: (n) =>
                  n.audience === "ADMINS" ? (
                    <span className="strong">Administrator</span>
                  ) : (
                    <a href={`#/customers/${n.userId}`} onClick={(event) => event.stopPropagation()}>
                      Account #{n.userId}
                    </a>
                  ),
              },
              {
                key: "m",
                header: "Notification",
                render: (n) => (
                  <div style={{ maxWidth: 560 }}>
                    <div className="cell-title">{n.title}</div>
                    <div className="cell-sub clamp-2">{n.message}</div>
                  </div>
                ),
              },
              { key: "k", header: "Kind", render: (n) => <span className="nowrap">{label(n.type)}</span> },
              { key: "r", header: "Read", render: (n) => (n.read ? <span className="muted">Read</span> : <span className="strong">Unread</span>) },
            ]}
          />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={25} onPage={(p) => set({ page: p })} />
        </div>
      )}
    </Panel>
  );
}

/** What Nexora told people: in-app notifications and the e-mail outbox (queued, sent, failed). */
export function OutboxPage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, {});
  return (
    <>
      <PageHead
        title="Notifications and e-mail"
        sub="Everything the platform told customers and stores: in-app notifications, and the e-mails behind them. A failed e-mail is tried again every minute, up to five attempts."
      />
      <Tabs
        tabs={[
          { id: "", label: "E-mail outbox" },
          { id: "notifications", label: "In-app notifications" },
        ]}
        value={f.view || ""}
        onChange={(view) => navigate(view ? "/outbox?view=notifications" : "/outbox", { replace: true })}
      />
      <div style={{ marginTop: 16 }}>
        {f.view === "notifications" ? <Notifications f={f} set={set} clear={clear} /> : <Emails f={f} set={set} clear={clear} />}
      </div>
    </>
  );
}
