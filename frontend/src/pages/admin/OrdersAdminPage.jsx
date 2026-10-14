import { useState } from "react";
import { Truck, PackageCheck, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDate, formatMoney } from "../../format.js";
import { Alert, Badge, Pagination, Spinner } from "../../components/ui.jsx";
import { useLoader } from "../../hooks.jsx";

export default function OrdersAdminPage({ navigate, toast }) {
  const [status, setStatus] = useState("CONFIRMED");
  const [page, setPage] = useState(0);
  const orders = useLoader(() => api(`/api/orders${qs({ status, page, size: 15 })}`), [status, page]);

  const move = async (order, next) => {
    const note = next === "SHIPPED" ? window.prompt("Tracking / courier note (optional)", "") : null;
    if (next === "CANCELLED" && !window.confirm(`Cancel ${order.orderNumber}? Stock is released and payments are refunded.`)) return;
    try {
      await api(`/api/orders/${order.id}/status`, { method: "PUT", body: { status: next, note: note || null } });
      toast.show(`${order.orderNumber} → ${next}`);
      orders.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <h1>Order management</h1>
        <div className="segmented">
          {["", "PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"].map((s) => (
            <button key={s || "ALL"} className={status === s ? "active" : ""} onClick={() => { setStatus(s); setPage(0); }}>{s || "ALL"}</button>
          ))}
        </div>
      </div>
      <Alert>{orders.error}</Alert>
      {orders.loading && !orders.data ? <Spinner /> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Order</th><th>Customer</th><th>Placed</th><th>Status</th><th>Payment</th><th className="right">Total</th><th className="right">Actions</th></tr></thead>
            <tbody>
              {orders.data?.content.map((o) => (
                <tr key={o.id}>
                  <td><button className="link" onClick={() => navigate(`/orders/${o.id}`)}>{o.orderNumber}</button></td>
                  <td>{o.customerUsername}</td>
                  <td className="nowrap">{formatDate(o.createdAt)}</td>
                  <td><Badge value={o.status} /></td>
                  <td><Badge value={o.paymentState} />{o.paymentMethod && <div className="muted small">{o.paymentMethod}</div>}</td>
                  <td className="right">{formatMoney(o.totalAmount, o.currency)}</td>
                  <td className="right nowrap">
                    {o.status === "CONFIRMED" && <button className="btn btn-sm" onClick={() => move(o, "SHIPPED")}><Truck size={14} /> Ship</button>}
                    {o.status === "SHIPPED" && <button className="btn btn-sm" onClick={() => move(o, "DELIVERED")}><PackageCheck size={14} /> Delivered</button>}
                    {["PENDING", "CONFIRMED"].includes(o.status) && <button className="icon-btn danger" title="Cancel" onClick={() => move(o, "CANCELLED")}><XCircle size={16} /></button>}
                  </td>
                </tr>
              ))}
              {orders.data?.content.length === 0 && <tr><td colSpan={7} className="muted">No orders in this status</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={orders.data} onChange={setPage} />
    </div>
  );
}
