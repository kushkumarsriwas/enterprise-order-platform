import { useState } from "react";
import { Banknote, CircleAlert, CreditCard, Minus, Plus, Smartphone } from "lucide-react";
import { api } from "../api.js";
import { useAuth } from "../auth.jsx";
import { useCart } from "../cart.js";
import { EMIRATES, formatMoney, formatNumber } from "../format.js";
import { useToast } from "../hooks.jsx";
import { Price, ProductImage } from "./brand.jsx";
import { Badge, Button, Field, Input, Modal, Notice, Select, Stars, Textarea } from "./ui.jsx";

export function ProductCard({ product, showSeller = true }) {
  const cart = useCart();
  const toast = useToast();
  const { isSeller } = useAuth();
  const inCart = cart.quantityOf(product.id);
  const soldOut = !product.inStock;
  return (
    <article className="product-card">
      <div className="card-badges">{product.discountPercent > 0 && <Badge tone="solid-bloom">-{product.discountPercent}%</Badge>}</div>
      <ProductImage product={product} />
      <div className="stack-sm" style={{ gap: 4 }}>
        {product.brand && <span className="card-brand">{product.brand}</span>}
        <a className="card-link" href={`#/product/${product.id}`}>
          <h3 className="card-name clamp-2">{product.name}</h3>
        </a>
        <div className="card-meta">
          {product.ratingCount > 0 ? <Stars value={product.ratingAverage} count={product.ratingCount} /> : <span>No reviews yet</span>}
        </div>
      </div>
      <div className="card-foot">
        <Price price={product.price} compareAtPrice={product.compareAtPrice} currency={product.currency} />
        {!isSeller && (
          <button
            type="button"
            className={`add-btn ${inCart ? "in-cart" : ""}`}
            disabled={soldOut || inCart >= product.availableQuantity}
            aria-label={inCart ? `${product.name}: ${inCart} in your cart, add one more` : `Add ${product.name} to your cart`}
            title={inCart ? `${inCart} in your cart` : "Add to cart"}
            onClick={() => {
              cart.add(product, 1);
              toast(`Added ${product.name} to your cart`);
            }}
          >
            {inCart ? formatNumber(inCart) : <Plus />}
          </button>
        )}
      </div>
      <div className="card-sub">
        {soldOut ? (
          <span className="stock-note out">Out of stock</span>
        ) : product.fewLeft ? (
          <span className="stock-note">Only {formatNumber(product.availableQuantity)} left</span>
        ) : showSeller ? (
          <span className="ellipsis">by {product.sellerName}</span>
        ) : null}
      </div>
    </article>
  );
}

/** A titled row of product cards (home page, product page, store page). Renders nothing when empty. */
export function ProductShelf({ title, sub, products, link, linkLabel = "See all" }) {
  if (!products?.length) return null;
  return (
    <section className="shelf" aria-label={title}>
      <div className="shelf-head">
        <div>
          <h2>{title}</h2>
          {sub && <p>{sub}</p>}
        </div>
        {link && <a href={link}>{linkLabel}</a>}
      </div>
      <div className="shelf-row">
        {products.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
    </section>
  );
}

export function QuantityStepper({ value, max = 100, onChange, label = "Quantity" }) {
  const clamp = (n) => Math.max(1, Math.min(max, n || 1));
  return (
    <div className="qty" role="group" aria-label={label}>
      <button type="button" aria-label="One less" onClick={() => onChange(clamp(value - 1))} disabled={value <= 1}>
        <Minus />
      </button>
      <input aria-label={label} inputMode="numeric" value={value} onChange={(event) => onChange(clamp(parseInt(event.target.value, 10)))} />
      <button type="button" aria-label="One more" onClick={() => onChange(clamp(value + 1))} disabled={value >= max}>
        <Plus />
      </button>
    </div>
  );
}

export const emptyAddress = { recipient: "", phone: "", line1: "", line2: "", city: "", region: "Dubai", country: "United Arab Emirates" };

/** Only the fields the API accepts for a delivery address (a saved address also carries "formatted"). */
export const addressBody = (address = {}) => ({
  recipient: address.recipient?.trim() || "",
  phone: address.phone?.trim() || "",
  line1: address.line1?.trim() || "",
  line2: address.line2?.trim() || null,
  city: address.city?.trim() || "",
  region: address.region || "Dubai",
  country: address.country || "United Arab Emirates",
});

export function AddressForm({ value, onChange, errors = {} }) {
  const set = (key) => (event) => onChange({ ...value, [key]: event.target.value });
  return (
    <div className="form-grid">
      <Field label="Full name" error={errors.recipient}>
        <Input value={value.recipient} onChange={set("recipient")} autoComplete="name" required />
      </Field>
      <Field label="Mobile number" hint="The courier calls this number" error={errors.phone}>
        <Input value={value.phone} onChange={set("phone")} autoComplete="tel" placeholder="+971 50 123 4567" required />
      </Field>
      <Field label="Building, street and area" className="span-2" error={errors.line1}>
        <Input value={value.line1} onChange={set("line1")} autoComplete="address-line1" placeholder="Marina Walk, Tower 3, Dubai Marina" required />
      </Field>
      <Field label="Apartment or villa (optional)" className="span-2">
        <Input value={value.line2 || ""} onChange={set("line2")} autoComplete="address-line2" placeholder="Apartment 1204" />
      </Field>
      <Field label="City" error={errors.city}>
        <Input value={value.city} onChange={set("city")} autoComplete="address-level2" required />
      </Field>
      <Field label="Emirate">
        <Select value={value.region} onChange={set("region")} options={EMIRATES.map((e) => ({ value: e, label: e }))} />
      </Field>
    </div>
  );
}

export const PAYMENT_METHODS = [
  { value: "CARD", label: "Card", hint: "Visa, Mastercard, Amex", icon: CreditCard },
  { value: "UPI", label: "Instant bank transfer", hint: "Pay with your bank payment ID", icon: Smartphone },
  { value: "COD", label: "Cash on delivery", hint: "Up to AED 5,000", icon: Banknote },
];

export function PaymentMethodPicker({ value, onChange }) {
  return (
    <div className="method-options" role="radiogroup" aria-label="Payment method">
      {PAYMENT_METHODS.map((method) => (
        <button key={method.value} type="button" className="method-option" aria-pressed={value === method.value} onClick={() => onChange(method.value)}>
          <method.icon />
          <b>{method.label}</b>
          <small>{method.hint}</small>
        </button>
      ))}
    </div>
  );
}

const groupCardNumber = (value) =>
  value
    .replace(/\D/g, "")
    .slice(0, 19)
    .replace(/(\d{4})(?=\d)/g, "$1 ");

const formatExpiry = (value, previous = "") => {
  const digits = value.replace(/\D/g, "").slice(0, 4);
  if (digits.length <= 2) return value.length < previous.length ? digits : digits.length === 2 ? `${digits}/` : digits;
  return `${digits.slice(0, 2)}/${digits.slice(2)}`;
};

function luhn(digits) {
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let digit = Number(digits[i]);
    if (double) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    double = !double;
  }
  return sum % 10 === 0;
}

/** The same checks the payment service makes, so mistakes show up before anything is sent. */
export function paymentProblem(method, details = {}) {
  if (method === "CARD") {
    const number = (details.cardNumber || "").replace(/[\s-]/g, "");
    if (!/^\d{13,19}$/.test(number) || !luhn(number)) return "Enter a valid card number.";
    if (!details.cardHolder?.trim()) return "Enter the name on the card.";
    const expiry = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec((details.expiry || "").trim());
    if (!expiry) return "Enter the expiry date as MM/YY.";
    const now = new Date();
    const year = 2000 + Number(expiry[2]);
    if (year < now.getFullYear() || (year === now.getFullYear() && Number(expiry[1]) < now.getMonth() + 1)) return "This card has expired.";
    if (!/^\d{3,4}$/.test(details.cvv || "")) return "Enter the 3 or 4 digit security code.";
  }
  if (method === "UPI" && !/^[a-z0-9._-]{2,64}@[a-z]{2,32}$/.test((details.upiId || "").trim().toLowerCase())) {
    return "Enter your payment ID like name@bank.";
  }
  return null;
}

export function PaymentDetails({ method, value, onChange }) {
  const set = (key, format) => (event) => onChange({ ...value, [key]: format ? format(event.target.value, value[key] || "") : event.target.value });
  if (method === "CARD") {
    return (
      <div className="form-grid">
        <Field label="Card number" className="span-2" hint="Test cards: 4111 1111 1111 1111 is approved, 4000 0000 0000 0002 is declined">
          <Input
            value={value.cardNumber || ""}
            onChange={set("cardNumber", groupCardNumber)}
            inputMode="numeric"
            autoComplete="cc-number"
            placeholder="1234 5678 9012 3456"
          />
        </Field>
        <Field label="Name on card" className="span-2">
          <Input value={value.cardHolder || ""} onChange={set("cardHolder")} autoComplete="cc-name" />
        </Field>
        <Field label="Expiry (MM/YY)">
          <Input
            value={value.expiry || ""}
            onChange={set("expiry", formatExpiry)}
            inputMode="numeric"
            autoComplete="cc-exp"
            placeholder="08/29"
            maxLength={5}
          />
        </Field>
        <Field label="Security code">
          <Input
            value={value.cvv || ""}
            onChange={set("cvv", (v) => v.replace(/\D/g, "").slice(0, 4))}
            inputMode="numeric"
            autoComplete="cc-csc"
            placeholder="123"
          />
        </Field>
      </div>
    );
  }
  if (method === "UPI") {
    return (
      <Field label="Payment ID" hint="Like name@bank. In this demo, IDs that start with 'fail' are declined.">
        <Input value={value.upiId || ""} onChange={set("upiId")} placeholder="kush@okbank" autoCapitalize="none" spellCheck={false} />
      </Field>
    );
  }
  return <p className="small soft">Pay the courier in cash when the order arrives. Available for orders up to AED 5,000 per store.</p>;
}

/** Pay (again) for orders that are waiting for payment, with one method for all of them, like at checkout. */
export function PayOrderDialog({ open, orders, onClose, onDone }) {
  const toast = useToast();
  const [method, setMethod] = useState("CARD");
  const [details, setDetails] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const total = orders.reduce((sum, order) => sum + Number(order.totalAmount), 0);
  const pay = async () => {
    const problem = paymentProblem(method, details);
    if (problem) {
      setError(problem);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const results = await api("/api/payments/checkout", { method: "POST", body: { orderIds: orders.map((o) => o.id), method, ...details } });
      const failed = results.filter((r) => !r.success);
      if (failed.length === 0) {
        toast(orders.length > 1 ? "Payment received. Your orders are confirmed." : "Payment received. Your order is confirmed.");
        setDetails({});
        onDone(results);
      } else {
        setError(
          failed
            .map((r) => r.message)
            .filter(Boolean)
            .join(" ") || "The payment did not go through.",
        );
        if (failed.length < results.length) onDone(results);
      }
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
      title={orders.length > 1 ? `Pay for ${orders.length} orders` : `Pay for order ${orders[0]?.orderNumber || ""}`}
      sub={`Total ${formatMoney(total)}, VAT included`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Not now
          </Button>
          <Button variant="primary" loading={busy} onClick={pay}>
            {method === "COD" ? "Confirm cash on delivery" : `Pay ${formatMoney(total)}`}
          </Button>
        </>
      }
    >
      <div className="stack">
        <PaymentMethodPicker
          value={method}
          onChange={(m) => {
            setMethod(m);
            setError(null);
          }}
        />
        <PaymentDetails method={method} value={details} onChange={setDetails} />
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

/** "Contact seller" from a product, a store or an order: opens (or continues) the conversation with that store. */
export function ContactSeller({ open, onClose, sellerId, sellerName, product, orderNumber }) {
  const toast = useToast();
  const [message, setMessage] = useState(
    product ? `Hello, I have a question about ${product.name}: ` : orderNumber ? `Hello, about my order ${orderNumber}: ` : "",
  );
  const [busy, setBusy] = useState(false);
  const send = async () => {
    setBusy(true);
    try {
      const result = await api("/api/conversations", {
        method: "POST",
        body: { sellerId, message, productId: product?.id, productName: product?.name, orderNumber },
      });
      toast(`Message sent to ${sellerName}`);
      onClose();
      window.location.hash = `/account/messages?c=${result.conversation.id}`;
    } catch (error) {
      toast(error.message, "bad");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Message ${sellerName}`}
      sub="The store usually answers within a day. Never pay outside Nexora."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} disabled={!message.trim()} onClick={send}>
            Send message
          </Button>
        </>
      }
    >
      <Field label="Your message">
        <Textarea value={message} onChange={(event) => setMessage(event.target.value)} rows={5} maxLength={2000} />
      </Field>
    </Modal>
  );
}
