import { useState } from "react";
import { CircleAlert, FileText, PackageCheck, Printer, ShoppingBag, Truck, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { CancelOrderDialog, OrderHistory, OrderTracker } from "../../components/orders.jsx";
import {
  Button,
  Confirm,
  DataTable,
  DownloadButton,
  Empty,
  ErrorState,
  ExportMenu,
  Field,
  Input,
  Loader,
  Modal,
  Notice,
  Pagination,
  Panel,
  Select,
  StatusBadge,
  Tabs,
  Textarea,
} from "../../components/ui.jsx";
import { EMIRATES, deliveryAddress, formatDate, formatMoney, formatNumber, formatPercent, label, plural, timeAgo } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import { ActiveFilters, DebouncedSearch, PageHead, PeriodPicker, Toolbar, listOf, useFilters } from "./common.jsx";
import { useSeller } from "./SellerLayout.jsx";

const STATUS_TABS = [
  { id: "", label: "All", key: "ALL" },
  { id: "PENDING", label: "Awaiting payment", key: "PENDING" },
  { id: "CONFIRMED", label: "To ship", key: "CONFIRMED" },
  { id: "SHIPPED", label: "Shipped", key: "SHIPPED" },
  { id: "DELIVERED", label: "Delivered", key: "DELIVERED" },
  { id: "CANCELLED", label: "Cancelled", key: "CANCELLED" },
];
const ORDER_SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "total_desc", label: "Highest total" },
  { value: "total_asc", label: "Lowest total" },
];
const PAYMENT_STATES = ["UNPAID", "PAID", "PAY_ON_DELIVERY", "REFUNDED", "VOIDED"];
export const METHOD_LABELS = { CARD: "Card", UPI: "Bank transfer", COD: "Cash on delivery" };
const CARRIERS = ["Aramex", "DHL Express", "Emirates Post", "FedEx", "iMile", "Quiqup", "Jeebly", "Own delivery"];

function ShipDialog({ order, onClose, onDone }) {
  const toast = useToast();
  const [carrier, setCarrier] = useState(CARRIERS[0]);
  const [other, setOther] = useState("");
  const [tracking, setTracking] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const carrierName = carrier === "other" ? other.trim() : carrier;
  return (
    <Modal
      open
      onClose={onClose}
      title={`Ship order ${order.orderNumber}`}
      sub={`To ${order.recipientName}, ${order.shippingCity || order.shippingRegion || ""}. The customer gets the tracking number by e-mail.`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            icon={Truck}
            loading={busy}
            disabled={!carrierName || !tracking.trim()}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                const updated = await api(`/api/seller/orders/${order.id}/ship`, {
                  method: "PUT",
                  body: { carrier: carrierName, trackingNumber: tracking.trim(), note: note.trim() || null },
                });
                toast(`Order ${order.orderNumber} shipped`);
                onDone(updated);
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            Mark as shipped
          </Button>
        </>
      }
    >
      <div className="stack">
        <div className="form-grid">
          <Field label="Carrier">
            <Select
              value={carrier}
              onChange={(e) => setCarrier(e.target.value)}
              options={[...CARRIERS.map((c) => ({ value: c, label: c })), { value: "other", label: "Another carrier" }]}
            />
          </Field>
          {carrier === "other" ? (
            <Field label="Carrier name">
              <Input value={other} onChange={(e) => setOther(e.target.value)} maxLength={60} />
            </Field>
          ) : (
            <span />
          )}
          <Field label="Tracking number" className="span-2">
            <Input value={tracking} onChange={(e) => setTracking(e.target.value)} maxLength={60} autoFocus />
          </Field>
          <Field label="Note for the customer (optional)" className="span-2">
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} rows={2} />
          </Field>
        </div>
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

export function SellerOrdersPage({ route, navigate }) {
  const { reloadCounts } = useSeller();
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const page = Number(f.page || 0);
  const query = {
    status: listOf(f.status),
    paymentState: f.paymentState,
    paymentMethod: f.paymentMethod,
    from: f.from,
    to: f.to,
    minTotal: f.minTotal,
    maxTotal: f.maxTotal,
    region: f.region,
    q: f.q,
    sort: f.sort,
  };
  const orders = useLoader(() => api(`/api/seller/orders${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const counts = useLoader(() => api("/api/seller/orders/counts").catch(() => ({})), []);
  const [shipping, setShipping] = useState(null);
  const data = orders.data;
  const moreCount = [f.paymentState, f.paymentMethod, f.minTotal || f.maxTotal, f.region].filter(Boolean).length;

  return (
    <>
      <PageHead
        title="Orders"
        sub="Ship confirmed orders, print packing slips and invoices, and follow every order of your store."
        actions={<ExportMenu path="/api/seller/orders/export/{format}" query={qs(query)} />}
      />
      <Tabs tabs={STATUS_TABS.map((t) => ({ ...t, count: counts.data?.[t.key] }))} value={f.status || ""} onChange={(status) => set({ status })} />
      <Panel flush>
        <Toolbar
          moreCount={moreCount}
          more={
            <>
              <Field label="Payment">
                <Select
                  value={f.paymentState || ""}
                  placeholder="Any payment state"
                  options={PAYMENT_STATES.map((s) => ({ value: s, label: label(s) }))}
                  onChange={(e) => set({ paymentState: e.target.value })}
                />
              </Field>
              <Field label="Payment method">
                <Select
                  value={f.paymentMethod || ""}
                  placeholder="Any method"
                  options={Object.entries(METHOD_LABELS).map(([value, text]) => ({ value, label: text }))}
                  onChange={(e) => set({ paymentMethod: e.target.value })}
                />
              </Field>
              <Field label="Emirate">
                <Select
                  value={f.region || ""}
                  placeholder="All emirates"
                  options={EMIRATES.map((e) => ({ value: e, label: e }))}
                  onChange={(e) => set({ region: e.target.value })}
                />
              </Field>
              <Field label="Order total (AED)">
                <div className="range-pair">
                  <Input
                    key={`min-${f.minTotal || ""}`}
                    type="number"
                    min="0"
                    placeholder="Min"
                    defaultValue={f.minTotal || ""}
                    onBlur={(e) => e.target.value !== (f.minTotal || "") && set({ minTotal: e.target.value })}
                    aria-label="Minimum total"
                  />
                  <Input
                    key={`max-${f.maxTotal || ""}`}
                    type="number"
                    min="0"
                    placeholder="Max"
                    defaultValue={f.maxTotal || ""}
                    onBlur={(e) => e.target.value !== (f.maxTotal || "") && set({ maxTotal: e.target.value })}
                    aria-label="Maximum total"
                  />
                </div>
              </Field>
            </>
          }
        >
          <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Order number, customer or product" />
          <PeriodPicker allowEmpty from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
          <Select className="select-sm" value={f.sort} options={ORDER_SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />
        </Toolbar>
        <ActiveFilters
          onClear={() => clear(["sort", "status"])}
          chips={[
            { key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) },
            { key: "dates", label: `${f.from || "start"} to ${f.to || "today"}`, value: f.from || f.to, onRemove: () => set({ from: "", to: "" }) },
            { key: "pay", label: label(f.paymentState), value: f.paymentState, onRemove: () => set({ paymentState: "" }) },
            { key: "method", label: METHOD_LABELS[f.paymentMethod], value: f.paymentMethod, onRemove: () => set({ paymentMethod: "" }) },
            { key: "region", label: f.region, value: f.region, onRemove: () => set({ region: "" }) },
            {
              key: "total",
              label: `AED ${f.minTotal || 0} to ${f.maxTotal || "any"}`,
              value: f.minTotal || f.maxTotal,
              onRemove: () => set({ minTotal: "", maxTotal: "" }),
            },
          ]}
        />
        {orders.loading && !data ? (
          <Loader text="Loading orders" />
        ) : orders.error && !data ? (
          <ErrorState message={orders.error} onRetry={orders.reload} />
        ) : (
          <div style={{ opacity: orders.loading ? 0.6 : 1 }}>
            <DataTable
              rows={data.content}
              onRowClick={(o) => navigate(`/seller/orders/${o.id}`)}
              empty={
                <Empty icon={ShoppingBag} title={f.status || f.q ? "No order matches" : "No orders yet"}>
                  {f.status || f.q ? "Try another tab or clear the filters." : "Orders appear here as soon as customers buy your listed products."}
                </Empty>
              }
              columns={[
                {
                  key: "order",
                  header: "Order",
                  render: (o) => (
                    <div>
                      <div className="cell-title">{o.orderNumber}</div>
                      <div className="cell-sub">{timeAgo(o.createdAt)}</div>
                    </div>
                  ),
                },
                {
                  key: "customer",
                  header: "Customer",
                  render: (o) => (
                    <div>
                      <div>{o.recipientName || o.customerUsername}</div>
                      <div className="cell-sub">{[...new Set([o.shippingCity, o.shippingRegion].filter(Boolean))].join(", ")}</div>
                    </div>
                  ),
                },
                { key: "units", header: "Items", align: "right", render: (o) => formatNumber(o.units) },
                { key: "total", header: "Total", align: "right", render: (o) => <span className="strong">{formatMoney(o.totalAmount)}</span> },
                {
                  key: "profit",
                  header: "Profit",
                  align: "right",
                  render: (o) => <span className={Number(o.grossProfit) < 0 ? "bad" : ""}>{formatMoney(o.grossProfit)}</span>,
                },
                {
                  key: "payment",
                  header: "Payment",
                  render: (o) => (
                    <div>
                      <StatusBadge status={o.paymentState} />
                      <div className="cell-sub">{METHOD_LABELS[o.paymentMethod] || "-"}</div>
                    </div>
                  ),
                },
                { key: "status", header: "Status", render: (o) => <StatusBadge status={o.status} /> },
                {
                  key: "action",
                  header: "",
                  render: (o) =>
                    o.status === "CONFIRMED" ? (
                      <span onClick={(e) => e.stopPropagation()}>
                        <Button size="sm" variant="primary" icon={Truck} onClick={() => setShipping(o)}>
                          Ship
                        </Button>
                      </span>
                    ) : null,
                },
              ]}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
      {shipping && (
        <ShipDialog
          order={shipping}
          onClose={() => setShipping(null)}
          onDone={() => {
            setShipping(null);
            orders.reload();
            counts.reload();
            reloadCounts();
          }}
        />
      )}
    </>
  );
}

export function SellerOrderPage({ id }) {
  const toast = useToast();
  const { reloadCounts } = useSeller();
  const order = useLoader(() => api(`/api/seller/orders/${id}`), [id]);
  const [shipping, setShipping] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [delivering, setDelivering] = useState(false);
  const [busy, setBusy] = useState(false);

  if (order.loading && !order.data) return <Loader text="Loading the order" />;
  if (order.error && !order.data) return <ErrorState message={order.error} onRetry={order.reload} />;
  const o = order.data;
  const delivery = deliveryAddress(o);
  const invoiced = ["CONFIRMED", "SHIPPED", "DELIVERED"].includes(o.status);
  const margin = Number(o.totalAmount) > 0 ? (Number(o.grossProfit) / Number(o.totalAmount)) * 100 : 0;

  const changed = (message) => {
    if (message) toast(message);
    order.reload();
    reloadCounts();
  };

  return (
    <>
      <PageHead
        title={`Order ${o.orderNumber}`}
        sub={`Placed ${formatDate(o.createdAt)} by ${o.customerUsername}.`}
        back={{ to: "/seller/orders", label: "Orders" }}
        actions={
          <>
            {invoiced && (
              <>
                <DownloadButton path={`/api/seller/orders/${id}/packing-slip`} icon={Printer}>
                  Packing slip
                </DownloadButton>
                <DownloadButton path={`/api/seller/orders/${id}/invoice`} icon={FileText}>
                  Tax invoice
                </DownloadButton>
              </>
            )}
            {(o.status === "PENDING" || o.status === "CONFIRMED") && (
              <Button variant="ghost" icon={XCircle} onClick={() => setCancelling(true)}>
                Cancel order
              </Button>
            )}
            {o.status === "CONFIRMED" && (
              <Button variant="primary" icon={Truck} onClick={() => setShipping(true)}>
                Ship
              </Button>
            )}
            {o.status === "SHIPPED" && (
              <Button variant="primary" icon={PackageCheck} onClick={() => setDelivering(true)}>
                Mark as delivered
              </Button>
            )}
          </>
        }
      />
      <div className="stack">
        {o.status === "PENDING" && (
          <Notice tone="warn" icon={CircleAlert}>
            The customer has not paid yet. The stock is reserved; if payment does not arrive within 30 minutes the order is cancelled and the stock released.
          </Notice>
        )}
        <Panel>
          <OrderTracker order={o} />
        </Panel>
        <div className="detail-grid">
          <Panel title="Items" flush>
            <DataTable
              rowKey="productId"
              rows={o.items}
              columns={[
                {
                  key: "product",
                  header: "Product",
                  render: (i) => (
                    <div>
                      <a className="cell-title" href={`#/seller/products/${i.productId}`}>
                        {i.productName}
                      </a>
                      <div className="cell-sub">
                        {i.sku} - {i.categoryName}
                      </div>
                    </div>
                  ),
                },
                { key: "price", header: "Price", align: "right", render: (i) => formatMoney(i.unitPrice) },
                { key: "cost", header: "Cost", align: "right", render: (i) => formatMoney(i.unitCost) },
                { key: "qty", header: "Qty", align: "right", render: (i) => formatNumber(i.quantity) },
                { key: "total", header: "Total", align: "right", render: (i) => <span className="strong">{formatMoney(i.lineTotal)}</span> },
                { key: "profit", header: "Profit", align: "right", render: (i) => formatMoney(i.lineProfit) },
              ]}
              footer={
                <>
                  <tr>
                    <td colSpan={4}>Total (VAT included)</td>
                    <td className="num">{formatMoney(o.totalAmount)}</td>
                    <td className="num">{formatMoney(o.grossProfit)}</td>
                  </tr>
                  <tr>
                    <td colSpan={6} className="small muted" style={{ fontWeight: 500 }}>
                      Includes VAT of {formatMoney(o.vatAmount)}. Cost of goods {formatMoney(o.totalCost)}, gross margin {formatPercent(margin)} before Nexora
                      fees.
                    </td>
                  </tr>
                </>
              }
            />
          </Panel>
          <div className="stack">
            <Panel title="Customer and delivery">
              <dl className="dl">
                <dt>Recipient</dt>
                <dd>{o.recipientName}</dd>
                <dt>Address</dt>
                <dd>{delivery.address}</dd>
                <dt>Phone</dt>
                <dd>
                  {delivery.phone || o.customerPhone ? <a href={`tel:${delivery.phone || o.customerPhone}`}>{delivery.phone || o.customerPhone}</a> : "-"}
                </dd>
                <dt>Email</dt>
                <dd>{o.customerEmail ? <a href={`mailto:${o.customerEmail}`}>{o.customerEmail}</a> : "-"}</dd>
                <dt>Account</dt>
                <dd>{o.customerUsername}</dd>
              </dl>
            </Panel>
            <Panel title="Payment and shipping">
              <dl className="dl">
                <dt>Method</dt>
                <dd>{METHOD_LABELS[o.paymentMethod] || "Not paid yet"}</dd>
                <dt>Payment</dt>
                <dd>
                  <StatusBadge status={o.paymentState} />
                </dd>
                {o.carrier && (
                  <>
                    <dt>Carrier</dt>
                    <dd>{o.carrier}</dd>
                    <dt>Tracking</dt>
                    <dd className="code">{o.trackingNumber}</dd>
                  </>
                )}
                {o.shippedAt && (
                  <>
                    <dt>Shipped</dt>
                    <dd>{formatDate(o.shippedAt)}</dd>
                  </>
                )}
                {o.deliveredAt && (
                  <>
                    <dt>Delivered</dt>
                    <dd>{formatDate(o.deliveredAt)}</dd>
                  </>
                )}
              </dl>
              {o.paymentState === "PAY_ON_DELIVERY" && (
                <p className="small soft" style={{ marginTop: 12 }}>
                  Collect {formatMoney(o.totalAmount)} in cash on delivery.
                </p>
              )}
            </Panel>
            <Panel title="History">
              <OrderHistory history={o.history} />
            </Panel>
          </div>
        </div>
      </div>

      {shipping && (
        <ShipDialog
          order={o}
          onClose={() => setShipping(false)}
          onDone={() => {
            setShipping(false);
            changed();
          }}
        />
      )}
      {cancelling && (
        <CancelOrderDialog
          open
          seller
          order={o}
          path={`/api/seller/orders/${id}/cancel`}
          onClose={() => setCancelling(false)}
          onDone={() => {
            setCancelling(false);
            changed();
          }}
        />
      )}
      <Confirm
        open={delivering}
        title={`Mark ${o.orderNumber} as delivered?`}
        confirmLabel="Mark as delivered"
        busy={busy}
        onClose={() => setDelivering(false)}
        onConfirm={async () => {
          setBusy(true);
          try {
            await api(`/api/seller/orders/${id}/deliver`, { method: "PUT" });
            setDelivering(false);
            changed(`Order ${o.orderNumber} delivered`);
          } catch (e) {
            toast(e.message, "bad");
          } finally {
            setBusy(false);
          }
        }}
      >
        {o.paymentState === "PAY_ON_DELIVERY"
          ? `This also records the ${formatMoney(o.totalAmount)} cash payment as collected.`
          : "The customer can then review the products."}{" "}
        {plural(o.units, "unit")} to {o.recipientName}.
      </Confirm>
    </>
  );
}
