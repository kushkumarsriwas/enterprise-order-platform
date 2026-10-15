import { useState } from "react";
import { ArrowLeft, CircleAlert, Clock, FileText, MessageSquare, Package, Star, Truck, Wallet, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { ProductImage } from "../../components/brand.jsx";
import { ContactSeller, PayOrderDialog } from "../../components/commerce.jsx";
import { CancelOrderDialog, OrderHistory, OrderTracker } from "../../components/orders.jsx";
import { Button, DownloadButton, Empty, ErrorState, Loader, Notice, Pagination, Panel, StatusBadge, Tabs } from "../../components/ui.jsx";
import { deliveryAddress, formatDate, formatDay, formatMoney, formatNumber, label, plural } from "../../format.js";
import { useInterval, useLoader, useTitle } from "../../hooks.jsx";

const TABS = [
  { id: "", label: "All orders" },
  { id: "PENDING", label: "Awaiting payment" },
  { id: "CONFIRMED", label: "Preparing" },
  { id: "SHIPPED", label: "On the way" },
  { id: "DELIVERED", label: "Delivered" },
  { id: "CANCELLED", label: "Cancelled" },
];
const PAGE_SIZE = 10;
const METHOD_LABELS = { CARD: "Card", UPI: "Instant bank transfer", COD: "Cash on delivery" };

const cancellable = (order) => order.status === "PENDING" || order.status === "CONFIRMED";
const payable = (order) => order.status === "PENDING" && order.paymentDeadline && new Date(order.paymentDeadline) > new Date();
const units = (order) => order.items.reduce((sum, item) => sum + item.quantity, 0);

/** One line under the order header that says what happens next. */
function NextStep({ order }) {
  if (payable(order)) {
    return (
      <span className="row warn strong small" style={{ gap: 6 }}>
        <Clock size={15} /> Pay by {formatDate(order.paymentDeadline)} or the order is cancelled
      </span>
    );
  }
  if (order.status === "CONFIRMED") return <span className="small soft">{order.sellerName} is preparing your order</span>;
  if (order.status === "SHIPPED") {
    return (
      <span className="row small soft" style={{ gap: 6 }}>
        <Truck size={15} /> Shipped {formatDay(order.shippedAt)} with {order.carrier}, tracking {order.trackingNumber}
      </span>
    );
  }
  if (order.status === "DELIVERED") return <span className="small good strong">Delivered {formatDay(order.deliveredAt)}</span>;
  if (order.status === "CANCELLED") return <span className="small muted">{order.cancelReason || "Cancelled"}</span>;
  return null;
}

export function CustomerOrdersPage({ route, navigate }) {
  useTitle("Your orders");
  const status = route.query.status || "";
  const page = Number(route.query.page || 0);
  const orders = useLoader(() => api(`/api/orders${qs({ status, page, size: PAGE_SIZE })}`), [status, page]);
  const toReview = useLoader(() => api("/api/reviews/pending").catch(() => []), []);
  const reviewable = new Set((toReview.data || []).map((item) => item.orderId));
  const [paying, setPaying] = useState(null);
  const [cancelling, setCancelling] = useState(null);
  const [contact, setContact] = useState(null);

  const go = (changes) => navigate(`/account/orders${qs({ status, page, ...changes })}`, { keepScroll: true });

  return (
    <div className="stack">
      <div className="page-title">
        <div>
          <h1>Your orders</h1>
          <p>Each store ships and invoices its own order.</p>
        </div>
      </div>
      <Tabs tabs={TABS} value={status} onChange={(id) => go({ status: id, page: 0 })} />
      {orders.loading && !orders.data ? (
        <Loader text="Loading your orders" />
      ) : orders.error && !orders.data ? (
        <ErrorState message={orders.error} onRetry={orders.reload} />
      ) : orders.data.content.length === 0 ? (
        <div className="panel">
          <Empty
            icon={Package}
            title={status ? `No orders ${TABS.find((t) => t.id === status)?.label.toLowerCase()}` : "No orders yet"}
            action={
              !status && (
                <Button variant="primary" onClick={() => navigate("/")}>
                  Start shopping
                </Button>
              )
            }
          >
            {status ? "Orders appear here when they reach this stage." : "When you check out, every store's order appears here."}
          </Empty>
        </div>
      ) : (
        <div style={{ opacity: orders.loading ? 0.6 : 1 }}>
          {orders.data.content.map((order) => (
            <article key={order.id} className="order-card">
              <div className="order-card-head">
                <span>
                  Order placed
                  <b>{formatDay(order.createdAt)}</b>
                </span>
                <span>
                  Total
                  <b>{formatMoney(order.totalAmount, order.currency)}</b>
                </span>
                <span>
                  Ship to
                  <b>{order.recipientName}</b>
                </span>
                <span>
                  Sold by
                  <b>
                    <a href={`#/store/${order.sellerId}`}>{order.sellerName}</a>
                  </b>
                </span>
                <span style={{ marginLeft: "auto", textAlign: "right" }}>
                  Order {order.orderNumber}
                  <b>
                    <a href={`#/account/orders/${order.id}`}>View details</a>
                  </b>
                </span>
              </div>
              <div className="order-card-body">
                <div className="stack-sm" style={{ gap: 8, minWidth: 0, flex: 1 }}>
                  <div className="row row-wrap" style={{ gap: 8 }}>
                    <StatusBadge status={order.status} />
                    {order.paymentState === "REFUNDED" && <StatusBadge status="REFUNDED" />}
                  </div>
                  <NextStep order={order} />
                  <div className="row" style={{ gap: 12, minWidth: 0 }}>
                    <div className="order-thumbs">
                      {order.items.slice(0, 4).map((item) => (
                        <a key={item.productId} href={`#/product/${item.productId}`} title={item.productName}>
                          <ProductImage product={{ id: item.productId, name: item.productName, categoryName: item.categoryName }} iconSize={20} />
                        </a>
                      ))}
                    </div>
                    <div className="small soft grow">
                      <div className="ellipsis">{order.items.map((i) => i.productName).join(", ")}</div>
                      <div className="muted">{plural(units(order), "item")}</div>
                    </div>
                  </div>
                </div>
                <div className="stack-sm" style={{ minWidth: 190 }}>
                  {payable(order) && (
                    <Button variant="primary" icon={Wallet} onClick={() => setPaying(order)}>
                      Pay now
                    </Button>
                  )}
                  {order.status === "DELIVERED" && reviewable.has(order.id) && (
                    <Button icon={Star} onClick={() => navigate("/account/reviews")}>
                      Review products
                    </Button>
                  )}
                  {order.invoiceAvailable && (
                    <DownloadButton path={`/api/orders/${order.id}/invoice`} icon={FileText}>
                      Invoice
                    </DownloadButton>
                  )}
                  <Button variant="ghost" icon={MessageSquare} onClick={() => setContact(order)}>
                    Message the store
                  </Button>
                  {cancellable(order) && (
                    <Button variant="ghost" icon={XCircle} onClick={() => setCancelling(order)}>
                      Cancel order
                    </Button>
                  )}
                </div>
              </div>
            </article>
          ))}
          <div className="panel" style={{ marginTop: 14 }}>
            <Pagination
              page={page}
              totalPages={orders.data.totalPages}
              totalElements={orders.data.totalElements}
              size={PAGE_SIZE}
              onPage={(p) => go({ page: p })}
            />
          </div>
        </div>
      )}

      {paying && (
        <PayOrderDialog
          open
          orders={[paying]}
          onClose={() => setPaying(null)}
          onDone={() => {
            setPaying(null);
            orders.reload();
          }}
        />
      )}
      {cancelling && (
        <CancelOrderDialog
          open
          order={cancelling}
          path={`/api/orders/${cancelling.id}/cancel`}
          onClose={() => setCancelling(null)}
          onDone={() => {
            setCancelling(null);
            orders.reload();
          }}
        />
      )}
      {contact && (
        <ContactSeller open onClose={() => setContact(null)} sellerId={contact.sellerId} sellerName={contact.sellerName} orderNumber={contact.orderNumber} />
      )}
    </div>
  );
}

export function CustomerOrderPage({ id, navigate }) {
  const order = useLoader(() => api(`/api/orders/${id}`), [id]);
  const payments = useLoader(() => api(`/api/payments/order/${id}`).catch(() => []), [id]);
  const [paying, setPaying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [contact, setContact] = useState(false);
  useTitle(order.data ? `Order ${order.data.orderNumber}` : "Order");
  // a pending order may be paid or expire while the page is open
  useInterval(() => order.reload(), 30000, order.data?.status === "PENDING" || order.data?.status === "CONFIRMED");

  if (order.loading && !order.data) return <Loader text="Loading the order" />;
  if (order.error && !order.data) return <ErrorState message={order.error} onRetry={order.reload} />;

  const o = order.data;
  const delivery = deliveryAddress(o);
  const subtotal = o.items.reduce((sum, item) => sum + Number(item.lineTotal), 0);
  const lastFailure = (payments.data || []).find((p) => p.status === "FAILED");
  const successful = (payments.data || []).find((p) => p.status === "SUCCESS" || p.status === "PENDING" || p.status === "REFUNDED");

  return (
    <div className="stack">
      <div>
        <Button variant="ghost" size="sm" icon={ArrowLeft} onClick={() => navigate("/account/orders")}>
          Your orders
        </Button>
      </div>
      <div className="page-title">
        <div>
          <h1>Order {o.orderNumber}</h1>
          <p>
            Placed {formatDate(o.createdAt)} with <a href={`#/store/${o.sellerId}`}>{o.sellerName}</a>
          </p>
        </div>
        <div className="row row-wrap">
          {o.invoiceAvailable && (
            <DownloadButton path={`/api/orders/${o.id}/invoice`} icon={FileText}>
              Tax invoice
            </DownloadButton>
          )}
          <Button icon={MessageSquare} onClick={() => setContact(true)}>
            Message the store
          </Button>
          {cancellable(o) && (
            <Button variant="danger" icon={XCircle} onClick={() => setCancelling(true)}>
              Cancel order
            </Button>
          )}
        </div>
      </div>

      {payable(o) && (
        <Notice tone="warn" icon={Clock}>
          <div className="row-between row-wrap" style={{ gap: 12 }}>
            <span>
              <strong>Waiting for payment.</strong> Pay by {formatDate(o.paymentDeadline)} or the order is cancelled and the stock released.
              {lastFailure?.failureReason && <span className="small"> Last attempt: {lastFailure.failureReason}.</span>}
            </span>
            <Button variant="primary" icon={Wallet} onClick={() => setPaying(true)}>
              Pay {formatMoney(o.totalAmount, o.currency)}
            </Button>
          </div>
        </Notice>
      )}

      <Panel>
        <OrderTracker order={o} />
      </Panel>

      <div className="detail-grid">
        <Panel title="Items" flush>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Price</th>
                  <th className="num">Qty</th>
                  <th className="num">Total</th>
                </tr>
              </thead>
              <tbody>
                {o.items.map((item) => (
                  <tr key={item.productId}>
                    <td>
                      <div className="product-cell">
                        <span className="thumb">
                          <ProductImage product={{ id: item.productId, name: item.productName, categoryName: item.categoryName }} iconSize={18} />
                        </span>
                        <div>
                          <a href={`#/product/${item.productId}`} className="cell-title">
                            {item.productName}
                          </a>
                          <div className="cell-sub">{item.categoryName}</div>
                        </div>
                      </div>
                    </td>
                    <td className="num">{formatMoney(item.unitPrice, o.currency)}</td>
                    <td className="num">{formatNumber(item.quantity)}</td>
                    <td className="num strong">{formatMoney(item.lineTotal, o.currency)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}>Items</td>
                  <td className="num">{formatMoney(subtotal, o.currency)}</td>
                </tr>
                <tr>
                  <td colSpan={3}>Delivery</td>
                  <td className="num good">Free</td>
                </tr>
                <tr>
                  <td colSpan={3}>
                    Total <span className="muted small">(includes VAT {formatMoney(o.vatAmount, o.currency)})</span>
                  </td>
                  <td className="num">{formatMoney(o.totalAmount, o.currency)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </Panel>

        <div className="stack">
          <Panel title="Delivery">
            <dl className="dl">
              <dt>Recipient</dt>
              <dd>{o.recipientName}</dd>
              <dt>Address</dt>
              <dd>{delivery.address}</dd>
              {delivery.phone && (
                <>
                  <dt>Phone</dt>
                  <dd>{delivery.phone}</dd>
                </>
              )}
              {o.carrier && (
                <>
                  <dt>Carrier</dt>
                  <dd>{o.carrier}</dd>
                  <dt>Tracking</dt>
                  <dd className="code">{o.trackingNumber}</dd>
                </>
              )}
            </dl>
          </Panel>
          <Panel title="Payment">
            <dl className="dl">
              <dt>Method</dt>
              <dd>{METHOD_LABELS[o.paymentMethod] || (o.paymentMethod ? label(o.paymentMethod) : "Not paid yet")}</dd>
              {successful?.maskedInstrument && o.paymentMethod !== "COD" && (
                <>
                  <dt>Paid with</dt>
                  <dd>{successful.maskedInstrument}</dd>
                </>
              )}
              <dt>Status</dt>
              <dd>
                <StatusBadge status={o.paymentState} />
              </dd>
              {successful?.transactionReference && (
                <>
                  <dt>Reference</dt>
                  <dd className="code">{successful.transactionReference}</dd>
                </>
              )}
            </dl>
            {o.paymentState === "PAY_ON_DELIVERY" && (
              <p className="small soft" style={{ marginTop: 12 }}>
                Have {formatMoney(o.totalAmount, o.currency)} ready for the courier.
              </p>
            )}
          </Panel>
          <Panel title="History">
            <OrderHistory history={o.history} />
          </Panel>
        </div>
      </div>

      {o.status === "DELIVERED" && (
        <Notice tone="good" icon={Star}>
          How was it? <a href="#/account/reviews">Review the products</a> to help other customers and the store.
        </Notice>
      )}
      {o.status === "CANCELLED" && o.paymentState === "REFUNDED" && (
        <Notice tone="info" icon={CircleAlert}>
          Refunds usually reach your account within 5 to 10 working days, depending on your bank.
        </Notice>
      )}

      {paying && (
        <PayOrderDialog
          open
          orders={[o]}
          onClose={() => setPaying(false)}
          onDone={() => {
            setPaying(false);
            order.reload();
            payments.reload();
          }}
        />
      )}
      {cancelling && (
        <CancelOrderDialog
          open
          order={o}
          path={`/api/orders/${o.id}/cancel`}
          onClose={() => setCancelling(false)}
          onDone={(updated) => {
            setCancelling(false);
            if (updated) order.setData(updated);
            else order.reload();
          }}
        />
      )}
      {contact && <ContactSeller open onClose={() => setContact(false)} sellerId={o.sellerId} sellerName={o.sellerName} orderNumber={o.orderNumber} />}
    </div>
  );
}
