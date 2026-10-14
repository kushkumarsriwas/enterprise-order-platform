import { useState } from "react";
import { Minus, Plus, ShoppingCart, Trash2 } from "lucide-react";
import { api } from "../api.js";
import { formatMoney } from "../format.js";
import { Alert, Empty, Field } from "../components/ui.jsx";

export default function CartPage({ cart, navigate, toast }) {
  const [address, setAddress] = useState(() => localStorage.getItem("nexora_address") || "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (cart.items.length === 0) {
    return (
      <div className="page">
        <Empty icon={ShoppingCart} title="Your cart is empty">
          <button className="btn btn-primary" onClick={() => navigate("/catalog")}>Browse the catalogue</button>
        </Empty>
      </div>
    );
  }

  const placeOrder = async () => {
    setBusy(true);
    setError("");
    try {
      localStorage.setItem("nexora_address", address);
      const order = await api("/api/orders", {
        method: "POST",
        body: { shippingAddress: address, items: cart.items.map(({ productId, quantity }) => ({ productId, quantity })) },
      });
      cart.clear();
      toast.show(`Order ${order.orderNumber} placed - stock reserved for 30 minutes`);
      navigate(`/orders/${order.id}`);
    } catch (err) {
      setError(err.status === 400 && err.details?.shippingAddress ? "Please enter a full delivery address (10+ characters)." : err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <h1>Cart</h1>
      </div>
      <div className="two-col">
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Price</th>
                <th>Quantity</th>
                <th className="right">Total</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {cart.items.map((item) => (
                <tr key={item.productId}>
                  <td>
                    <strong>{item.name}</strong>
                    <div className="muted small">{item.sku}</div>
                  </td>
                  <td>{formatMoney(item.price, item.currency)}</td>
                  <td>
                    <div className="qty">
                      <button className="icon-btn" onClick={() => cart.update(item.productId, item.quantity - 1)} aria-label="Decrease">
                        <Minus size={14} />
                      </button>
                      <span>{item.quantity}</span>
                      <button
                        className="icon-btn"
                        disabled={item.quantity >= item.available}
                        onClick={() => cart.update(item.productId, item.quantity + 1)}
                        aria-label="Increase"
                      >
                        <Plus size={14} />
                      </button>
                    </div>
                  </td>
                  <td className="right">{formatMoney(item.quantity * item.price, item.currency)}</td>
                  <td className="right">
                    <button className="icon-btn danger" onClick={() => cart.update(item.productId, 0)} aria-label="Remove">
                      <Trash2 size={15} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <aside className="card stack">
          <h3>Checkout</h3>
          <Field label="Delivery address" hint="e.g. Office 12, Business Bay, Dubai, UAE">
            <textarea rows={3} value={address} onChange={(e) => setAddress(e.target.value)} />
          </Field>
          <div className="summary-line">
            <span>Items</span>
            <span>{cart.count}</span>
          </div>
          <div className="summary-line total">
            <span>Estimated total</span>
            <span>{formatMoney(cart.total)}</span>
          </div>
          <p className="muted small">The final price is confirmed by the server when the order is placed.</p>
          <Alert onClose={() => setError("")}>{error}</Alert>
          <button className="btn btn-primary btn-block" disabled={busy || address.trim().length < 10} onClick={placeOrder}>
            {busy ? "Reserving stock..." : "Place order"}
          </button>
        </aside>
      </div>
    </div>
  );
}
