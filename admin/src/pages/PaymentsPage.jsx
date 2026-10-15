import { Banknote, CircleAlert, CreditCard, Landmark, Receipt, RotateCcw, Wallet } from "lucide-react";
import { api, qs } from "../api.js";
import { HBarList, TrendChart } from "../components/charts.jsx";
import {
  ActiveFilters,
  DebouncedSearch,
  KeyValues,
  PageHead,
  PeriodPicker,
  Toolbar,
  periodName,
  storeOptions,
  useFilters,
  useStores,
} from "../components/common.jsx";
import { PaymentStatus, methodName, paymentColumns } from "../components/tables.jsx";
import { DataTable, Drawer, Empty, ErrorState, ExportMenu, Field, Input, Loader, Notice, Pagination, Panel, Select, Stat } from "../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, formatPercent, lastDays, shortDay } from "../format.js";
import { useLoader } from "../hooks.jsx";

const STATUSES = [
  { value: "", label: "Any status" },
  { value: "SUCCESS", label: "Successful" },
  { value: "PENDING", label: "Cash to collect" },
  { value: "FAILED", label: "Failed" },
  { value: "REFUNDED", label: "Refunded" },
  { value: "CANCELLED", label: "Cancelled" },
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
  { value: "amount_desc", label: "Highest amount" },
  { value: "amount_asc", label: "Lowest amount" },
];

/** Everything known about one payment, side by side with its order, store and customer. */
function PaymentDrawer({ id, onClose }) {
  const payment = useLoader(() => api(`/api/admin/payments/${id}`), [id]);
  const p = payment.data;
  return (
    <Drawer open title={p ? p.transactionReference : "Payment"} sub={p ? `${methodName(p.method)}, ${formatDate(p.createdAt)}` : null} onClose={onClose}>
      {payment.loading && !p ? (
        <Loader />
      ) : payment.error && !p ? (
        <ErrorState message={payment.error} onRetry={payment.reload} />
      ) : (
        <div className="stack">
          <div className="row row-wrap" style={{ gap: 8 }}>
            <PaymentStatus status={p.status} />
            {p.orderUpdatePending && <span className="small bad">The order has not taken this result yet; it is retried automatically.</span>}
          </div>
          {(p.failureReason || p.refundReason) && (
            <Notice tone={p.failureReason ? "bad" : "info"} icon={CircleAlert}>
              {p.failureReason || p.refundReason}
            </Notice>
          )}
          <Panel title="Money">
            <KeyValues
              items={[
                { label: "Amount", value: formatMoney(p.amount, p.currency) },
                { label: "Nexora fee", value: formatMoney(p.platformFee, p.currency) },
                { label: "The store gets", value: formatMoney(p.sellerNet, p.currency), total: true },
              ]}
            />
          </Panel>
          <Panel title="Who and what">
            <KeyValues
              items={[
                { label: "Order", value: <a href={`#/orders/${p.orderId}`}>{p.orderNumber}</a> },
                { label: "Store", value: <a href={`#/stores/${p.sellerId}`}>{p.sellerName}</a> },
                { label: "Customer", value: <a href={`#/customers/${p.customerId}`}>{p.customerUsername}</a> },
              ]}
            />
          </Panel>
          <Panel title="Payment provider">
            <KeyValues
              items={[
                { label: "Method", value: methodName(p.method) },
                { label: "Card or account", value: p.maskedInstrument || "-" },
                { label: "Provider", value: p.provider || "-" },
                { label: "Provider reference", value: p.providerReference ? <span className="ref">{p.providerReference}</span> : "-" },
                { label: "Created", value: formatDate(p.createdAt) },
                { label: "Last change", value: formatDate(p.updatedAt) },
              ]}
            />
          </Panel>
        </div>
      )}
    </Drawer>
  );
}

export function PaymentsPage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const stores = useStores();
  const range = f.from && f.to ? { from: f.from, to: f.to } : lastDays(30);
  const page = Number(f.page || 0);
  // a reference search looks at every date: the payment someone asks about may be older than the period
  const query = {
    status: f.status,
    method: f.method,
    sellerId: f.sellerId,
    minAmount: f.minAmount,
    maxAmount: f.maxAmount,
    q: f.q,
    sort: f.sort,
    ...(f.q ? {} : range),
  };
  const summary = useLoader(() => api(`/api/admin/payments/summary${qs(range)}`), [range.from, range.to]);
  const list = useLoader(() => api(`/api/admin/payments${qs({ ...query, page, size: 25 })}`), [JSON.stringify(query), page]);
  const s = summary.data;
  const data = list.data;

  return (
    <>
      <PageHead
        title="Payments"
        sub="Every payment attempt: cards, instant bank transfers and cash on delivery. Nexora keeps its fee when a payment is collected; the rest is the store's."
        actions={
          <>
            <PeriodPicker from={range.from} to={range.to} onChange={(r) => set(r)} />
            <ExportMenu path="/api/admin/payments/export/{format}" query={qs(query)} />
          </>
        }
      />
      <div className="cc-stack">
        {summary.loading && !s ? (
          <Loader text="Adding up payments" />
        ) : summary.error && !s ? (
          <ErrorState message={summary.error} onRetry={summary.reload} />
        ) : (
          <>
            <div className="stats" style={{ opacity: summary.loading ? 0.6 : 1 }}>
              <Stat label="Payment volume" icon={Wallet} value={formatMoney(s.totalVolume)} hint={`${formatNumber(s.payments)} payments`} />
              <Stat label="Collected" icon={CreditCard} value={formatMoney(s.collected)} hint={`${formatMoney(s.awaitingCollection)} cash still to collect`} />
              <Stat label="Nexora fees" icon={Landmark} value={formatMoney(s.platformRevenue)} hint={`${formatPercent(s.feeRatePercent)} of collected`} />
              <Stat label="Paid out to stores" icon={Banknote} value={formatMoney(s.paidOutToSellers)} />
              <Stat label="Refunded" icon={RotateCcw} value={formatMoney(s.refunded)} hint={`${formatNumber(s.refunds)} refunds`} upIsGood={false} />
              <Stat
                label="Success rate"
                icon={Receipt}
                value={formatPercent(s.successRatePercent)}
                hint={`${formatNumber(s.failedAttempts)} failed attempts`}
                attention={Number(s.successRatePercent) < 90}
              />
            </div>
            <div className="grid-main" style={{ opacity: summary.loading ? 0.6 : 1 }}>
              <Panel title="Payment volume per day" sub={`All methods, ${periodName(range.from, range.to)}`}>
                <TrendChart
                  data={(s.daily || []).map((d) => ({ ...d, label: shortDay(d.date) }))}
                  series={[{ key: "amount", label: "Volume" }]}
                  labelFormat={(text, point) => `${text} - ${formatNumber(point?.payments)} payments, ${formatMoney(point?.platformFees)} in fees`}
                />
              </Panel>
              <div className="cc-stack">
                <Panel title="By method">
                  <HBarList
                    items={(s.byMethod || []).map((m) => ({ label: methodName(m.method), value: m.amount, hint: `${formatNumber(m.payments)}` }))}
                    color="var(--series-2)"
                  />
                </Panel>
                <Panel title="Stores by volume" flush>
                  <DataTable
                    rowKey="sellerId"
                    rows={(s.topSellers || []).slice(0, 6)}
                    onRowClick={(t) => set({ sellerId: String(t.sellerId) })}
                    columns={[
                      { key: "s", header: "Store", render: (t) => t.sellerName },
                      { key: "a", header: "Volume", align: "right", render: (t) => formatMoney(t.amount) },
                      { key: "f", header: "Fees", align: "right", render: (t) => formatMoney(t.platformFees) },
                    ]}
                    empty={<Empty title="No payments in this period" />}
                  />
                </Panel>
              </div>
            </div>
          </>
        )}

        <Panel flush>
          <Toolbar
            moreCount={[f.minAmount, f.maxAmount].filter(Boolean).length}
            more={
              <Field label="Amount (AED)">
                <div className="range-pair">
                  <Input type="number" min="0" placeholder="Min" value={f.minAmount || ""} onChange={(e) => set({ minAmount: e.target.value })} />
                  <Input type="number" min="0" placeholder="Max" value={f.maxAmount || ""} onChange={(e) => set({ maxAmount: e.target.value })} />
                </div>
              </Field>
            }
            right={<Select className="select-sm" value={f.sort} options={SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />}
          >
            <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Reference, order number, customer or card" />
            <Select className="select-sm" value={f.status || ""} options={STATUSES} onChange={(e) => set({ status: e.target.value })} aria-label="Status" />
            <Select className="select-sm" value={f.method || ""} options={METHODS} onChange={(e) => set({ method: e.target.value })} aria-label="Method" />
            <Select
              className="select-sm"
              value={f.sellerId || ""}
              options={storeOptions(stores.data)}
              onChange={(e) => set({ sellerId: e.target.value })}
              aria-label="Store"
            />
          </Toolbar>
          <ActiveFilters
            onClear={() => clear(["from", "to"])}
            chips={[
              { key: "q", label: `"${f.q}" (all dates)`, value: f.q, onRemove: () => set({ q: "", open: "" }) },
              { key: "status", label: STATUSES.find((x) => x.value === f.status)?.label, value: f.status, onRemove: () => set({ status: "" }) },
              { key: "method", label: methodName(f.method), value: f.method, onRemove: () => set({ method: "" }) },
              {
                key: "store",
                label: stores.data?.find((x) => String(x.id) === f.sellerId)?.store?.storeName || "Store",
                value: f.sellerId,
                onRemove: () => set({ sellerId: "" }),
              },
              { key: "min", label: `From ${formatMoney(f.minAmount)}`, value: f.minAmount, onRemove: () => set({ minAmount: "" }) },
              { key: "max", label: `Up to ${formatMoney(f.maxAmount)}`, value: f.maxAmount, onRemove: () => set({ maxAmount: "" }) },
            ]}
          />
          {list.loading && !data ? (
            <Loader text="Loading payments" />
          ) : list.error && !data ? (
            <ErrorState message={list.error} onRetry={list.reload} />
          ) : (
            <div style={{ opacity: list.loading ? 0.6 : 1 }}>
              <DataTable
                rows={data.content}
                columns={paymentColumns()}
                onRowClick={(p) => set({ open: String(p.id), page: f.page })}
                empty={<Empty icon={CreditCard} title="No payment matches these filters" />}
              />
              <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={25} onPage={(p) => set({ page: p })} />
            </div>
          )}
        </Panel>
      </div>
      {f.open && <PaymentDrawer id={f.open} onClose={() => set({ open: "", page: f.page })} />}
    </>
  );
}
