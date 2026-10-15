import { useState } from "react";
import { CircleAlert, FileText, Package, Store, UserRound, XCircle } from "lucide-react";
import { api, qs } from "../api.js";
import { ActiveFilters, DebouncedSearch, KeyValues, PageHead, PeriodPicker, Toolbar, storeOptions, useFilters, useStores } from "../components/common.jsx";
import { PaymentStatus, methodName, orderColumns } from "../components/tables.jsx";
import {
  Button,
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
} from "../components/ui.jsx";
import { EMIRATES, deliveryAddress, formatDate, formatMoney, formatNumber, formatPercent, label } from "../format.js";
import { useLoader, useToast } from "../hooks.jsx";

const STATUS_TABS = [
  { id: "", label: "All", count: "ALL" },
  { id: "PENDING", label: "Awaiting payment", count: "PENDING" },
  { id: "CONFIRMED", label: "To ship", count: "CONFIRMED" },
  { id: "SHIPPED", label: "Shipped", count: "SHIPPED" },
  { id: "DELIVERED", label: "Delivered", count: "DELIVERED" },
  { id: "CANCELLED", label: "Cancelled", count: "CANCELLED" },
];

const PAYMENT_STATES = [
  { value: "", label: "Any payment state" },
  { value: "PAID", label: "Paid" },
  { value: "PAY_ON_DELIVERY", label: "Cash on delivery" },
  { value: "UNPAID", label: "Unpaid" },
  { value: "REFUNDED", label: "Refunded" },
  { value: "VOIDED", label: "Voided" },
];

const METHODS = [
  { value: "", label: "Any method" },
  { value: "CARD", label: "Card" },
  { value: "UPI", label: "Instant bank transfer" },
  { value: "COD", label: "Cash on delivery" },
];

const SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "total_desc", label: "Highest total" },
  { value: "total_asc", label: "Lowest total" },
];

/** Every order on the marketplace, with the filters support needs to find one. */
export function OrderListPage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const stores = useStores();
  const page = Number(f.page || 0);
  const query = {
    status: f.status,
    sellerId: f.sellerId,
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
  const orders = useLoader(() => api(`/api/admin/orders${qs({ ...query, page, size: 25 })}`), [JSON.stringify(query), page]);
  const counts = useLoader(() => api(`/api/admin/orders/counts${qs({ sellerId: f.sellerId })}`), [f.sellerId]);
  const data = orders.data;
  const storeLabel = stores.data?.find((s) => String(s.id) === f.sellerId);
  const moreCount = [f.paymentState, f.paymentMethod, f.region, f.minTotal, f.maxTotal].filter(Boolean).length;

  return (
    <>
      <PageHead
        title="Orders"
        sub="Every order on Nexora. A checkout with products from several stores becomes one order per store, each shipped and invoiced by its store."
        actions={<ExportMenu path="/api/admin/orders/export/{format}" query={qs(query)} />}
      />
      <Panel flush>
        <Tabs
          className="tabs-inset"
          tabs={STATUS_TABS.map((t) => ({ id: t.id, label: t.label, count: counts.data ? counts.data[t.count] || 0 : undefined }))}
          value={f.status || ""}
          onChange={(status) => set({ status })}
        />
        <Toolbar
          moreCount={moreCount}
          more={
            <>
              <Field label="Payment">
                <Select value={f.paymentState || ""} options={PAYMENT_STATES} onChange={(e) => set({ paymentState: e.target.value })} />
              </Field>
              <Field label="Method">
                <Select value={f.paymentMethod || ""} options={METHODS} onChange={(e) => set({ paymentMethod: e.target.value })} />
              </Field>
              <Field label="Emirate">
                <Select
                  value={f.region || ""}
                  options={[{ value: "", label: "Any emirate" }, ...EMIRATES.map((e) => ({ value: e, label: e }))]}
                  onChange={(e) => set({ region: e.target.value })}
                />
              </Field>
              <Field label="Total (AED)">
                <div className="range-pair">
                  <Input type="number" min="0" placeholder="Min" value={f.minTotal || ""} onChange={(e) => set({ minTotal: e.target.value })} />
                  <Input type="number" min="0" placeholder="Max" value={f.maxTotal || ""} onChange={(e) => set({ maxTotal: e.target.value })} />
                </div>
              </Field>
            </>
          }
          right={<Select className="select-sm" value={f.sort} options={SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />}
        >
          <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Order number, customer, product or tracking" />
          <Select
            className="select-sm"
            value={f.sellerId || ""}
            options={storeOptions(stores.data)}
            onChange={(e) => set({ sellerId: e.target.value })}
            aria-label="Store"
          />
          <PeriodPicker from={f.from} to={f.to} allowEmpty onChange={(r) => set(r)} label="Placed" />
        </Toolbar>
        <ActiveFilters
          onClear={() => clear(["status"])}
          chips={[
            { key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) },
            { key: "store", label: storeLabel?.store?.storeName || "Store", value: f.sellerId, onRemove: () => set({ sellerId: "" }) },
            { key: "period", label: `${f.from} to ${f.to}`, value: f.from, onRemove: () => set({ from: "", to: "" }) },
            { key: "pay", label: label(f.paymentState), value: f.paymentState, onRemove: () => set({ paymentState: "" }) },
            { key: "method", label: methodName(f.paymentMethod), value: f.paymentMethod, onRemove: () => set({ paymentMethod: "" }) },
            { key: "region", label: f.region, value: f.region, onRemove: () => set({ region: "" }) },
            { key: "min", label: `From ${formatMoney(f.minTotal)}`, value: f.minTotal, onRemove: () => set({ minTotal: "" }) },
            { key: "max", label: `Up to ${formatMoney(f.maxTotal)}`, value: f.maxTotal, onRemove: () => set({ maxTotal: "" }) },
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
              columns={orderColumns()}
              onRowClick={(o) => navigate(`/orders/${o.id}`)}
              empty={<Empty icon={Package} title="No order matches these filters" />}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={25} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
    </>
  );
}

// ---------------------------------------------------------------- one order

function CancelDialog({ order, open, onClose, onDone }) {
  const toast = useToast();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const paid = order.paymentState === "PAID";
  const submit = async () => {
    if (reason.trim().length < 5) {
      setError("Write the reason the customer and the store will see (at least a few words).");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api(`/api/admin/orders/${order.id}/cancel`, { method: "PUT", body: { reason: `Nexora support: ${reason.trim()}`.slice(0, 255) } });
      toast(`Order ${order.orderNumber} cancelled${paid ? "; the payment is being refunded" : ""}`);
      onDone();
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
          ? `${formatMoney(order.totalAmount)} goes back to the customer's ${methodName(order.paymentMethod).toLowerCase()} and the stock returns to ${order.sellerName}.`
          : `Nothing has been charged. The reserved stock returns to ${order.sellerName}.`
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
        <Field label="Reason" hint="The customer and the store both see it, after 'Nexora support:'">
          <Textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} maxLength={230} autoFocus />
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

const HISTORY_TITLES = { PENDING: "Order placed", CONFIRMED: "Confirmed", SHIPPED: "Shipped", DELIVERED: "Delivered", CANCELLED: "Cancelled" };

export function OrderPage({ params, navigate }) {
  const order = useLoader(() => api(`/api/admin/orders/${params.id}`), [params.id]);
  const payments = useLoader(
    () => (order.data ? api(`/api/admin/payments${qs({ q: order.data.orderNumber, size: 10 })}`).then((p) => p.content) : Promise.resolve(null)),
    [order.data?.orderNumber],
  );
  const [cancelling, setCancelling] = useState(false);

  if (order.loading && !order.data) return <Loader text="Loading the order" />;
  if (order.error && !order.data) return <ErrorState message={order.error} onRetry={order.reload} />;
  const o = order.data;
  const delivery = deliveryAddress(o);
  const margin = Number(o.totalAmount) > 0 ? (Number(o.grossProfit) / Number(o.totalAmount)) * 100 : null;
  const cancellable = o.status === "PENDING" || o.status === "CONFIRMED";
  const invoiceReady = ["CONFIRMED", "SHIPPED", "DELIVERED"].includes(o.status);

  return (
    <>
      <PageHead
        title={`Order ${o.orderNumber}`}
        badges={<StatusBadge status={o.status} />}
        back={{ to: "/orders", label: "Orders" }}
        sub={`Placed ${formatDate(o.createdAt)} with ${o.sellerName} by ${o.recipientName || o.customerUsername}.`}
        actions={
          <>
            {invoiceReady && (
              <DownloadButton path={`/api/admin/orders/${o.id}/invoice`} icon={FileText}>
                Tax invoice
              </DownloadButton>
            )}
            {cancellable && (
              <Button variant="danger" icon={XCircle} onClick={() => setCancelling(true)}>
                Cancel order
              </Button>
            )}
          </>
        }
      />
      {o.status === "CANCELLED" && (
        <div style={{ marginBottom: 16 }}>
          <Notice tone="bad" icon={XCircle}>
            <strong>Cancelled.</strong> {o.cancelReason || "No reason was given."}
          </Notice>
        </div>
      )}
      <div className="grid-main">
        <div className="cc-stack">
          <Panel title="Items" sub={`${formatNumber(o.units)} units, cost and profit as the store recorded them`} flush>
            <DataTable
              rowKey="productId"
              rows={o.items}
              columns={[
                {
                  key: "product",
                  header: "Product",
                  render: (i) => (
                    <div>
                      <div className="cell-title">{i.productName}</div>
                      <div className="cell-sub">
                        {i.sku}
                        {i.categoryName ? ` - ${i.categoryName}` : ""}
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
                    <td colSpan={4}>
                      Total <span className="muted small">(includes VAT {formatMoney(o.vatAmount)})</span>
                    </td>
                    <td className="num strong">{formatMoney(o.totalAmount)}</td>
                    <td className="num strong">{formatMoney(o.grossProfit)}</td>
                  </tr>
                </>
              }
            />
            <div className="session-note" style={{ borderBottom: 0, borderTop: "1px solid var(--line)" }}>
              Cost of goods {formatMoney(o.totalCost)}; gross margin {formatPercent(margin)} before Nexora fees.
            </div>
          </Panel>

          <Panel title="Payments for this order" flush>
            {payments.loading && !payments.data ? (
              <Loader />
            ) : (
              <DataTable
                rows={payments.data || []}
                columns={[
                  {
                    key: "ref",
                    header: "Payment",
                    render: (p) => (
                      <div>
                        <div className="cell-ref">{p.transactionReference}</div>
                        <div className="cell-sub">{formatDate(p.createdAt)}</div>
                      </div>
                    ),
                  },
                  { key: "method", header: "Method", render: (p) => `${methodName(p.method)}${p.maskedInstrument ? ` - ${p.maskedInstrument}` : ""}` },
                  { key: "amount", header: "Amount", align: "right", render: (p) => formatMoney(p.amount) },
                  { key: "fee", header: "Nexora fee", align: "right", render: (p) => formatMoney(p.platformFee) },
                  {
                    key: "status",
                    header: "Status",
                    render: (p) => (
                      <div>
                        <PaymentStatus status={p.status} />
                        {(p.failureReason || p.refundReason) && <div className="cell-sub">{p.failureReason || p.refundReason}</div>}
                      </div>
                    ),
                  },
                ]}
                onRowClick={(p) => navigate(`/payments${qs({ q: p.transactionReference, open: p.id })}`)}
                empty={<Empty title="No payment attempt yet" />}
              />
            )}
          </Panel>

          <Panel title="History">
            <ol className="timeline">
              {o.history.map((h, index) => {
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
          </Panel>
        </div>

        <div className="cc-stack">
          <Panel
            title="Customer"
            actions={
              <a className="btn btn-ghost btn-sm" href={`#/customers/${o.customerId}`}>
                <UserRound size={15} /> Profile
              </a>
            }
          >
            <KeyValues
              items={[
                { label: "Account", value: o.customerUsername },
                { label: "E-mail", value: o.customerEmail ? <a href={`mailto:${o.customerEmail}`}>{o.customerEmail}</a> : "-" },
                { label: "Phone", value: o.customerPhone || "-" },
              ]}
            />
          </Panel>
          <Panel title="Delivery">
            <KeyValues
              items={[
                { label: "Recipient", value: o.recipientName },
                { label: "Address", value: delivery.address },
                { label: "Phone", value: delivery.phone || "-" },
                o.carrier && { label: "Carrier", value: o.carrier },
                o.trackingNumber && { label: "Tracking", value: <span className="ref">{o.trackingNumber}</span> },
                o.shippedAt && { label: "Shipped", value: formatDate(o.shippedAt) },
                o.deliveredAt && { label: "Delivered", value: formatDate(o.deliveredAt) },
              ]}
            />
          </Panel>
          <Panel
            title="Store"
            actions={
              <a className="btn btn-ghost btn-sm" href={`#/stores/${o.sellerId}`}>
                <Store size={15} /> Store
              </a>
            }
          >
            <KeyValues
              items={[
                { label: "Store", value: o.sellerName },
                { label: "Payment", value: <StatusBadge status={o.paymentState} /> },
                { label: "Method", value: o.paymentMethod ? methodName(o.paymentMethod) : "Not chosen yet" },
                o.confirmedAt && { label: "Confirmed", value: formatDate(o.confirmedAt) },
              ]}
            />
          </Panel>
        </div>
      </div>
      {cancelling && (
        <CancelDialog
          order={o}
          open
          onClose={() => setCancelling(false)}
          onDone={() => {
            setCancelling(false);
            order.reload();
            payments.reload();
          }}
        />
      )}
    </>
  );
}
