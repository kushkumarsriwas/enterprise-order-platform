import { useState } from "react";
import { CircleAlert, CircleCheck, Package, PackageCheck, Star, Truck, Wallet, XCircle } from "lucide-react";
import { api } from "../api.js";
import { formatDate, formatDay, label } from "../format.js";
import { useToast } from "../hooks.jsx";
import { Button, Field, Modal, Notice, Select, Textarea } from "./ui.jsx";

/** Where an order is in its life: placed, confirmed (paid or cash on delivery), shipped, delivered. */
export function OrderTracker({ order }) {
  if (order.status === "CANCELLED") {
    return (
      <Notice tone="bad" icon={XCircle}>
        <strong>Cancelled</strong> {order.cancelReason ? `- ${order.cancelReason}` : ""}
        {order.paymentState === "REFUNDED" && <div className="small">The payment was refunded to the original payment method.</div>}
      </Notice>
    );
  }
  const confirmedAt = order.history?.find((h) => h.toStatus === "CONFIRMED")?.changedAt;
  const steps = [
    { key: "PENDING", title: "Placed", icon: Package, at: order.createdAt },
    {
      key: "CONFIRMED",
      title: order.paymentState === "PAY_ON_DELIVERY" ? "Confirmed, pay on delivery" : "Paid and confirmed",
      icon: Wallet,
      at: confirmedAt || order.confirmedAt,
    },
    { key: "SHIPPED", title: "Shipped", icon: Truck, at: order.shippedAt },
    { key: "DELIVERED", title: "Delivered", icon: PackageCheck, at: order.deliveredAt },
  ];
  const reached = steps.findIndex((s) => s.key === order.status);
  return (
    <ol className="tracker" aria-label="Order progress">
      {steps.map((step, index) => {
        const state = index < reached ? "done" : index === reached ? (order.status === "DELIVERED" ? "done" : "current") : "";
        return (
          <li key={step.key} className={state} aria-current={index === reached ? "step" : undefined}>
            <span className="dot">{state === "done" ? <CircleCheck size={16} /> : <step.icon size={16} />}</span>
            <b>{step.title}</b>
            <span>{step.at && index <= reached ? formatDay(step.at) : index === reached + 1 && order.status === "PENDING" ? "After payment" : ""}</span>
          </li>
        );
      })}
    </ol>
  );
}

const CUSTOMER_REASONS = ["I changed my mind", "I found a better price", "I ordered by mistake", "Delivery would take too long", "Other"];
const SELLER_REASONS = ["Out of stock", "Cannot deliver to this address", "Pricing error", "Customer asked to cancel", "Suspected fraud", "Other"];

/** Cancel an order (customer or store). Paid orders are refunded and the stock goes back to the store. */
export function CancelOrderDialog({ open, order, path, seller, onClose, onDone }) {
  const toast = useToast();
  const reasons = seller ? SELLER_REASONS : CUSTOMER_REASONS;
  const [reason, setReason] = useState(reasons[0]);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const paid = order.paymentState === "PAID";
  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const text = reason === "Other" ? note.trim() || "Other" : note.trim() ? `${reason}: ${note.trim()}` : reason;
      const result = await api(path, { method: "PUT", body: { reason: text.slice(0, 255) } });
      toast(`Order ${order.orderNumber} cancelled${paid ? ". The payment is being refunded." : ""}`);
      onDone(result);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Cancel order ${order.orderNumber}?`}
      sub={
        paid
          ? "The full amount is refunded to the original payment method and the stock goes back to the store."
          : "Nothing has been charged. The reserved stock goes back to the store."
      }
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Keep the order
          </Button>
          <Button variant="danger" loading={busy} onClick={submit}>
            Cancel the order
          </Button>
        </>
      }
    >
      <div className="stack">
        <Field label="Reason">
          <Select value={reason} onChange={(event) => setReason(event.target.value)} options={reasons.map((r) => ({ value: r, label: r }))} />
        </Field>
        <Field
          label={reason === "Other" ? "Tell us why" : "Anything to add? (optional)"}
          hint={seller ? "The customer sees this reason" : "The store sees this reason"}
        >
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={200} rows={3} />
        </Field>
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

/** Five large stars as a radio group (arrow keys work). */
export function StarPicker({ value, onChange }) {
  const words = ["", "Poor", "Fair", "Good", "Very good", "Excellent"];
  return (
    <div className="row" style={{ gap: 12 }}>
      <div className="star-picker" role="radiogroup" aria-label="Your rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n} star${n > 1 ? "s" : ""}`}
            className={n <= value ? "on" : ""}
            onClick={() => onChange(n)}
            onKeyDown={(event) => {
              if (event.key === "ArrowRight" || event.key === "ArrowUp") onChange(Math.min(5, (value || 0) + 1));
              if (event.key === "ArrowLeft" || event.key === "ArrowDown") onChange(Math.max(1, (value || 2) - 1));
            }}
          >
            <Star fill={n <= value ? "currentColor" : "none"} />
          </button>
        ))}
      </div>
      <span className="small strong">{words[value] || "Pick a rating"}</span>
    </div>
  );
}

const HISTORY_TITLES = {
  PENDING: "Order placed",
  CONFIRMED: "Payment confirmed",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

/** Status history of an order as a vertical timeline, oldest first. */
export function OrderHistory({ history = [] }) {
  if (!history.length) return <p className="small muted">No changes yet.</p>;
  return (
    <ol className="timeline">
      {history.map((h, index) => {
        const title = HISTORY_TITLES[h.toStatus] || label(h.toStatus);
        return (
          <li key={`${h.changedAt}-${index}`} className="done">
            <div className="t-title">{title}</div>
            <div className="t-meta">
              {formatDate(h.changedAt)} by {h.changedBy}
            </div>
            {h.note && h.note !== title && <div className="small soft">{h.note}</div>}
          </li>
        );
      })}
    </ol>
  );
}
