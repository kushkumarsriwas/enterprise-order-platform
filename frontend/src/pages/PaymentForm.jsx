import { useState } from "react";
import { Banknote, CreditCard, Smartphone } from "lucide-react";
import { api } from "../api.js";
import { formatMoney } from "../format.js";
import { Alert, Field } from "../components/ui.jsx";

const METHODS = [
  { id: "CARD", label: "Card", icon: CreditCard },
  { id: "UPI", label: "UPI", icon: Smartphone },
  { id: "COD", label: "Cash on delivery", icon: Banknote },
];

export default function PaymentForm({ order, onPaid }) {
  const [method, setMethod] = useState("CARD");
  const [form, setForm] = useState({ cardNumber: "4111 1111 1111 1111", cardHolder: "", expiry: "12/30", cvv: "", upiId: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });

  const pay = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const body = method === "CARD"
        ? { method, cardNumber: form.cardNumber, cardHolder: form.cardHolder, expiry: form.expiry, cvv: form.cvv }
        : method === "UPI" ? { method, upiId: form.upiId } : { method };
      const result = await api(`/api/orders/${order.id}/pay`, { method: "POST", body });
      if (result.payment.status === "FAILED") setError(`Payment failed: ${result.payment.failureReason}`);
      onPaid(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card stack" onSubmit={pay}>
      <h3>Pay {formatMoney(order.totalAmount, order.currency)}</h3>
      <div className="method-picker">
        {METHODS.map(({ id, label, icon: Icon }) => (
          <button type="button" key={id} className={method === id ? "active" : ""} onClick={() => setMethod(id)}>
            <Icon size={16} /> {label}
          </button>
        ))}
      </div>
      {method === "CARD" && (
        <>
          <Field label="Card number" hint="Test cards: 4111 1111 1111 1111 approves, 4000 0000 0000 0002 is declined">
            <input value={form.cardNumber} onChange={set("cardNumber")} inputMode="numeric" required />
          </Field>
          <Field label="Name on card">
            <input value={form.cardHolder} onChange={set("cardHolder")} required />
          </Field>
          <div className="row">
            <Field label="Expiry (MM/YY)">
              <input value={form.expiry} onChange={set("expiry")} required />
            </Field>
            <Field label="CVV">
              <input value={form.cvv} onChange={set("cvv")} inputMode="numeric" maxLength={4} required />
            </Field>
          </div>
        </>
      )}
      {method === "UPI" && (
        <Field label="UPI ID" hint="Any name@bank approves; IDs starting with 'fail' are declined">
          <input value={form.upiId} onChange={set("upiId")} placeholder="name@bank" required />
        </Field>
      )}
      {method === "COD" && <p className="muted">Pay in cash when the order is delivered (orders up to AED 5,000).</p>}
      <Alert onClose={() => setError("")}>{error}</Alert>
      <button className="btn btn-primary btn-block" disabled={busy}>
        {busy ? "Processing..." : method === "COD" ? "Confirm order" : "Pay now"}
      </button>
      <p className="muted small">Card details are only used for this demo charge; the server stores a masked number only.</p>
    </form>
  );
}
