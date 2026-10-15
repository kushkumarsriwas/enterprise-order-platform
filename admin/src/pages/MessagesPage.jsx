import { useState } from "react";
import { CheckCheck, Flag, MessageSquare, MessagesSquare, Search } from "lucide-react";
import { api, qs } from "../api.js";
import { DebouncedSearch, PageHead, Toolbar, storeOptions, useFilters, useStores } from "../components/common.jsx";
import { Button, Checkbox, DataTable, Empty, ErrorState, Loader, Notice, Pagination, Panel, Select, Stat, StatusBadge, Tabs } from "../components/ui.jsx";
import { formatDate, formatNumber, timeAgo } from "../format.js";
import { useLoader, useToast } from "../hooks.jsx";

/** One conversation between a customer and a store, as both of them see it, with the report (if any). */
function Transcript({ id, onResolved }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const conversation = useLoader(() => api(`/api/admin/conversations/${id}`), [id]);
  const messages = useLoader(() => api(`/api/admin/conversations/${id}/messages${qs({ size: 100 })}`), [id]);
  const c = conversation.data;

  const resolve = async () => {
    setBusy(true);
    try {
      await api(`/api/admin/conversations/${id}/resolve-report`, { method: "PUT" });
      toast("Report resolved; the conversation is no longer flagged");
      conversation.reload();
      onResolved();
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(false);
    }
  };

  if (conversation.loading && !c) return <Loader />;
  if (conversation.error && !c) return <ErrorState message={conversation.error} onRetry={conversation.reload} />;
  return (
    <Panel
      title={
        <span>
          <a href={`#/customers/${c.customerId}`}>{c.customerName}</a> <span className="muted">and</span> <a href={`#/stores/${c.sellerId}`}>{c.sellerName}</a>
        </span>
      }
      sub={`Started ${formatDate(c.createdAt)}; last message ${timeAgo(c.lastMessageAt)}`}
      flush
    >
      {c.reported && (
        <div style={{ padding: "14px 18px 0" }}>
          <Notice tone="warn" icon={Flag}>
            <div className="stack-sm" style={{ gap: 8 }}>
              <span>
                <strong>Reported by {c.reportedBy || "a participant"}</strong>
                {c.reportedAt ? ` on ${formatDate(c.reportedAt)}` : ""}: {c.reportReason || "no reason given"}
              </span>
              <span>
                <Button size="sm" variant="primary" icon={CheckCheck} loading={busy} onClick={resolve}>
                  Mark the report as resolved
                </Button>
              </span>
            </div>
          </Notice>
        </div>
      )}
      {messages.loading && !messages.data ? (
        <Loader />
      ) : messages.error && !messages.data ? (
        <ErrorState message={messages.error} onRetry={messages.reload} />
      ) : (
        <div className="transcript">
          {messages.data.content.map((m) => (
            <div key={m.id} className={`bubble ${m.senderRole === "SELLER" ? "seller" : ""}`}>
              <div className="who">
                <b>{m.senderName}</b>
                <span>{m.senderRole === "SELLER" ? "store" : "customer"}</span>
                <span>{formatDate(m.createdAt)}</span>
              </div>
              <div style={{ whiteSpace: "pre-wrap" }}>{m.body}</div>
              {(m.productName || m.orderNumber) && <div className="about">About {m.orderNumber ? `order ${m.orderNumber}` : m.productName}</div>}
            </div>
          ))}
          {messages.data.totalPages > 1 && <p className="small muted">Showing the first {formatNumber(messages.data.content.length)} messages.</p>}
        </div>
      )}
    </Panel>
  );
}

function Conversations({ f, set }) {
  const stores = useStores();
  const page = Number(f.page || 0);
  const query = { reported: f.reported === "true" ? true : undefined, sellerId: f.sellerId, q: f.q };
  const list = useLoader(() => api(`/api/admin/conversations${qs({ ...query, page, size: 15 })}`), [JSON.stringify(query), page]);
  const data = list.data;
  return (
    <div className="grid-side">
      <Panel flush>
        <div className="toolbar-stack">
          <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Customer or store" />
          <Select value={f.sellerId || ""} options={storeOptions(stores.data)} onChange={(e) => set({ sellerId: e.target.value })} aria-label="Store" />
          <Checkbox checked={f.reported === "true"} onChange={(checked) => set({ reported: checked ? "true" : "" })}>
            Only reported conversations
          </Checkbox>
        </div>
        {list.loading && !data ? (
          <Loader />
        ) : list.error && !data ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : data.content.length === 0 ? (
          <Empty icon={MessagesSquare} title={f.reported === "true" ? "No reported conversation" : "No conversation matches"} />
        ) : (
          <div className="convo-list" style={{ opacity: list.loading ? 0.6 : 1 }}>
            {data.content.map((c) => (
              <button
                key={c.id}
                type="button"
                className={`convo ${String(c.id) === f.open ? "active" : ""}`}
                onClick={() => set({ open: String(c.id), page: f.page })}
              >
                <span className="convo-top">
                  {c.customerName} <span className="muted">and</span> {c.sellerName}
                  <span className="when">{timeAgo(c.lastMessageAt)}</span>
                </span>
                <p>{c.lastMessagePreview}</p>
                {c.reported && (
                  <span>
                    <StatusBadge status="REPORTED" />
                  </span>
                )}
              </button>
            ))}
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={15} onPage={(p) => set({ page: p, open: f.open })} />
          </div>
        )}
      </Panel>
      {f.open ? (
        <Transcript key={f.open} id={f.open} onResolved={list.reload} />
      ) : (
        <Panel>
          <Empty icon={MessageSquare} title="Pick a conversation">
            Read what a customer and a store wrote to each other. Reported conversations are flagged; resolve the report once you have dealt with it.
          </Empty>
        </Panel>
      )}
    </div>
  );
}

function MessageSearch({ f, set }) {
  const page = Number(f.page || 0);
  const results = useLoader(() => (f.q ? api(`/api/admin/messages${qs({ q: f.q, page, size: 20 })}`) : Promise.resolve(null)), [f.q, page]);
  const data = results.data;
  return (
    <Panel flush>
      <Toolbar>
        <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Words in a message, e.g. a phone number or 'refund'" />
      </Toolbar>
      {!f.q ? (
        <Empty icon={Search} title="Search every message on Nexora">
          Useful when a report mentions something that was said, or to check that stores do not move customers off the platform.
        </Empty>
      ) : results.loading && !data ? (
        <Loader />
      ) : results.error && !data ? (
        <ErrorState message={results.error} onRetry={results.reload} />
      ) : (
        <div style={{ opacity: results.loading ? 0.6 : 1 }}>
          <DataTable
            rows={data.content}
            onRowClick={(m) => set({ view: "", q: "", open: String(m.conversationId) })}
            empty={<Empty title={`No message contains "${f.q}"`} />}
            columns={[
              { key: "t", header: "When", render: (m) => <span className="nowrap">{formatDate(m.createdAt)}</span> },
              {
                key: "w",
                header: "From",
                render: (m) => (
                  <div>
                    <div className="cell-title">{m.senderName}</div>
                    <div className="cell-sub">{m.senderRole === "SELLER" ? "store" : "customer"}</div>
                  </div>
                ),
              },
              { key: "c", header: "Conversation", render: (m) => <span className="nowrap">{`${m.customerName} and ${m.sellerName}`}</span> },
              {
                key: "b",
                header: "Message",
                render: (m) => (
                  <span className="clamp-2" style={{ maxWidth: 520 }}>
                    {m.body}
                  </span>
                ),
              },
            ]}
          />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
        </div>
      )}
    </Panel>
  );
}

/** Conversations between customers and stores, the reports on them, and a search over every message. */
export function MessagesPage({ route, navigate }) {
  const [f, set] = useFilters(route, navigate, {});
  const stats = useLoader(() => api("/api/admin/conversations/stats"), []);
  const s = stats.data;
  return (
    <>
      <PageHead
        title="Messages"
        sub="What customers and stores write to each other on Nexora. A participant can report a conversation; it then waits here for you."
      />
      <div className="cc-stack">
        {s && (
          <div className="stats">
            <Stat label="Conversations" value={formatNumber(s.conversations)} />
            <Stat label="Reported, open" value={formatNumber(s.reportedConversations)} attention={s.reportedConversations > 0} />
            <Stat label="Messages" value={formatNumber(s.messages)} />
            <Stat label="Messages, last 24 h" value={formatNumber(s.messagesLast24Hours)} />
          </div>
        )}
        <Tabs
          tabs={[
            { id: "", label: "Conversations" },
            { id: "search", label: "Search messages" },
          ]}
          value={f.view || ""}
          onChange={(view) => navigate(view ? "/messages?view=search" : "/messages", { replace: true })}
        />
        {f.view === "search" ? (
          <MessageSearch f={f} set={set} />
        ) : (
          <Conversations
            f={f}
            set={(changes) => {
              set(changes);
              if ("reported" in changes) stats.reload();
            }}
          />
        )}
      </div>
    </>
  );
}
