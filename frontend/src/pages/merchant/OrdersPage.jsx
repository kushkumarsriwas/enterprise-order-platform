import { useState } from "react";
import { ArrowLeft, CheckCircle2, ClipboardList, Truck, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDate, formatMoney, label } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { Alert, Badge, Empty, Field, Modal, PageHeader, Pagination, Spinner, Tabs } from "../../components/ui.jsx";

const TABS = [
  { id: "CONFIRMED", label: "To ship" },
  { id: "PENDING", label: "Awaiting payment" },
  { id: "SHIPPED", label: "Shipped" },
  { id: "DELIVERED", label: "Delivered" },
  { id: "CANCELLED", label: "Cancelled" },
  { id: "", label: "All" },
];
const CARRIERS = ["Aramex", "DHL Express", "Emirates Post", "FedEx", "SMSA Express", "Quiqup", "Own delivery"];
const STATUS_TEXT = { PENDING: "Awaiting payment", CONFIRMED: "To ship" };

export function MerchantOrders({ navigate }) {
  const [status, setStatus] = useState("CONFIRMED");
  const [page, setPage] = useState(0);
  const orders = useLoader(() => api(`/api/merchant/orders${qs({ status, page, size: 15 })}`), [status, page]);

  return (
    <div className="page">
      <PageHeader title="Orders" subtitle="Orders from the marketplace. Paid orders are ready to ship; stock was already deducted when the customer paid." />
      <Tabs tabs={TABS} value={status} onChange={(s) => { setStatus(s); setPage(0); }} />
      <Alert>{orders.error}</Alert>
      {orders.loading && !orders.data ? (
        <Spinner />
      ) : orders.data?.content.length === 0 ? (
        <Empty icon={ClipboardList} title={status === "CONFIRMED" ? "Nothing to ship" : "No orders here"}>
          {status === "CONFIRMED" ? "Paid orders waiting for delivery appear here." : "Orders with this status will appear here."}
        </Empty>
      ) : (
        <div className={`card flush ${orders.loading ? "refetching" : ""}`}>
          <div className="table-wrap">
            <table className="table clickable">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Items</th>
                  <th>Payment</th>
                  <th className="num">Total</th>
                  <th className="num">Gross profit</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {orders.data?.content.map((o) => (
                  <tr key={o.id} onClick={() => navigate(`/merchant/orders/${o.id}`)}>
                    <td>
                      <strong>{o.orderNumber}</strong>
                      <span className="sub">{formatDate(o.createdAt)}</span>
                    </td>
                    <td>
                      {o.customerUsername}
                      <span className="sub">{o.shippingAddress.slice(0, 40)}{o.shippingAddress.length > 40 ? "..." : ""}</span>
                    </td>
                    <td>{o.items.reduce((n, i) => n + i.quantity, 0)}</td>
                    <td>
                      <Badge value={o.paymentState} friendly />
                    </td>
                    <td className="num">{formatMoney(o.totalAmount)}</td>
                    <td className={`num ${Number(o.grossProfit) < 0 ? "neg" : ""}`}>{formatMoney(o.grossProfit)}</td>
                    <td>
                      <Badge value={o.status} text={STATUS_TEXT[o.status]} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <Pagination page={orders.data} onChange={setPage} />
    </div>
  );
}

export function MerchantOrderDetail({ id, toast }) {
  const order = useLoader(() => api(`/api/merchant/orders/${id}`), [id]);
  const [dialog, setDialog] = useState(null);
  const [form, setForm] = useState({ carrier: CARRIERS[0], trackingNumber: "", note: "", reason: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  if (order.loading && !order.data) return <Spinner />;
  if (order.error) return <Alert>{order.error}</Alert>;
  const o = order.data;
  const close = () => {
    setDialog(null);
    setError("");
  };
  const act = async (path, body, message) => {
    setBusy(true);
    setError("");
    try {
      await api(`/api/merchant/orders/${o.id}/${path}`, { method: "PUT", body });
      close();
      toast.show(message);
      order.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  const cod = o.paymentState === "PAY_ON_DELIVERY";

  return (
    <div className="page" style={{ maxWidth: 1080 }}>
      <PageHeader
        back={
          <a className="back" href="#/merchant/orders">
            <ArrowLeft size={15} /> Orders
          </a>
        }
        title={`Order ${o.orderNumber}`}
        subtitle={`Placed ${formatDate(o.createdAt)} by ${o.customerUsername}`}
        actions={
          <>
            <Badge value={o.status} text={STATUS_TEXT[o.status]} />
            {o.status === "CONFIRMED" && (
              <button className="btn btn-primary" onClick={() => setDialog("ship")}>
                <Truck size={16} /> Ship order
              </button>
            )}
            {o.status === "SHIPPED" && (
              <button className="btn btn-primary" onClick={() => setDialog("deliver")}>
                <CheckCircle2 size={16} /> Mark as delivered
              </button>
            )}
            {(o.status === "CONFIRMED" || o.status === "PENDING") && (
              <button className="btn btn-danger" onClick={() => setDialog("cancel")}>
                <XCircle size={16} /> Cancel{o.paymentState === "PAID" ? " and refund" : ""}
              </button>
            )}
          </>
        }
      />
      {o.status === "PENDING" && <Alert kind="info">The customer has not paid yet. The stock stays reserved until the payment deadline, then it is released automatically.</Alert>}
      {o.status === "CANCELLED" && <Alert kind="warning">Cancelled{o.cancelReason ? `: ${o.cancelReason}` : ""}. Payment: {label(o.paymentState)}.</Alert>}

      <div className="two-col">
        <div className="stack">
          <div className="card flush">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th className="num">Qty</th>
                    <th className="num">Price</th>
                    <th className="num">Unit cost</th>
                    <th className="num">Line total</th>
                    <th className="num">Profit</th>
                  </tr>
                </thead>
                <tbody>
                  {o.items.map((item) => (
                    <tr key={item.productId}>
                      <td>
                        {item.productName}
                        <span className="sub">{item.sku}</span>
                      </td>
                      <td className="num">{item.quantity}</td>
                      <td className="num">{formatMoney(item.unitPrice)}</td>
                      <td className="num">{formatMoney(item.unitCost)}</td>
                      <td className="num">{formatMoney(item.lineTotal)}</td>
                      <td className={`num ${Number(item.lineProfit) < 0 ? "neg" : ""}`}>{formatMoney(item.lineProfit)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={4}>Order total</td>
                    <td className="num">{formatMoney(o.totalAmount)}</td>
                    <td className="num">{formatMoney(o.grossProfit)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
          <div className="card">
            <h3>History</h3>
            <ul className="timeline">
              {o.history.map((h, index) => (
                <li key={index}>
                  <span className="muted small">{formatDate(h.changedAt)}</span>
                  <span>
                    <Badge value={h.toStatus} text={STATUS_TEXT[h.toStatus]} /> <span className="muted small">by {h.changedBy}{h.note ? ` - ${h.note}` : ""}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
        <div className="stack">
          <div className="card">
            <h3>Deliver to</h3>
            <dl className="kv">
              <dt>Customer</dt>
              <dd>{o.customerUsername}</dd>
              <dt>Phone</dt>
              <dd>{o.customerPhone ? <a href={`tel:${o.customerPhone.replace(/\s/g, "")}`}>{o.customerPhone}</a> : "-"}</dd>
              <dt>Email</dt>
              <dd>
                <a href={`mailto:${o.customerEmail}`}>{o.customerEmail}</a>
              </dd>
              <dt>Address</dt>
              <dd>{o.shippingAddress}</dd>
            </dl>
          </div>
          <div className="card">
            <h3>Payment and shipping</h3>
            <dl className="kv">
              <dt>Payment</dt>
              <dd>
                <Badge value={o.paymentState} friendly /> {o.paymentMethod && <span className="muted small">{label(o.paymentMethod)}</span>}
              </dd>
              <dt>Courier</dt>
              <dd>{o.carrier || "-"}</dd>
              <dt>Tracking</dt>
              <dd className="mono">{o.trackingNumber || "-"}</dd>
              <dt>Shipped</dt>
              <dd>{o.shippedAt ? formatDate(o.shippedAt) : "-"}</dd>
              <dt>Delivered</dt>
              <dd>{o.deliveredAt ? formatDate(o.deliveredAt) : "-"}</dd>
            </dl>
          </div>
        </div>
      </div>

      {dialog === "ship" && (
        <Modal title={`Ship ${o.orderNumber}`} onClose={close}>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              act("ship", { carrier: form.carrier, trackingNumber: form.trackingNumber.trim(), note: form.note || null }, "Order shipped. The customer was notified.");
            }}
          >
            <div className="form-grid">
              <Field label="Courier">
                <select value={form.carrier} onChange={(e) => setForm({ ...form, carrier: e.target.value })}>
                  {CARRIERS.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </Field>
              <Field label="Tracking number">
                <input value={form.trackingNumber} onChange={(e) => setForm({ ...form, trackingNumber: e.target.value })} maxLength={60} required autoFocus />
              </Field>
              <Field label="Note for the customer (optional)" className="span-2">
                <input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} maxLength={255} />
              </Field>
            </div>
            <Alert onClose={() => setError("")}>{error}</Alert>
            <div className="row end">
              <button type="button" className="btn btn-ghost" onClick={close}>
                Cancel
              </button>
              <button className="btn btn-primary" disabled={busy}>
                {busy ? "Saving..." : "Confirm shipment"}
              </button>
            </div>
          </form>
        </Modal>
      )}
      {dialog === "deliver" && (
        <Modal title="Mark as delivered?" onClose={close}>
          <div className="stack">
            <p>{cod ? `This also confirms that the courier collected ${formatMoney(o.totalAmount)} in cash.` : "The customer can then review the products."}</p>
            <Alert onClose={() => setError("")}>{error}</Alert>
            <div className="row end">
              <button className="btn btn-ghost" onClick={close}>
                Not yet
              </button>
              <button className="btn btn-primary" disabled={busy} onClick={() => act("deliver", undefined, "Order delivered")}>
                {busy ? "Saving..." : "Mark as delivered"}
              </button>
            </div>
          </div>
        </Modal>
      )}
      {dialog === "cancel" && (
        <Modal title={`Cancel ${o.orderNumber}?`} onClose={close}>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              act("cancel", { reason: form.reason || "Cancelled by the seller" }, o.paymentState === "PAID" ? "Order cancelled and refunded" : "Order cancelled");
            }}
          >
            <p>
              The items go back into your available stock{o.paymentState === "PAID" ? " and the customer is refunded in full" : ""}. The customer is notified with your reason.
            </p>
            <Field label="Reason">
              <input value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} maxLength={255} placeholder="For example: item damaged in the warehouse" required />
            </Field>
            <Alert onClose={() => setError("")}>{error}</Alert>
            <div className="row end">
              <button type="button" className="btn btn-ghost" onClick={close}>
                Keep order
              </button>
              <button className="btn btn-danger" disabled={busy}>
                {busy ? "Cancelling..." : "Cancel order"}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
