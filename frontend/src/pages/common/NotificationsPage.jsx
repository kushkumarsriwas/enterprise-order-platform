import { useState } from "react";
import { AlertTriangle, Bell, CheckCheck, CreditCard, MessageSquare, Package, PiggyBank, ShoppingBag, Star, Truck, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { timeAgo } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { Alert, Empty, Pagination, Spinner } from "../../components/ui.jsx";

const ICONS = {
  NEW_ORDER: ShoppingBag,
  ORDER_PLACED: ShoppingBag,
  ORDER_CONFIRMED: CreditCard,
  ORDER_SHIPPED: Truck,
  ORDER_DELIVERED: Package,
  ORDER_CANCELLED: XCircle,
  PAYMENT_FAILED: AlertTriangle,
  LOW_STOCK: AlertTriangle,
  BUDGET_ALERT: PiggyBank,
  NEW_MESSAGE: MessageSquare,
  NEW_REVIEW: Star,
};

/** Where a notification leads, by type and by who is reading it. */
function target(type, role) {
  const merchant = role === "MERCHANT";
  switch (type) {
    case "NEW_MESSAGE":
      return merchant ? "/merchant/messages" : "/messages";
    case "NEW_ORDER":
      return "/merchant/orders";
    case "LOW_STOCK":
      return merchant ? "/merchant/inventory" : null;
    case "BUDGET_ALERT":
      return "/merchant/finance";
    case "NEW_REVIEW":
      return "/merchant/reviews";
    default:
      return type.startsWith("ORDER_") || type === "PAYMENT_FAILED" ? (merchant ? "/merchant/orders" : role === "CUSTOMER" ? "/orders" : null) : null;
  }
}

export default function NotificationsPage({ user, navigate, refreshCounts }) {
  const [page, setPage] = useState(0);
  const list = useLoader(() => api(`/api/notifications${qs({ page, size: 15 })}`), [page]);

  const markAll = async () => {
    await api("/api/notifications/read-all", { method: "PATCH" });
    list.reload();
    refreshCounts();
  };
  const open = async (n) => {
    if (!n.read) {
      await api(`/api/notifications/${n.id}/read`, { method: "PATCH" }).catch(() => {});
      refreshCounts();
    }
    const to = target(n.type, user.role);
    if (to) navigate(to);
    else list.reload();
  };

  return (
    <div className="page" style={{ maxWidth: 860 }}>
      <div className="page-head">
        <div>
          <h1>Notifications</h1>
          <p className="muted">Orders, payments, messages and stock alerts.</p>
        </div>
        <button className="btn btn-ghost" onClick={markAll}>
          <CheckCheck size={16} /> Mark all as read
        </button>
      </div>
      <Alert>{list.error}</Alert>
      {list.loading && !list.data ? (
        <Spinner />
      ) : list.data?.content.length === 0 ? (
        <Empty icon={Bell} title="You're all caught up">New orders, payment updates and messages will show up here.</Empty>
      ) : (
        <div className="card flush">
          {list.data?.content.map((n) => {
            const Icon = ICONS[n.type] || Bell;
            return (
              <button key={n.id} className={`notification ${n.read ? "" : "unread"}`} onClick={() => open(n)}>
                <span className="notif-icon">
                  <Icon size={17} />
                </span>
                <span className="grow">
                  <span className="row between">
                    <strong>{n.title}</strong>
                    <span className="muted small nowrap">{timeAgo(n.createdAt)}</span>
                  </span>
                  <p>{n.message}</p>
                </span>
              </button>
            );
          })}
        </div>
      )}
      <Pagination page={list.data} onChange={setPage} />
    </div>
  );
}
