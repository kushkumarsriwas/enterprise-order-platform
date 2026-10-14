import { useEffect, useRef, useState } from "react";
import { ArrowLeft, MessageSquare, Package, Send, ShoppingBag } from "lucide-react";
import { api } from "../api.js";
import { formatDate, timeAgo } from "../format.js";
import { useInterval, useLoader } from "../hooks.jsx";
import { Alert, Avatar, Empty, Spinner } from "./ui.jsx";

/** Inbox shared by customers and merchants: conversations on the left, the selected thread on the right. */
export default function Messenger({ user, conversationId, basePath, onRead, emptyHint }) {
  const conversations = useLoader(() => api("/api/conversations?size=50"), []);
  useInterval(() => conversations.reload(), 15000);
  const selected = conversations.data?.content.find((c) => String(c.id) === String(conversationId));

  return (
    <div className={`card messenger ${conversationId ? "has-thread" : ""}`}>
      <div className="conv-list">
        {conversations.loading && !conversations.data && <div style={{ padding: 16 }}><Spinner /></div>}
        <div style={{ padding: conversations.error ? 12 : 0 }}><Alert>{conversations.error}</Alert></div>
        {conversations.data?.content.length === 0 && (
          <Empty icon={MessageSquare} title="No conversations yet">{emptyHint}</Empty>
        )}
        {conversations.data?.content.map((c) => (
          <a key={c.id} href={`#${basePath}/${c.id}`} className={`conv ${String(c.id) === String(conversationId) ? "active" : ""}`}>
            <Avatar name={c.counterpartName} />
            <div className="conv-body">
              <div className="row between">
                <strong>{c.counterpartName}</strong>
                <span className="muted small nowrap">{timeAgo(c.lastMessageAt)}</span>
              </div>
              <p>{c.lastMessagePreview || "No messages yet"}</p>
            </div>
            {c.unread > 0 && <span className="unread-dot" aria-label={`${c.unread} unread`}>{c.unread}</span>}
          </a>
        ))}
      </div>
      {conversationId ? (
        <Thread
          key={conversationId}
          id={conversationId}
          user={user}
          conversation={selected}
          basePath={basePath}
          onActivity={() => {
            conversations.reload();
            onRead?.();
          }}
        />
      ) : (
        <div className="thread">
          <Empty icon={MessageSquare} title="Select a conversation">Messages with {user.role === "MERCHANT" ? "your customers" : "sellers"} appear here.</Empty>
        </div>
      )}
    </div>
  );
}

function Thread({ id, user, conversation, basePath, onActivity }) {
  const messages = useLoader(() => api(`/api/conversations/${id}/messages?size=100`), [id]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const bottom = useRef(null);
  const count = messages.data?.content.length || 0;

  useEffect(() => {
    api(`/api/conversations/${id}/read`, { method: "POST" }).then(onActivity).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, count]);
  useEffect(() => bottom.current?.scrollIntoView({ block: "end" }), [count]);
  useInterval(() => messages.reload(), 8000);

  const send = async (event) => {
    event.preventDefault();
    if (!text.trim()) return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/conversations/${id}/messages`, { method: "POST", body: { message: text.trim() } });
      setText("");
      await messages.reload();
      onActivity();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const ordered = [...(messages.data?.content || [])].reverse();
  const title = conversation?.counterpartName || "Conversation";
  return (
    <div className="thread">
      <div className="thread-head">
        <div className="row">
          <a className="icon-btn hidden-desktop" href={`#${basePath}`} aria-label="Back to conversations">
            <ArrowLeft size={16} />
          </a>
          <Avatar name={title} />
          <div>
            <strong>{title}</strong>
            <div className="muted small">{user.role === "MERCHANT" ? "Customer" : "Seller"}</div>
          </div>
        </div>
        {user.role === "CUSTOMER" && conversation && (
          <a className="btn btn-ghost btn-sm" href={`#/store/${conversation.merchantId}`}>
            Visit store
          </a>
        )}
      </div>
      <div className="thread-body" aria-live="polite">
        {messages.loading && !messages.data && <Spinner />}
        <Alert>{messages.error}</Alert>
        {ordered.map((m) => (
          <div key={m.id} className={`bubble ${m.mine ? "mine" : ""}`}>
            {(m.productName || m.orderNumber) && (
              <span className="context-chip">
                {m.productName ? <ShoppingBag size={12} /> : <Package size={12} />}
                {m.productName || `Order ${m.orderNumber}`}
              </span>
            )}
            <div>{m.body}</div>
            <time dateTime={m.createdAt}>{m.mine ? "You" : m.senderName} - {formatDate(m.createdAt)}</time>
          </div>
        ))}
        <div ref={bottom} />
      </div>
      <form className="composer" onSubmit={send}>
        <textarea
          rows={1}
          value={text}
          maxLength={2000}
          placeholder="Write a message"
          aria-label="Message"
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) send(event);
          }}
        />
        <button className="btn btn-primary" disabled={busy || !text.trim()} aria-label="Send message">
          <Send size={16} />
        </button>
      </form>
      {error && <div style={{ padding: "0 12px 12px" }}><Alert onClose={() => setError("")}>{error}</Alert></div>}
    </div>
  );
}
