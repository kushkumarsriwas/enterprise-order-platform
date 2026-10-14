import { useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import { api, qs } from "../api.js";
import { timeAgo } from "../format.js";
import { Alert, Badge, Empty, Pagination, Spinner } from "../components/ui.jsx";
import { useLoader } from "../hooks.jsx";

export default function NotificationsPage({ onChange }) {
  const [page, setPage] = useState(0);
  const list = useLoader(() => api(`/api/notifications${qs({ page, size: 15 })}`), [page]);

  const markAll = async () => {
    await api("/api/notifications/read-all", { method: "PATCH" });
    list.reload();
    onChange();
  };
  const markOne = async (n) => {
    if (n.read) return;
    await api(`/api/notifications/${n.id}/read`, { method: "PATCH" });
    list.reload();
    onChange();
  };

  return (
    <div className="page">
      <div className="page-head">
        <h1>Notifications</h1>
        <button className="btn btn-ghost" onClick={markAll}><CheckCheck size={16} /> Mark all as read</button>
      </div>
      <Alert>{list.error}</Alert>
      {list.loading && !list.data ? (
        <Spinner />
      ) : list.data?.content.length === 0 ? (
        <Empty icon={Bell} title="No notifications">Order and payment updates will show up here.</Empty>
      ) : (
        <div className="card notification-list">
          {list.data?.content.map((n) => (
            <button key={n.id} className={`notification ${n.read ? "" : "unread"}`} onClick={() => markOne(n)}>
              <div className="row between">
                <strong>{n.title}</strong>
                <span className="muted small">{timeAgo(n.createdAt)}</span>
              </div>
              <p>{n.message}</p>
              <div className="row">
                <Badge value={n.type} tone={n.type.includes("FAIL") || n.type.includes("CANCEL") || n.type === "LOW_STOCK" ? "red" : "blue"} />
                {n.audience === "ADMINS" && <Badge value="ADMIN ALERT" tone="violet" />}
              </div>
            </button>
          ))}
        </div>
      )}
      <Pagination page={list.data} onChange={setPage} />
    </div>
  );
}
