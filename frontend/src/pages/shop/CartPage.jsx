import { useState } from "react";
import { CheckCircle2, ShoppingCart, Trash2, XCircle } from "lucide-react";
import { api } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { formatMoney } from "../../format.js";
import PaymentForm from "../../components/PaymentForm.jsx";
import { Alert, Empty, Field, ProductArt, Qty } from "../../components/ui.jsx";

/**
 * Cart -> checkout -> payment. The server splits the cart into one order per store, re-prices everything
 * and reserves the stock; then all orders are paid with one payment step.
 */
export default function CartPage({ user, cart, navigate, toast, refreshCounts }) {
  const { refresh } = useAuth();
  const [address, setAddress] = useState(() => localStorageGet("nexora_address"));
  const [phone, setPhone] = useState(user?.phone || "");
  const [checkout, setCheckout] = useState(null);
  const [results, setResults] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  const placeOrder = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      const response = await api("/api/orders", {
        method: "POST",
        body: { items: cart.items.map((i) => ({ productId: i.productId, quantity: i.quantity })), shippingAddress: address.trim(), phone: phone.trim() },
      });
      localStorageSet("nexora_address", address.trim());
      cart.clear();
      setCheckout(response);
      if (!user.phone && phone.trim()) refresh().catch(() => {});
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
    } finally {
      setBusy(false);
    }
  };

  /** Pays the given orders of this checkout with one payment method; earlier successful results are kept. */
  const payOrders = async (orderIds, payment) => {
    setBusy(true);
    setError("");
    try {
      const outcome = await api("/api/orders/pay", { method: "POST", body: { orderIds, payment } });
      setResults((previous) => [...(previous || []).filter((r) => !orderIds.includes(r.orderId)), ...outcome]);
      refreshCounts();
      if (outcome.every((r) => r.success)) toast.show(payment.method === "COD" ? "Order confirmed. Pay the courier on delivery." : "Payment successful");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  // ---------------------------------------------------------------- after checkout: pay / results
  if (checkout) {
    const failed = results?.filter((r) => !r.success) || [];
    return (
      <div className="page" style={{ maxWidth: 860 }}>
        <div>
          <h1>{results ? (failed.length ? "Some payments did not go through" : "Thank you, your order is confirmed") : "Payment"}</h1>
          <p className="muted">
            {results && !failed.length
              ? "The seller has been notified and will ship your order. Track it under My orders."
              : checkout.orders.length > 1
                ? `Your cart was split into ${checkout.orders.length} orders, one per store. Each store ships its own order.`
                : "Your order has been created."}{" "}
            {!results && "The items are reserved for you for 30 minutes."}
          </p>
        </div>
        <div className="card stack">
          {checkout.orders.map((order) => {
            const result = results?.find((r) => r.orderId === order.id);
            return (
              <div className="result-line" key={order.id}>
                {result ? result.success ? <CheckCircle2 size={18} className="pos" /> : <XCircle size={18} className="neg" /> : null}
                <div className="grow">
                  <strong>{order.merchantName}</strong>
                  <div className="muted small">
                    {order.orderNumber} - {order.items.reduce((n, i) => n + i.quantity, 0)} item(s)
                    {result && !result.success ? ` - ${result.message}` : ""}
                  </div>
                </div>
                <strong className="num">{formatMoney(order.totalAmount, order.currency)}</strong>
              </div>
            );
          })}
          <div className="summary-line total">
            <span>Total</span>
            <span>{formatMoney(checkout.grandTotal, checkout.currency)}</span>
          </div>
        </div>
        {!results || failed.length ? (
          <div className="card stack">
            <h3>{failed.length ? "Try another payment method" : "Choose how to pay"}</h3>
            <PaymentForm
              amount={(failed.length ? checkout.orders.filter((o) => failed.some((f) => f.orderId === o.id)) : checkout.orders).reduce(
                (sum, o) => sum + Number(o.totalAmount),
                0
              )}
              busy={busy}
              error={error}
              onClearError={() => setError("")}
              onSubmit={(payment) => payOrders(failed.length ? failed.map((f) => f.orderId) : checkout.orders.map((o) => o.id), payment)}
            />
          </div>
        ) : null}
        <div className="row">
          <button className="btn btn-primary" onClick={() => navigate("/orders")}>
            View my orders
          </button>
          <button className="btn" onClick={() => navigate("/shop")}>
            Continue shopping
          </button>
        </div>
      </div>
    );
  }

  // ---------------------------------------------------------------- the cart
  if (cart.items.length === 0)
    return (
      <Empty icon={ShoppingCart} title="Your cart is empty" action={<button className="btn btn-primary" onClick={() => navigate("/shop")}>Browse the marketplace</button>}>
        Add products from any store. You can check out items from several stores at once.
      </Empty>
    );

  return (
    <div className="stack" style={{ gap: 18 }}>
      <div>
        <h1>Your cart</h1>
        <p className="muted">
          {cart.count} item{cart.count === 1 ? "" : "s"} from {cart.groups.length} store{cart.groups.length === 1 ? "" : "s"}. Each store ships its own order.
        </p>
      </div>
      <div className="two-col">
        <div className="stack">
          {cart.groups.map((group) => (
            <section className="card cart-group" key={group.merchantId}>
              <div className="cart-group-head">
                <span>
                  Sold by{" "}
                  <a className="link" href={`#/store/${group.merchantId}`}>
                    {group.merchantName}
                  </a>
                </span>
                <span className="muted small num">Subtotal {formatMoney(group.subtotal)}</span>
              </div>
              {group.items.map((item) => (
                <div className="cart-line" key={item.productId}>
                  <ProductArt name={item.name} imageUrl={item.imageUrl} categoryId={item.categoryId} size="sm" />
                  <div style={{ minWidth: 0 }}>
                    <a className="link clamp" href={`#/product/${item.productId}`} style={{ color: "var(--text)" }}>
                      {item.name}
                    </a>
                    <div className="muted small num">{formatMoney(item.price, item.currency)} each</div>
                  </div>
                  <Qty small value={item.quantity} max={Math.min(item.available || 100, 100)} onChange={(n) => cart.update(item.productId, n)} />
                  <strong className="num line-total">{formatMoney(item.quantity * Number(item.price), item.currency)}</strong>
                  <button className="icon-btn danger" onClick={() => cart.remove(item.productId)} aria-label={`Remove ${item.name}`}>
                    <Trash2 size={16} />
                  </button>
                </div>
              ))}
            </section>
          ))}
        </div>

        <form className="card stack sticky" onSubmit={placeOrder}>
          <h3>Order summary</h3>
          <div className="summary-line">
            <span className="muted">Items ({cart.count})</span>
            <span className="num">{formatMoney(cart.total)}</span>
          </div>
          <div className="summary-line">
            <span className="muted">Delivery</span>
            <span>Free</span>
          </div>
          <div className="summary-line total">
            <span>Total</span>
            <span className="num">{formatMoney(cart.total)}</span>
          </div>
          {user ? (
            <>
              <Field label="Delivery address" error={fieldErrors.shippingAddress} hint="Building, street, area and emirate">
                <textarea rows={3} value={address} onChange={(e) => setAddress(e.target.value)} minLength={10} maxLength={500} required />
              </Field>
              <Field label="Mobile number for the courier" error={fieldErrors.phone}>
                <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+971 50 123 4567" />
              </Field>
              <Alert onClose={() => setError("")}>{error}</Alert>
              <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
                {busy ? "Placing order..." : "Place order and pay"}
              </button>
              <p className="muted small">Prices and stock are checked again when you place the order.</p>
            </>
          ) : (
            <>
              <p className="muted">Sign in or create a customer account to check out. Your cart is kept.</p>
              <a className="btn btn-primary btn-block btn-lg" href="#/login?next=%2Fcart">
                Sign in to check out
              </a>
              <a className="btn btn-block" href="#/register?next=%2Fcart">
                Create an account
              </a>
            </>
          )}
        </form>
      </div>
    </div>
  );
}

function localStorageGet(key) {
  try {
    return localStorage.getItem(key) || "";
  } catch {
    return "";
  }
}

function localStorageSet(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}
