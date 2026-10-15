import { useEffect, useMemo, useState } from "react";
import { CircleAlert, CircleCheck, Clock, Info, Lock, PackageCheck, ShoppingCart, TriangleAlert } from "lucide-react";
import { api } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { useCart } from "../../cart.js";
import { ProductImage } from "../../components/brand.jsx";
import {
  AddressForm,
  PAYMENT_METHODS,
  PayOrderDialog,
  PaymentDetails,
  PaymentMethodPicker,
  addressBody,
  emptyAddress,
  paymentProblem,
} from "../../components/commerce.jsx";
import { Button, Checkbox, Empty, Loader, Notice, Panel, StatusBadge } from "../../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, plural } from "../../format.js";
import { useInterval, useLoader, useTitle } from "../../hooks.jsx";
import { lineProblem, useCartCheck } from "./CartPage.jsx";

const STEPS = [
  { id: "address", label: "Delivery" },
  { id: "payment", label: "Payment" },
  { id: "review", label: "Review and pay" },
];
const COD_LIMIT = 5000;
const RESULT_KEY = "nexora_checkout_result";
const PHONE = /^[+0-9 ()-]{7,30}$/;

function addressErrors(address) {
  const errors = {};
  if (!address.recipient?.trim()) errors.recipient = "Enter the name of the person receiving the order";
  if (!PHONE.test(address.phone?.trim() || "")) errors.phone = "Enter a mobile number, for example +971 50 123 4567";
  if (!address.line1?.trim()) errors.line1 = "Enter the building, street and area";
  if (!address.city?.trim()) errors.city = "Enter the city";
  return errors;
}

/** Groups checkout lines by store, like the server does when it creates one order per store. */
function byStore(lines) {
  const groups = new Map();
  lines.forEach((line) => {
    const group = groups.get(line.sellerId) || { sellerId: line.sellerId, sellerName: line.sellerName, lines: [], subtotal: 0 };
    group.lines.push(line);
    group.subtotal += line.quantity * Number(line.price);
    groups.set(line.sellerId, group);
  });
  return [...groups.values()];
}

/** The lines being bought: the whole cart, or one product with "Buy now". */
function useCheckoutLines(route) {
  const cart = useCart();
  const buyId = route.query.buy;
  const buyQty = Math.max(1, parseInt(route.query.qty, 10) || 1);
  const single = useLoader(() => (buyId ? api(`/api/products/${buyId}`, { auth: false }) : Promise.resolve(null)), [buyId]);
  const cartLines = cart.items;
  const fresh = useCartCheck(buyId ? [] : cartLines);
  if (buyId) {
    const p = single.data;
    const lines = p
      ? [
          {
            productId: p.id,
            name: p.name,
            brand: p.brand,
            price: p.price,
            currency: p.currency,
            sellerId: p.sellerId,
            sellerName: p.sellerName,
            categoryName: p.categoryName,
            imageUrl: p.imageUrl,
            quantity: Math.min(buyQty, p.availableQuantity),
          },
        ]
      : [];
    const problem = p && !p.inStock ? "This product has just sold out." : null;
    return { lines, loading: single.loading && !p, error: single.error, problems: problem ? [problem] : [], buyNow: true, cart };
  }
  const problems = cartLines
    .filter((item) => lineProblem(item, fresh[item.productId]))
    .map((item) => `${item.name}: ${lineProblem(item, fresh[item.productId])}`);
  return { lines: cartLines, loading: false, error: null, problems, buyNow: false, cart };
}

export function CheckoutPage({ route, navigate }) {
  useTitle("Checkout");
  const { user, refresh } = useAuth();
  const { lines, loading, error, problems, buyNow, cart } = useCheckoutLines(route);
  const [step, setStep] = useState("address");
  const [address, setAddress] = useState(() =>
    user?.address ? { ...emptyAddress, ...user.address } : { ...emptyAddress, recipient: user?.fullName || "", phone: user?.phone || "" },
  );
  const [saveAddress, setSaveAddress] = useState(!user?.address);
  const [errors, setErrors] = useState({});
  const [method, setMethod] = useState("CARD");
  const [details, setDetails] = useState({});
  const [paymentError, setPaymentError] = useState(null);
  const [placing, setPlacing] = useState(false);
  const [placeError, setPlaceError] = useState(null);

  // the saved address may have changed on another device
  useEffect(() => {
    refresh()
      .then((me) => {
        if (me?.address) setAddress((current) => (current.line1 ? current : { ...emptyAddress, ...me.address }));
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const groups = useMemo(() => byStore(lines), [lines]);
  const total = groups.reduce((sum, group) => sum + group.subtotal, 0);
  const units = lines.reduce((sum, line) => sum + line.quantity, 0);
  const codTooHigh = groups.filter((group) => group.subtotal > COD_LIMIT);
  const methodInfo = PAYMENT_METHODS.find((m) => m.value === method);
  const stepIndex = STEPS.findIndex((s) => s.id === step);

  if (loading) return <Loader text="Preparing checkout" />;
  if (error) {
    return (
      <div className="panel">
        <Empty
          icon={CircleAlert}
          title="This product can't be bought right now"
          action={<Button onClick={() => navigate("/")}>Back to the marketplace</Button>}
        >
          {error}
        </Empty>
      </div>
    );
  }
  if (lines.length === 0) {
    return (
      <div className="panel">
        <Empty
          icon={ShoppingCart}
          title="Nothing to check out"
          action={
            <Button variant="primary" onClick={() => navigate("/")}>
              Find something you like
            </Button>
          }
        >
          Your cart is empty.
        </Empty>
      </div>
    );
  }

  const continueToPayment = () => {
    const found = addressErrors(address);
    setErrors(found);
    if (Object.keys(found).length === 0) setStep("payment");
  };

  const continueToReview = () => {
    const problem = paymentProblem(method, details);
    if (problem) {
      setPaymentError(problem);
      return;
    }
    if (method === "COD" && codTooHigh.length > 0) {
      setPaymentError(
        `Cash on delivery is available for orders up to ${formatMoney(COD_LIMIT)} per store. Pick another method for ${codTooHigh.map((g) => g.sellerName).join(", ")}.`,
      );
      return;
    }
    setPaymentError(null);
    setStep("review");
  };

  const placeOrder = async () => {
    setPlacing(true);
    setPlaceError(null);
    let checkout;
    try {
      checkout = await api("/api/orders", {
        method: "POST",
        body: { items: lines.map((line) => ({ productId: line.productId, quantity: line.quantity })), address: addressBody(address) },
      });
    } catch (e) {
      setPlaceError(e.message);
      setPlacing(false);
      return;
    }
    // the orders exist now (stock is reserved for 30 minutes): these items leave the cart whatever the payment does
    if (!buyNow) cart.removeMany(lines.map((line) => line.productId));
    if (saveAddress)
      api("/api/users/me/address", { method: "PUT", body: addressBody(address) })
        .then(refresh)
        .catch(() => {});
    const ids = checkout.orders.map((order) => order.id);
    let results;
    try {
      results = await api("/api/payments/checkout", { method: "POST", body: { orderIds: ids, method, ...details } });
    } catch (e) {
      results = ids.map((orderId) => ({ orderId, success: false, message: e.message }));
    }
    try {
      sessionStorage.setItem(
        RESULT_KEY,
        JSON.stringify({ ids, method, results: results.map(({ orderId, success, message }) => ({ orderId, success, message })) }),
      );
    } catch {
      /* the result page reloads the orders anyway */
    }
    navigate(`/checkout/complete?orders=${ids.join(",")}`, { replace: true });
  };

  return (
    <div className="stack-lg">
      <div className="page-title">
        <div>
          <h1>Checkout</h1>
          <p>
            {plural(units, "item")} from {plural(groups.length, "store")}
            {buyNow && " (buy now: your cart stays as it is)"}
          </p>
        </div>
        <span className="row small muted" style={{ gap: 6 }}>
          <Lock size={15} /> Payments are processed securely
        </span>
      </div>

      <ol className="steps" aria-label="Checkout steps">
        {STEPS.map((s, index) => (
          <li key={s.id} className={index === stepIndex ? "current" : index < stepIndex ? "done" : ""} aria-current={index === stepIndex ? "step" : undefined}>
            <button type="button" disabled={index >= stepIndex} onClick={() => setStep(s.id)}>
              <span className="n">{index < stepIndex ? <CircleCheck size={14} /> : index + 1}</span>
              {s.label}
            </button>
          </li>
        ))}
      </ol>

      {problems.length > 0 && (
        <Notice tone="warn" icon={TriangleAlert}>
          <strong>Some items changed since you added them.</strong> {problems.join(". ")}.{" "}
          {!buyNow && (
            <a href="#/cart" className="strong">
              Review your cart
            </a>
          )}
        </Notice>
      )}

      <div className="cart-layout">
        <div className="stack">
          {step === "address" && (
            <Panel title="Where should we deliver?" sub="Every store in this order ships to this address.">
              <form
                className="stack"
                onSubmit={(event) => {
                  event.preventDefault();
                  continueToPayment();
                }}
              >
                <AddressForm value={address} onChange={setAddress} errors={errors} />
                <Checkbox checked={saveAddress} onChange={setSaveAddress}>
                  Save as my default address
                </Checkbox>
                <div>
                  <Button type="submit" variant="primary">
                    Continue to payment
                  </Button>
                </div>
              </form>
            </Panel>
          )}

          {step === "payment" && (
            <Panel title="How would you like to pay?" sub="One payment covers every store in this order.">
              <form
                className="stack"
                onSubmit={(event) => {
                  event.preventDefault();
                  continueToReview();
                }}
              >
                <PaymentMethodPicker
                  value={method}
                  onChange={(m) => {
                    setMethod(m);
                    setPaymentError(null);
                  }}
                />
                <PaymentDetails method={method} value={details} onChange={setDetails} />
                {method === "COD" && codTooHigh.length > 0 && (
                  <Notice tone="warn" icon={Info}>
                    Cash on delivery is available up to {formatMoney(COD_LIMIT)} per store.{" "}
                    {codTooHigh.map((g) => `${g.sellerName}: ${formatMoney(g.subtotal)}`).join(", ")}.
                  </Notice>
                )}
                {paymentError && (
                  <Notice tone="bad" icon={CircleAlert}>
                    {paymentError}
                  </Notice>
                )}
                <div className="row">
                  <Button type="submit" variant="primary">
                    Review your order
                  </Button>
                  <Button variant="ghost" onClick={() => setStep("address")}>
                    Back
                  </Button>
                </div>
              </form>
            </Panel>
          )}

          {step === "review" && (
            <>
              <Panel>
                <div className="stack">
                  <div className="review-block">
                    <div>
                      <h3>Delivery to</h3>
                      <div>{address.recipient}</div>
                      <div className="soft">{[address.line1, address.line2, address.city, address.region].filter(Boolean).join(", ")}</div>
                      <div className="soft">{address.phone}</div>
                    </div>
                    <Button size="sm" variant="ghost" onClick={() => setStep("address")}>
                      Change
                    </Button>
                  </div>
                  <hr />
                  <div className="review-block">
                    <div>
                      <h3>Payment</h3>
                      <div className="row" style={{ gap: 8 }}>
                        {methodInfo && <methodInfo.icon size={17} />}
                        {method === "CARD" && details.cardNumber ? `Card ending ${details.cardNumber.replace(/\D/g, "").slice(-4)}` : methodInfo?.label}
                        {method === "UPI" && details.upiId ? ` (${details.upiId})` : ""}
                      </div>
                    </div>
                    <Button size="sm" variant="ghost" onClick={() => setStep("payment")}>
                      Change
                    </Button>
                  </div>
                </div>
              </Panel>

              {groups.map((group, index) => (
                <section key={group.sellerId} className="cart-store" aria-label={`Order ${index + 1} from ${group.sellerName}`}>
                  <div className="cart-store-head">
                    <span>
                      Order {index + 1} of {groups.length}: shipped by <b>{group.sellerName}</b>
                    </span>
                    <span className="num strong">{formatMoney(group.subtotal)}</span>
                  </div>
                  {group.lines.map((line) => (
                    <div key={line.productId} className="cart-line">
                      <ProductImage product={{ ...line, id: line.productId }} iconSize={26} />
                      <div className="stack-sm" style={{ gap: 2 }}>
                        <span className="strong clamp-2">{line.name}</span>
                        <span className="small muted">
                          {formatNumber(line.quantity)} x {formatMoney(line.price, line.currency)}
                        </span>
                      </div>
                      <span className="num strong">{formatMoney(line.quantity * Number(line.price), line.currency)}</span>
                    </div>
                  ))}
                </section>
              ))}
              {placeError && (
                <Notice tone="bad" icon={CircleAlert}>
                  <strong>Your order was not placed.</strong> {placeError}
                </Notice>
              )}
            </>
          )}
        </div>

        <aside className="summary-box" aria-label="Order summary">
          <h2 style={{ fontSize: "var(--text-lg)" }}>Order summary</h2>
          <div className="mini-lines">
            {lines.map((line) => (
              <div key={line.productId} className="mini-line">
                <span className="thumb-wrap">
                  <ProductImage product={{ ...line, id: line.productId }} iconSize={18} />
                  <span className="qty-dot">{line.quantity}</span>
                </span>
                <span className="clamp-2">{line.name}</span>
                <span className="num">{formatMoney(line.quantity * Number(line.price), line.currency)}</span>
              </div>
            ))}
          </div>
          <hr />
          <div className="summary-row">
            <span>Items ({formatNumber(units)})</span>
            <span className="num">{formatMoney(total)}</span>
          </div>
          <div className="summary-row">
            <span>Delivery</span>
            <span className="good strong">Free</span>
          </div>
          <div className="summary-row total">
            <span>Total</span>
            <span className="num">{formatMoney(total)}</span>
          </div>
          <div className="summary-row tiny muted" style={{ marginTop: -6 }}>
            <span>Includes VAT (5%)</span>
            <span className="num">{formatMoney((total * 5) / 105)}</span>
          </div>
          {step === "review" ? (
            <Button variant="primary" size="lg" block icon={Lock} loading={placing} disabled={problems.length > 0} onClick={placeOrder}>
              {method === "COD" ? "Place order" : `Place order and pay ${formatMoney(total)}`}
            </Button>
          ) : (
            <Button variant="primary" size="lg" block onClick={step === "address" ? continueToPayment : continueToReview}>
              Continue
            </Button>
          )}
          <p className="tiny muted" style={{ margin: 0 }}>
            Stock is held for you for 30 minutes after you place the order. You can cancel free of charge until a store ships.
          </p>
        </aside>
      </div>
    </div>
  );
}

function readResult() {
  try {
    return JSON.parse(sessionStorage.getItem(RESULT_KEY) || "null");
  } catch {
    return null;
  }
}

/** Shown after checkout: one line per store order, with "Pay now" for any order whose payment did not go through. */
export function CheckoutCompletePage({ route, navigate }) {
  useTitle("Order placed");
  const ids = (route.query.orders || "").split(",").filter(Boolean);
  const [paying, setPaying] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const stored = readResult();
  const messages = Object.fromEntries((stored?.results || []).map((r) => [String(r.orderId), r.message]));
  const orders = useLoader(() => Promise.all(ids.map((id) => api(`/api/orders/${id}`))), [route.query.orders]);
  useInterval(() => setNow(Date.now()), 30000);

  if (ids.length === 0) return <Empty title="No order to show" action={<Button onClick={() => navigate("/account/orders")}>Your orders</Button>} />;
  if (orders.loading && !orders.data) return <Loader text="Confirming your order" />;
  if (orders.error && !orders.data) {
    return (
      <div className="panel">
        <Empty icon={CircleAlert} title="Your order was placed" action={<Button onClick={() => navigate("/account/orders")}>Go to your orders</Button>}>
          We could not load its details just now ({orders.error}). It is in your orders.
        </Empty>
      </div>
    );
  }

  const list = orders.data;
  const pending = list.filter((o) => o.status === "PENDING" && o.paymentDeadline && new Date(o.paymentDeadline).getTime() > now);
  const allGood = pending.length === 0 && list.every((o) => o.status !== "PENDING");
  const total = list.reduce((sum, o) => sum + Number(o.totalAmount), 0);

  return (
    <div className="stack-lg" style={{ maxWidth: 860 }}>
      <div className="panel">
        <div className="panel-body done-hero">
          <span className={`done-icon ${allGood ? "" : "warn"}`}>{allGood ? <PackageCheck /> : <Clock />}</span>
          <div className="grow">
            <h1 style={{ fontSize: "var(--text-xl)" }}>{allGood ? "Thank you, your order is confirmed" : "Your order is placed: payment needed"}</h1>
            <p className="soft" style={{ marginTop: 6 }}>
              {allGood
                ? `${plural(list.length, "store")} will ship your items. We e-mailed a confirmation and you can follow every order from your account.`
                : `We are holding the stock for you. Pay before the time shown, or the ${pending.length === 1 ? "order is" : "orders are"} cancelled automatically.`}
            </p>
            <div className="row row-wrap" style={{ marginTop: 14 }}>
              {pending.length > 0 && (
                <Button variant="primary" onClick={() => setPaying(true)}>
                  Pay {formatMoney(pending.reduce((s, o) => s + Number(o.totalAmount), 0))} now
                </Button>
              )}
              <Button onClick={() => navigate("/account/orders")}>Go to your orders</Button>
              <Button variant="ghost" onClick={() => navigate("/")}>
                Continue shopping
              </Button>
            </div>
          </div>
        </div>
      </div>

      <div>
        {list.map((order) => {
          const needsPayment = pending.includes(order);
          return (
            <div key={order.id} className={`order-result ${needsPayment ? "attention" : ""}`}>
              <div className="stack-sm" style={{ gap: 4 }}>
                <div className="row row-wrap" style={{ gap: 10 }}>
                  <a href={`#/account/orders/${order.id}`} className="strong">
                    Order {order.orderNumber}
                  </a>
                  <StatusBadge status={order.status} />
                  {order.paymentState !== "UNPAID" && <StatusBadge status={order.paymentState} />}
                </div>
                <span className="small soft">
                  {plural(
                    order.items.reduce((s, i) => s + i.quantity, 0),
                    "item",
                  )}{" "}
                  from {order.sellerName}
                </span>
                {needsPayment && (
                  <span className="small warn strong">
                    {messages[String(order.id)] ? `${messages[String(order.id)]} ` : "Payment not received. "}
                    Pay by {formatDate(order.paymentDeadline)}.
                  </span>
                )}
                {order.status === "CANCELLED" && <span className="small muted">{order.cancelReason}</span>}
              </div>
              <span className="num strong">{formatMoney(order.totalAmount, order.currency)}</span>
            </div>
          );
        })}
        {list.length > 1 && (
          <div className="summary-row total" style={{ padding: "14px 4px 0", border: 0 }}>
            <span>Total for {plural(list.length, "order")}</span>
            <span className="num">{formatMoney(total)}</span>
          </div>
        )}
      </div>

      {paying && (
        <PayOrderDialog
          open
          orders={pending}
          onClose={() => setPaying(false)}
          onDone={() => {
            setPaying(false);
            orders.reload();
          }}
        />
      )}
    </div>
  );
}
