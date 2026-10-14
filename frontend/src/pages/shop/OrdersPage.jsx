import { useState } from "react";
import { ArrowLeft, ClipboardList, MessageSquare, PackageCheck, Star, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDate, formatMoney } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import ContactSeller from "../../components/ContactSeller.jsx";
import PaymentForm from "../../components/PaymentForm.jsx";
import { Alert, Badge, Empty, Field, Modal, Pagination, ProductArt, Spinner, StarInput, Tabs } from "../../components/ui.jsx";

const TABS = [
  { id: "", label: "All orders" },
  { id: "PENDING", label: "Awaiting payment" },
  { id: "CONFIRMED", label: "Processing" },
  { id: "SHIPPED", label: "On the way" },
  { id: "DELIVERED", label: "Delivered" },
  { id: "CANCELLED", label: "Cancelled" },
];

export function OrdersPage({ navigate }) {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const orders = useLoader(() => api(`/api/orders${qs({ status, page, size: 10 })}`), [status, page]);

  return (
    <div className="page">
      <div>
        <h1>My orders</h1>
        <p className="muted">Track deliveries, pay pending orders and review what you bought.</p>
      </div>
      <Tabs tabs={TABS} value={status} onChange={(s) => { setStatus(s); setPage(0); }} />
      <Alert>{orders.error}</Alert>
      {orders.loading && !orders.data ? (
        <Spinner />
      ) : orders.data?.content.length === 0 ? (
        <Empty icon={ClipboardList} title={status ? "No orders here" : "No orders yet"} action={!status && <a className="btn btn-primary" href="#/shop">Start shopping</a>}>
          {status ? "Orders with this status will appear here." : "When you place an order it appears here with its delivery status."}
        </Empty>
      ) : (
        <div className={`card flush ${orders.loading ? "refetching" : ""}`}>
          {orders.data?.content.map((order) => {
            const units = order.items.reduce((sum, item) => sum + item.quantity, 0);
            return (
              <button key={order.id} className="order-card" onClick={() => navigate(`/orders/${order.id}`)}>
                <div className="stack tight" style={{ minWidth: 0 }}>
                  <div className="row wrap">
                    <strong>{order.merchantName}</strong>
                    <Badge value={order.status} friendly />
                    {order.paymentState !== "UNPAID" && order.status !== "CANCELLED" && <Badge value={order.paymentState} friendly />}
                  </div>
                  <span className="muted small clamp">
                    {order.items.map((i) => i.productName).join(", ")} ({units} item{units === 1 ? "" : "s"})
                  </span>
                  <span className="muted small">
                    {order.orderNumber} - placed {formatDate(order.createdAt)}
                    {order.trackingNumber ? ` - ${order.carrier} ${order.trackingNumber}` : ""}
                  </span>
                </div>
                <strong className="num">{formatMoney(order.totalAmount, order.currency)}</strong>
              </button>
            );
          })}
        </div>
      )}
      <Pagination page={orders.data} onChange={setPage} />
    </div>
  );
}

function steps(order) {
  const confirmed = order.history.find((h) => h.toStatus === "CONFIRMED");
  return [
    { label: "Order placed", at: order.createdAt, done: true },
    { label: order.paymentState === "PAY_ON_DELIVERY" ? "Confirmed (cash on delivery)" : "Paid", at: confirmed?.changedAt, done: Boolean(confirmed) },
    { label: "Shipped", at: order.shippedAt, done: Boolean(order.shippedAt) },
    { label: "Delivered", at: order.deliveredAt, done: Boolean(order.deliveredAt) },
  ];
}

export function OrderDetailPage({ id, navigate, toast, refreshCounts }) {
  const order = useLoader(() => api(`/api/orders/${id}`), [id]);
  const myReviews = useLoader(() => api("/api/reviews/mine"), [id]);
  const [dialog, setDialog] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [reason, setReason] = useState("");
  const [review, setReview] = useState({ rating: 5, comment: "" });

  if (order.loading && !order.data) return <Spinner />;
  if (order.error) return <Alert>{order.error}</Alert>;
  const o = order.data;
  const reviewed = new Set((myReviews.data || []).filter((r) => r.orderId === o.id).map((r) => r.productId));
  const canPay = o.status === "PENDING" && o.paymentState === "UNPAID";
  const canCancel = o.status === "PENDING" || o.status === "CONFIRMED";
  const close = () => {
    setDialog(null);
    setError("");
  };

  const pay = async (payment) => {
    setBusy(true);
    setError("");
    try {
      const result = await api(`/api/orders/${o.id}/pay`, { method: "POST", body: payment });
      if (result.payment.status === "FAILED") {
        setError(`Payment declined: ${result.payment.failureReason}`);
      } else {
        close();
        toast.show(payment.method === "COD" ? "Order confirmed. Pay the courier on delivery." : "Payment successful");
        order.reload();
        refreshCounts();
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const cancel = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      await api(`/api/orders/${o.id}/cancel`, { method: "PUT", body: { reason: reason || "Cancelled by the customer" } });
      close();
      toast.show(o.paymentState === "PAID" ? "Order cancelled. Your payment will be refunded." : "Order cancelled");
      order.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const submitReview = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      await api("/api/reviews", { method: "POST", body: { orderId: o.id, productId: dialog.item.productId, rating: review.rating, comment: review.comment } });
      close();
      toast.show("Thank you for your review");
      myReviews.reload();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page" style={{ maxWidth: 1080 }}>
      <a className="back" href="#/orders">
        <ArrowLeft size={15} /> My orders
      </a>
      <div className="page-head">
        <div>
          <h1>Order {o.orderNumber}</h1>
          <p className="muted">
            Placed {formatDate(o.createdAt)} - sold by{" "}
            <a className="link" href={`#/store/${o.merchantId}`}>
              {o.merchantName}
            </a>
          </p>
        </div>
        <div className="row wrap">
          {canPay && (
            <button className="btn btn-primary" onClick={() => setDialog({ type: "pay" })}>
              Pay {formatMoney(o.totalAmount, o.currency)}
            </button>
          )}
          <button className="btn" onClick={() => setDialog({ type: "contact" })}>
            <MessageSquare size={15} /> Contact seller
          </button>
          {canCancel && (
            <button className="btn btn-danger" onClick={() => setDialog({ type: "cancel" })}>
              <XCircle size={15} /> Cancel order
            </button>
          )}
        </div>
      </div>

      {o.status === "CANCELLED" ? (
        <Alert kind="warning">
          This order was cancelled{o.cancelReason ? `: ${o.cancelReason}` : ""}.
          {o.paymentState === "REFUNDED" ? " Your payment has been refunded." : ""}
        </Alert>
      ) : (
        <div className="card">
          <div className="stepper">
            {steps(o).map((step) => (
              <div key={step.label} className={`step ${step.done ? "done" : "todo"}`}>
                <div className="step-bar" />
                <strong>{step.label}</strong>
                <span className="muted small">{step.at ? formatDate(step.at) : "Pending"}</span>
              </div>
            ))}
          </div>
          {canPay && o.paymentDeadline && (
            <p className="muted small" style={{ marginTop: 12 }}>
              Please pay by {formatDate(o.paymentDeadline)}. Unpaid orders are cancelled automatically and the items are released.
            </p>
          )}
        </div>
      )}

      <div className="two-col">
        <div className="card flush">
          <div className="card-head" style={{ padding: "16px 18px 0" }}>
            <h3>Items</h3>
          </div>
          {o.items.map((item) => (
            <div className="cart-line" key={item.productId} style={{ gridTemplateColumns: "56px minmax(0,1fr) auto auto" }}>
              <ProductArt name={item.productName} size="sm" />
              <div style={{ minWidth: 0 }}>
                <a className="link" href={`#/product/${item.productId}`} style={{ color: "var(--text)" }}>
                  {item.productName}
                </a>
                <div className="muted small num">
                  {item.quantity} x {formatMoney(item.unitPrice, o.currency)}
                </div>
              </div>
              {o.status === "DELIVERED" &&
                (reviewed.has(item.productId) ? (
                  <span className="muted small">
                    <PackageCheck size={14} /> Reviewed
                  </span>
                ) : (
                  <button className="btn btn-sm" onClick={() => { setReview({ rating: 5, comment: "" }); setDialog({ type: "review", item }); }}>
                    <Star size={14} /> Write a review
                  </button>
                ))}
              <strong className="num">{formatMoney(item.lineTotal, o.currency)}</strong>
            </div>
          ))}
          <div className="summary-line total" style={{ padding: "12px 16px 16px" }}>
            <span>Total</span>
            <span className="num">{formatMoney(o.totalAmount, o.currency)}</span>
          </div>
        </div>

        <div className="stack">
          <div className="card">
            <h3>Delivery</h3>
            <dl className="kv">
              <dt>Address</dt>
              <dd>{o.shippingAddress}</dd>
              <dt>Courier</dt>
              <dd>{o.carrier || "Not shipped yet"}</dd>
              <dt>Tracking number</dt>
              <dd className="mono">{o.trackingNumber || "-"}</dd>
            </dl>
          </div>
          <div className="card">
            <h3>Payment</h3>
            <dl className="kv">
              <dt>Status</dt>
              <dd>
                <Badge value={o.paymentState} friendly />
              </dd>
              <dt>Method</dt>
              <dd>{o.paymentMethod ? { CARD: "Card", UPI: "UPI", COD: "Cash on delivery" }[o.paymentMethod] : "-"}</dd>
            </dl>
          </div>
          <div className="card">
            <h3>History</h3>
            <ul className="timeline">
              {o.history.map((h, index) => (
                <li key={index}>
                  <span className="muted small">{formatDate(h.changedAt)}</span>
                  <span>
                    <Badge value={h.toStatus} friendly /> {h.note && <span className="muted small"> {h.note}</span>}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      {dialog?.type === "pay" && (
        <Modal title={`Pay order ${o.orderNumber}`} onClose={close}>
          <PaymentForm amount={o.totalAmount} busy={busy} error={error} onClearError={() => setError("")} onSubmit={pay} />
        </Modal>
      )}
      {dialog?.type === "cancel" && (
        <Modal title="Cancel this order?" onClose={close}>
          <form className="stack" onSubmit={cancel}>
            <p>
              The items go back to the seller's stock.
              {o.paymentState === "PAID" ? " Your payment is refunded to the same method." : ""}
            </p>
            <Field label="Reason (optional)">
              <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} placeholder="For example: ordered by mistake" />
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
      {dialog?.type === "review" && (
        <Modal title={`Review ${dialog.item.productName}`} onClose={close}>
          <form className="stack" onSubmit={submitReview}>
            <StarInput value={review.rating} onChange={(rating) => setReview({ ...review, rating })} />
            <Field label="Your review (optional)">
              <textarea rows={4} maxLength={1000} value={review.comment} onChange={(e) => setReview({ ...review, comment: e.target.value })} placeholder="What did you like or dislike?" />
            </Field>
            <Alert onClose={() => setError("")}>{error}</Alert>
            <div className="row end">
              <button className="btn btn-primary" disabled={busy}>
                {busy ? "Posting..." : "Post review"}
              </button>
            </div>
          </form>
        </Modal>
      )}
      {dialog?.type === "contact" && (
        <ContactSeller merchantId={o.merchantId} storeName={o.merchantName} orderNumber={o.orderNumber} onClose={close} onSent={(cid) => navigate(`/messages/${cid}`)} />
      )}
    </div>
  );
}
