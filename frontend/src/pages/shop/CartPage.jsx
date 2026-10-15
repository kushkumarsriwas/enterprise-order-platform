import { useEffect, useState } from "react";
import { CircleAlert, Info, ShoppingCart, Store, Trash2 } from "lucide-react";
import { api } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { useCart } from "../../cart.js";
import { Price, ProductImage } from "../../components/brand.jsx";
import { QuantityStepper } from "../../components/commerce.jsx";
import { Button, Empty, IconButton, Notice } from "../../components/ui.jsx";
import { formatMoney, formatNumber, plural } from "../../format.js";
import { useTitle } from "../../hooks.jsx";
import { WrongAccount } from "../NotFound.jsx";

/**
 * Checks every cart line against the marketplace: current price, stock and whether the product is still on sale.
 * Returns a map productId -> fresh product (or null when it is gone).
 */
export function useCartCheck(items) {
  const [fresh, setFresh] = useState({});
  const ids = items.map((item) => item.productId).join(",");
  useEffect(() => {
    let cancelled = false;
    if (!ids) return undefined;
    Promise.all(
      ids.split(",").map((id) =>
        api(`/api/products/${id}`, { auth: false })
          .then((product) => [id, product])
          .catch((error) => [id, error.status === 404 ? null : undefined]),
      ),
    ).then((entries) => {
      if (!cancelled) setFresh(Object.fromEntries(entries.filter(([, product]) => product !== undefined)));
    });
    return () => {
      cancelled = true;
    };
  }, [ids]);
  return fresh;
}

/** Problems that block checkout for one line: gone, sold out, or more than the store has. */
export function lineProblem(item, product) {
  if (product === null) return "No longer on sale";
  if (!product) return null;
  if (!product.inStock) return "Sold out";
  if (item.quantity > product.availableQuantity) return `Only ${formatNumber(product.availableQuantity)} available`;
  return null;
}

export function CartPage({ navigate }) {
  const { isSeller } = useAuth();
  const cart = useCart();
  const fresh = useCartCheck(cart.items);
  useTitle("Cart");

  // keep cart prices in line with the stores' current prices
  useEffect(() => {
    cart.items.forEach((item) => {
      const product = fresh[item.productId];
      if (product && (Number(product.price) !== Number(item.price) || product.availableQuantity !== item.available || product.imageUrl !== item.imageUrl)) {
        cart.refresh(product);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fresh]);

  if (isSeller) return <WrongAccount expected="customer" navigate={navigate} />;

  if (cart.items.length === 0) {
    return (
      <div className="panel">
        <Empty
          icon={ShoppingCart}
          title="Your cart is empty"
          action={
            <div className="row row-wrap" style={{ justifyContent: "center" }}>
              <Button variant="primary" onClick={() => navigate("/search?deals=true")}>
                See today's deals
              </Button>
              <Button onClick={() => navigate("/")}>Browse departments</Button>
            </div>
          }
        >
          Products you add from any store collect here. You check out once and every store ships its part.
        </Empty>
      </div>
    );
  }

  const problems = cart.items.filter((item) => lineProblem(item, fresh[item.productId]));
  const vat = (cart.total * 5) / 105;
  const stores = cart.groups.length;

  return (
    <div className="stack-lg">
      <div className="page-title">
        <div>
          <h1>Your cart</h1>
          <p>
            {plural(cart.count, "item")} from {plural(stores, "store")}
          </p>
        </div>
      </div>
      <div className="cart-layout">
        <div>
          {cart.groups.map((group) => (
            <section key={group.sellerId} className="cart-store" aria-label={`Items from ${group.sellerName}`}>
              <div className="cart-store-head">
                <span className="row" style={{ gap: 8 }}>
                  <Store size={16} />
                  <span>
                    Sold and shipped by{" "}
                    <a href={`#/store/${group.sellerId}`} className="strong">
                      {group.sellerName}
                    </a>
                  </span>
                </span>
                <span className="num strong">{formatMoney(group.subtotal)}</span>
              </div>
              {group.items.map((item) => {
                const product = fresh[item.productId];
                const problem = lineProblem(item, product);
                const max = Math.max(1, Math.min(product?.availableQuantity ?? item.available ?? 100, 100));
                return (
                  <div key={item.productId} className="cart-line">
                    <a href={`#/product/${item.productId}`} aria-label={item.name}>
                      <ProductImage product={{ ...item, id: item.productId }} iconSize={28} />
                    </a>
                    <div className="stack-sm" style={{ gap: 4 }}>
                      {item.brand && <span className="card-brand">{item.brand}</span>}
                      <a href={`#/product/${item.productId}`} className="strong clamp-2" style={{ color: "var(--ink)" }}>
                        {item.name}
                      </a>
                      <Price price={item.price} compareAtPrice={item.compareAtPrice} currency={item.currency} size="sm" />
                      {problem ? (
                        <span className="stock-note out">
                          <CircleAlert size={13} /> {problem}
                        </span>
                      ) : product?.fewLeft ? (
                        <span className="stock-note">Only {formatNumber(product.availableQuantity)} left</span>
                      ) : null}
                    </div>
                    <div className="stack-sm" style={{ alignItems: "flex-end", gap: 10 }}>
                      {product === null ? (
                        <Button size="sm" icon={Trash2} onClick={() => cart.remove(item.productId)}>
                          Remove
                        </Button>
                      ) : (
                        <div className="row" style={{ gap: 6 }}>
                          <QuantityStepper value={Math.min(item.quantity, max)} max={max} onChange={(quantity) => cart.update(item.productId, quantity)} />
                          <IconButton icon={Trash2} label={`Remove ${item.name}`} onClick={() => cart.remove(item.productId)} />
                        </div>
                      )}
                      <span className="num strong">{formatMoney(item.quantity * Number(item.price), item.currency)}</span>
                    </div>
                  </div>
                );
              })}
            </section>
          ))}
        </div>

        <aside className="summary-box" aria-label="Order summary">
          <h2 style={{ fontSize: "var(--text-lg)" }}>Summary</h2>
          <div className="summary-row">
            <span>Items ({formatNumber(cart.count)})</span>
            <span className="num">{formatMoney(cart.total)}</span>
          </div>
          <div className="summary-row">
            <span>Delivery</span>
            <span className="good strong">Free</span>
          </div>
          <div className="summary-row total">
            <span>Total</span>
            <span className="num">{formatMoney(cart.total)}</span>
          </div>
          <div className="summary-row tiny muted" style={{ marginTop: -6 }}>
            <span>Includes VAT (5%)</span>
            <span className="num">{formatMoney(vat)}</span>
          </div>
          {stores > 1 && (
            <Notice tone="info" icon={Info}>
              Your cart has products from {stores} stores, so you will get {stores} orders, each shipped and invoiced by its store. You pay once.
            </Notice>
          )}
          {problems.length > 0 && (
            <Notice tone="warn" icon={CircleAlert}>
              {problems.length === 1 ? "One item needs" : `${problems.length} items need`} your attention before checkout.
            </Notice>
          )}
          <Button variant="primary" size="lg" block disabled={problems.length > 0} onClick={() => navigate("/checkout")}>
            Go to checkout
          </Button>
          <Button variant="ghost" block onClick={() => navigate("/")}>
            Continue shopping
          </Button>
        </aside>
      </div>
    </div>
  );
}
