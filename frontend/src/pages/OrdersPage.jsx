import { useState } from "react";
import { ArrowLeft, ClipboardList, XCircle } from "lucide-react";
import { api, qs } from "../api.js";
import { formatDate, formatMoney } from "../format.js";
import { Alert, Badge, Empty, Pagination, Spinner } from "../components/ui.jsx";
import { useLoader } from "../hooks.jsx";
import PaymentForm from "./PaymentForm.jsx";

export function OrdersPage({ navigate }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const orders = useLoader(() => api(`/api/orders${qs({ page, size: 10, status })}`), [page, status]);

  return (
    <div className="page">
      <div className="page-head">
        <h1>My orders</h1>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All statuses</option>
          {["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"].map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>
      <Alert>{orders.error}</Alert>
      {orders.loading && !orders.data ? (
        <Spinner />
      ) : orders.data?.content.length === 0 ? (
        <Empty icon={ClipboardList} title="No orders yet">Your orders will appear here.</Empty>
      ) : (
        <div className="card">
          <table className="table clickable">
            <thead>
              <tr>
                <th>Order</th>
                <th>Placed</th>
                <th>Items</th>
                <th>Status</th>
                <th>Payment</th>
                <th className="right">Total</th>
              </tr>
            </thead>
            <tbody>
              {orders.data?.content.map((order) => (
                <tr key={order.id} onClick={() => navigate(`/orders/${order.id}`)}>
                  <td><strong>{order.orderNumber}</strong></td>
                  <td>{formatDate(order.createdAt)}</td>
                  <td>{order.items.reduce((sum, item) => sum + item.quantity, 0)}</td>
                  <td><Badge value={order.status} /></td>
                  <td><Badge value={order.paymentState} /></td>
                  <td className="right">{formatMoney(order.totalAmount, order.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={orders.data} onChange={setPage} />
    </div>
  );
}

export function OrderDetailPage({ id, navigate, toast, isAdmin }) {
  const order = useLoader(() => api(`/api/orders/${id}`), [id]);
  const payments = useLoader(() => api(`/api/payments/order/${id}`).catch(() => []), [id]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (order.loading && !order.data) return <div className="page"><Spinner /></div>;
  if (order.error) return <div className="page"><Alert>{order.error}</Alert></div>;
  const o = order.data;
  const canCancel = ["PENDING", "CONFIRMED"].includes(o.status);

  const cancel = async () => {
    if (!window.confirm(`Cancel order ${o.orderNumber}?`)) return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/orders/${o.id}/cancel`, { method: "PUT", body: { reason: "Cancelled by customer" } });
      toast.show("Order cancelled. Stock released" + (o.paymentState === "PAID" ? " and payment refunded." : "."));
      order.reload();
      payments.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <button className="btn btn-ghost" onClick={() => navigate(isAdmin ? "/admin/orders" : "/orders")}>
        <ArrowLeft size={16} /> Back
      </button>
      <div className="page-head">
        <div>
          <h1>{o.orderNumber}</h1>
          <p className="muted">Placed {formatDate(o.createdAt)} by {o.customerUsername}</p>
        </div>
        <div className="row">
          <Badge value={o.status} />
          <Badge value={o.paymentState} />
        </div>
      </div>
      <Alert onClose={() => setError("")}>{error}</Alert>
      {o.status === "PENDING" && o.paymentDeadline && (
        <Alert kind="info">Stock is reserved until {formatDate(o.paymentDeadline)}. Unpaid orders are cancelled automatically.</Alert>
      )}
      <div className="two-col">
        <div className="stack">
          <div className="card">
            <table className="table">
              <thead>
                <tr><th>Product</th><th>Unit price</th><th>Qty</th><th className="right">Total</th></tr>
              </thead>
              <tbody>
                {o.items.map((item) => (
                  <tr key={item.productId}>
                    <td><strong>{item.productName}</strong><div className="muted small">{item.sku}</div></td>
                    <td>{formatMoney(item.unitPrice, o.currency)}</td>
                    <td>{item.quantity}</td>
                    <td className="right">{formatMoney(item.lineTotal, o.currency)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr><td colSpan={3}><strong>Total</strong></td><td className="right"><strong>{formatMoney(o.totalAmount, o.currency)}</strong></td></tr>
              </tfoot>
            </table>
          </div>
          <div className="card">
            <h3>Status history</h3>
            <ol className="timeline">
              {o.history.map((h, index) => (
                <li key={index}>
                  <Badge value={h.toStatus} />
                  <div>
                    <div>{h.note || (h.fromStatus ? `${h.fromStatus} → ${h.toStatus}` : "Created")}</div>
                    <div className="muted small">{formatDate(h.changedAt)} · {h.changedBy}</div>
                  </div>
                </li>
              ))}
            </ol>
          </div>
          {payments.data?.length > 0 && (
            <div className="card">
              <h3>Payment attempts</h3>
              <table className="table">
                <thead><tr><th>Reference</th><th>Method</th><th>Instrument</th><th>Status</th><th>When</th></tr></thead>
                <tbody>
                  {payments.data.map((p) => (
                    <tr key={p.id}>
                      <td className="mono small">{p.transactionRef}</td>
                      <td>{p.method}</td>
                      <td>{p.maskedInstrument}</td>
                      <td><Badge value={p.status} />{p.failureReason && <div className="muted small">{p.failureReason}</div>}</td>
                      <td>{formatDate(p.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <aside className="stack">
          {o.status === "PENDING" && !isAdmin && (
            <PaymentForm
              order={o}
              onPaid={(result) => {
                if (result.order.status === "CONFIRMED") toast.show(`Order ${o.orderNumber} confirmed`);
                order.reload();
                payments.reload();
              }}
            />
          )}
          <div className="card stack">
            <h3>Delivery</h3>
            <p>{o.shippingAddress}</p>
            {o.cancelReason && <p className="muted">Cancelled: {o.cancelReason}</p>}
            {canCancel && (
              <button className="btn btn-danger" disabled={busy} onClick={cancel}>
                <XCircle size={16} /> Cancel order
              </button>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
