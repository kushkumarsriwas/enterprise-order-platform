import { useEffect, useRef, useState } from "react";
import { ArrowLeft, Bell, CircleDollarSign, Flag, MessageSquare, Package, PackageCheck, Send, Star, TriangleAlert, Truck, Wallet } from "lucide-react";
import { api, qs } from "../api.js";
import { formatDate, timeAgo } from "../format.js";
import { useInterval, useLoader, useToast } from "../hooks.jsx";
import { Avatar, Button, Empty, ErrorState, Field, Loader, Modal, Pagination, Textarea } from "./ui.jsx";

/** Customer <-> store conversations. Polls every 8 seconds while open. */
export function Messenger({ initialId, emptyText }) {
  const toast = useToast();
  const [selected, setSelected] = useState(initialId ? Number(initialId) : null);
  const list = useLoader(() => api(`/api/conversations${qs({ size: 50 })}`), []);
  const conversations = list.data?.content || [];
  useInterval(() => list.reload(), 15000);

  if (list.loading && !list.data) return <Loader text="Loading your messages" />;
  if (list.error && !list.data) return <ErrorState message={list.error} onRetry={list.reload} />;
  if (conversations.length === 0) {
    return (
      <div className="panel">
        <Empty icon={MessageSquare} title="No messages yet">
          {emptyText}
        </Empty>
      </div>
    );
  }
  const current = conversations.find((c) => c.id === selected);
  return (
    <div className={`messenger ${current ? "has-thread" : ""}`}>
      <div className="thread-list" aria-label="Conversations">
        {conversations.map((c) => (
          <button key={c.id} type="button" className={`thread-item ${c.id === selected ? "active" : ""}`} onClick={() => setSelected(c.id)}>
            <Avatar name={c.counterpartName} />
            <div className="grow">
              <div className="row-between">
                <span className="t-name ellipsis">{c.counterpartName}</span>
                <span className="tiny muted nowrap">{timeAgo(c.lastMessageAt)}</span>
              </div>
              <div className="row-between">
                <span className="t-preview ellipsis">{c.lastMessagePreview}</span>
                {c.unread > 0 && <span className="unread">{c.unread}</span>}
              </div>
            </div>
          </button>
        ))}
      </div>
      {current ? (
        <Thread
          key={current.id}
          conversation={current}
          onBack={() => setSelected(null)}
          onChange={() => list.reload()}
          onReported={() => toast("Thanks. Nexora support will review this conversation.")}
        />
      ) : (
        <div className="chat">
          <Empty icon={MessageSquare} title="Pick a conversation" />
        </div>
      )}
    </div>
  );
}

function Thread({ conversation, onBack, onChange, onReported }) {
  const toast = useToast();
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [reporting, setReporting] = useState(false);
  const body = useRef(null);
  const messages = useLoader(async () => {
    const page = await api(`/api/conversations/${conversation.id}/messages${qs({ size: 100 })}`);
    if (conversation.unread > 0) {
      await api(`/api/conversations/${conversation.id}/read`, { method: "POST" });
      onChange();
    }
    return [...page.content].reverse();
  }, [conversation.id]);
  useInterval(() => messages.reload(), 8000);

  useEffect(() => {
    if (body.current) body.current.scrollTop = body.current.scrollHeight;
  }, [messages.data?.length]);

  const send = async () => {
    if (!text.trim()) return;
    setBusy(true);
    try {
      await api(`/api/conversations/${conversation.id}/messages`, { method: "POST", body: { message: text.trim() } });
      setText("");
      await messages.reload();
      onChange();
    } catch (error) {
      toast(error.message, "bad");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="chat">
      <div className="chat-head">
        <div className="row">
          <button type="button" className="icon-btn" aria-label="Back to conversations" onClick={onBack}>
            <ArrowLeft />
          </button>
          <Avatar name={conversation.counterpartName} />
          <div>
            <div className="strong">{conversation.counterpartName}</div>
            <div className="tiny muted">Started {formatDate(conversation.createdAt)}</div>
          </div>
        </div>
        <Button size="sm" variant="ghost" icon={Flag} onClick={() => setReporting(true)}>
          Report
        </Button>
      </div>
      <div className="chat-body" ref={body}>
        {messages.loading && !messages.data && <Loader />}
        {(messages.data || []).map((m) => (
          <div key={m.id} className={`bubble ${m.mine ? "mine" : ""}`}>
            {(m.productName || m.orderNumber) && <span className="about">{m.productName || `Order ${m.orderNumber}`}</span>}
            <div>{m.body}</div>
            <div className="meta">
              {m.mine ? "You" : m.senderName} at {formatDate(m.createdAt)}
            </div>
          </div>
        ))}
      </div>
      <form
        className="chat-compose"
        onSubmit={(event) => {
          event.preventDefault();
          send();
        }}
      >
        <Textarea
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="Write a message"
          aria-label="Message"
          maxLength={2000}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send();
            }
          }}
        />
        <Button type="submit" variant="primary" icon={Send} loading={busy} disabled={!text.trim()}>
          Send
        </Button>
      </form>
      <ReportDialog
        open={reporting}
        conversation={conversation}
        onClose={() => setReporting(false)}
        onDone={() => {
          setReporting(false);
          onReported();
        }}
      />
    </div>
  );
}

function ReportDialog({ open, conversation, onClose, onDone }) {
  const toast = useToast();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Report this conversation"
      sub={`Nexora support reads the conversation with ${conversation.counterpartName} and takes action if needed.`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={busy}
            disabled={!reason.trim()}
            onClick={async () => {
              setBusy(true);
              try {
                await api(`/api/conversations/${conversation.id}/report`, { method: "POST", body: { reason: reason.trim() } });
                setReason("");
                onDone();
              } catch (error) {
                toast(error.message, "bad");
              } finally {
                setBusy(false);
              }
            }}
          >
            Send report
          </Button>
        </>
      }
    >
      <Field label="What happened?" hint="For example: asked me to pay outside Nexora, abusive language, spam">
        <Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={255} />
      </Field>
    </Modal>
  );
}

const NOTE_ICONS = {
  ORDER_PLACED: Package,
  ORDER_CONFIRMED: PackageCheck,
  NEW_ORDER: Package,
  ORDER_SHIPPED: Truck,
  ORDER_DELIVERED: PackageCheck,
  ORDER_CANCELLED: TriangleAlert,
  ORDER_EXPIRED: TriangleAlert,
  PAYMENT_FAILED: Wallet,
  LOW_STOCK: TriangleAlert,
  BUDGET_ALERT: CircleDollarSign,
  NEW_REVIEW: Star,
  REVIEW_REPLY: Star,
  NEW_MESSAGE: MessageSquare,
};

/** Notification feed with "mark all as read". */
export function NotificationFeed({ onRead }) {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const feed = useLoader(() => api(`/api/notifications${qs({ page, size: 20 })}`), [page]);
  if (feed.loading && !feed.data) return <Loader text="Loading notifications" />;
  if (feed.error && !feed.data) return <ErrorState message={feed.error} onRetry={feed.reload} />;
  const items = feed.data?.content || [];
  return (
    <div className="stack">
      <div className="row-between">
        <span className="small muted">{feed.data.totalElements} notifications</span>
        <Button
          size="sm"
          disabled={!items.some((n) => !n.read)}
          onClick={async () => {
            try {
              await api("/api/notifications/read-all", { method: "PATCH" });
              await feed.reload();
              onRead?.();
            } catch (error) {
              toast(error.message, "bad");
            }
          }}
        >
          Mark all as read
        </Button>
      </div>
      {items.length === 0 ? (
        <div className="panel">
          <Empty icon={Bell} title="You are all caught up">
            Order updates, messages and alerts appear here.
          </Empty>
        </div>
      ) : (
        <div className="note-list">
          {items.map((n) => {
            const Icon = NOTE_ICONS[n.type] || Bell;
            return (
              <div key={n.id} className={`note-item ${n.read ? "" : "unread"}`}>
                <span className="note-icon">
                  <Icon />
                </span>
                <div className="grow">
                  <div className="row-between">
                    <span className="strong small">{n.title}</span>
                    <span className="tiny muted nowrap">{timeAgo(n.createdAt)}</span>
                  </div>
                  <p className="small soft">{n.message}</p>
                </div>
              </div>
            );
          })}
          <Pagination page={page} totalPages={feed.data.totalPages} totalElements={feed.data.totalElements} size={20} onPage={setPage} />
        </div>
      )}
    </div>
  );
}
