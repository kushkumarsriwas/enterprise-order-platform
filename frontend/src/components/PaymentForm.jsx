import { useState } from "react";
import { Banknote, CreditCard, Smartphone } from "lucide-react";
import { formatMoney } from "../format.js";
import { Alert, Field } from "./ui.jsx";

const METHODS = [
  { id: "CARD", label: "Card", icon: CreditCard },
  { id: "UPI", label: "UPI", icon: Smartphone },
  { id: "COD", label: "Cash on delivery", icon: Banknote },
];

/**
 * Collects the payment method. The caller decides what to pay (one order, or every order of a checkout).
 * Test cards: 4111 1111 1111 1111 is approved, 4000 0000 0000 0002 is declined.
 */
export default function PaymentForm({ amount, onSubmit, busy, error, onClearError, submitLabel }) {
  const [method, setMethod] = useState("CARD");
  const [form, setForm] = useState({ cardNumber: "4111 1111 1111 1111", cardHolder: "", expiry: "12/30", cvv: "", upiId: "" });
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });

  const submit = (event) => {
    event.preventDefault();
    const payment =
      method === "CARD"
        ? { method, cardNumber: form.cardNumber, cardHolder: form.cardHolder, expiry: form.expiry, cvv: form.cvv }
        : method === "UPI"
          ? { method, upiId: form.upiId }
          : { method };
    onSubmit(payment);
  };

  return (
    <form className="stack" onSubmit={submit}>
      <div className="method-picker" role="radiogroup" aria-label="Payment method">
        {METHODS.map(({ id, label, icon: Icon }) => (
          <button type="button" key={id} role="radio" aria-checked={method === id} className={method === id ? "active" : ""} onClick={() => setMethod(id)}>
            <Icon size={18} /> {label}
          </button>
        ))}
      </div>
      {method === "CARD" && (
        <>
          <Field label="Card number" hint="Demo cards: 4111 1111 1111 1111 is approved, 4000 0000 0000 0002 is declined">
            <input value={form.cardNumber} onChange={set("cardNumber")} inputMode="numeric" autoComplete="cc-number" required />
          </Field>
          <Field label="Name on card">
            <input value={form.cardHolder} onChange={set("cardHolder")} autoComplete="cc-name" required />
          </Field>
          <div className="row">
            <Field label="Expiry (MM/YY)">
              <input value={form.expiry} onChange={set("expiry")} autoComplete="cc-exp" required />
            </Field>
            <Field label="CVV">
              <input value={form.cvv} onChange={set("cvv")} inputMode="numeric" maxLength={4} autoComplete="cc-csc" required />
            </Field>
          </div>
        </>
      )}
      {method === "UPI" && (
        <Field label="UPI ID" hint="Any name@bank is approved; IDs starting with 'fail' are declined">
          <input value={form.upiId} onChange={set("upiId")} placeholder="name@bank" required />
        </Field>
      )}
      {method === "COD" && <p className="muted">Pay the courier in cash when your order arrives (orders up to AED 5,000).</p>}
      <Alert onClose={onClearError}>{error}</Alert>
      <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
        {busy ? "Processing..." : submitLabel || (method === "COD" ? "Confirm order" : `Pay ${formatMoney(amount)}`)}
      </button>
      <p className="muted small">This is a demo payment. Card numbers are never stored: only the last four digits are kept.</p>
    </form>
  );
}
