import { useState } from "react";
import { Ban, ExternalLink, RotateCcw, Store } from "lucide-react";
import { api, qs } from "../api.js";
import { AccountBadge, AccountStatusDialog, RecentLogins, SuspensionNotice, VatBadge } from "../components/account.jsx";
import { HBarList, TrendChart } from "../components/charts.jsx";
import {
  ActiveFilters,
  DebouncedSearch,
  KeyValues,
  MARKETPLACE_URL,
  PageHead,
  PeriodPicker,
  Toolbar,
  periodName,
  storeName,
  useFilters,
} from "../components/common.jsx";
import { PagedPanel, methodName, orderColumns, paymentColumns, productColumns } from "../components/tables.jsx";
import { Button, DataTable, Empty, ErrorState, ExportMenu, Loader, Pagination, Panel, PillToggle, Select, Stat, StatusBadge, Tabs } from "../components/ui.jsx";
import { formatDate, formatDay, formatMoney, formatNumber, formatPercent, lastDays, lastMonths, longMonth, timeAgo } from "../format.js";
import { useLoader } from "../hooks.jsx";
import { StoreReviewsPanel } from "./ReviewsPage.jsx";

const STATUS = [
  { value: "", label: "All" },
  { value: "true", label: "Active" },
  { value: "false", label: "Suspended" },
];

const SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "name", label: "Username" },
  { value: "lastLogin", label: "Signed in most recently" },
];

/** Shared by the store and customer lists: who, status, joined and last sign-in. */
export function AccountList({ role, route, navigate, title, sub, searchPlaceholder, columns }) {
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const page = Number(f.page || 0);
  const query = { role, active: f.active, search: f.q, joinedFrom: f.from, joinedTo: f.to, sort: f.sort };
  const accounts = useLoader(() => api(`/api/admin/users${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const data = accounts.data;
  const base = role === "SELLER" ? "/stores" : "/customers";
  return (
    <>
      <PageHead title={title} sub={sub} actions={<ExportMenu path="/api/admin/users/export/{format}" query={qs(query)} />} />
      <Panel flush>
        <Toolbar right={<Select className="select-sm" value={f.sort} options={SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />}>
          <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder={searchPlaceholder} />
          <PillToggle label="Status" options={STATUS} value={f.active || ""} onChange={(active) => set({ active })} />
          <PeriodPicker from={f.from} to={f.to} allowEmpty onChange={(r) => set(r)} label="Joined" />
        </Toolbar>
        <ActiveFilters
          onClear={() => clear()}
          chips={[
            { key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) },
            { key: "joined", label: `Joined ${f.from} to ${f.to}`, value: f.from, onRemove: () => set({ from: "", to: "" }) },
          ]}
        />
        {accounts.loading && !data ? (
          <Loader />
        ) : accounts.error && !data ? (
          <ErrorState message={accounts.error} onRetry={accounts.reload} />
        ) : (
          <div style={{ opacity: accounts.loading ? 0.6 : 1 }}>
            <DataTable
              rows={data.content}
              columns={columns}
              onRowClick={(u) => navigate(`${base}/${u.id}`)}
              empty={<Empty title="No account matches these filters" />}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
    </>
  );
}

export const joinedColumns = [
  { key: "joined", header: "Joined", render: (u) => <span className="nowrap">{formatDay(u.createdAt)}</span> },
  { key: "login", header: "Last sign-in", render: (u) => <span className="nowrap">{u.lastLoginAt ? timeAgo(u.lastLoginAt) : "Never"}</span> },
  { key: "status", header: "Status", render: (u) => <AccountBadge user={u} /> },
];

export function StoreListPage(props) {
  return (
    <AccountList
      {...props}
      role="SELLER"
      title="Stores"
      sub="Every business selling on Nexora. Open a store to see its sales, stock, purchasing and money, or to suspend it."
      searchPlaceholder="Store, owner, e-mail, phone or city"
      columns={[
        {
          key: "store",
          header: "Store",
          render: (u) => (
            <div>
              <div className="cell-title">{storeName(u)}</div>
              <div className="cell-sub">{u.username}</div>
            </div>
          ),
        },
        { key: "city", header: "Ships from", render: (u) => u.store?.city || "-" },
        {
          key: "owner",
          header: "Owner",
          render: (u) => (
            <div>
              <div>{u.fullName || "-"}</div>
              <div className="cell-sub">{u.email}</div>
            </div>
          ),
        },
        {
          key: "vat",
          header: "VAT",
          render: (u) => (u.store?.vatNumber ? <span className="ref">{u.store.vatNumber}</span> : <span className="muted">Not registered</span>),
        },
        ...joinedColumns,
      ]}
    />
  );
}

// ---------------------------------------------------------------- one store

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "orders", label: "Orders" },
  { id: "products", label: "Products" },
  { id: "stock", label: "Stock" },
  { id: "purchasing", label: "Purchasing" },
  { id: "customers", label: "Customers" },
  { id: "reviews", label: "Reviews" },
  { id: "money", label: "Money" },
  { id: "account", label: "Account" },
];

function StoreOverview({ id, range, setRange, navigate }) {
  const data = useLoader(async () => {
    const [sales, stock] = await Promise.all([api(`/api/admin/sales/sellers/${id}${qs(range)}`), api(`/api/admin/inventory/sellers/${id}/summary`)]);
    return { sales, stock };
  }, [id, range.from, range.to]);
  if (data.loading && !data.data) return <Loader text="Loading the store's figures" />;
  if (data.error && !data.data) return <ErrorState message={data.error} onRetry={data.reload} />;
  const { sales: a, stock: s } = data.data;
  return (
    <div className="cc-stack" style={{ opacity: data.loading ? 0.6 : 1 }}>
      <div className="row-between">
        <span className="small muted">Sales {periodName(range.from, range.to)}; stock as of now.</span>
        <PeriodPicker from={range.from} to={range.to} onChange={setRange} />
      </div>
      <div className="stats">
        <Stat label="Sales" value={formatMoney(a.revenue)} delta={a.growth?.revenuePercent} />
        <Stat label="Orders" value={formatNumber(a.orders)} delta={a.growth?.ordersPercent} />
        <Stat label="Gross profit" value={formatMoney(a.grossProfit)} hint={`${formatPercent(a.grossMarginPercent)} margin`} />
        <Stat label="Cancelled" value={formatPercent(a.cancellationRatePercent)} hint="of orders" upIsGood={false} />
        <Stat
          label="Time to dispatch"
          value={a.averageDispatchHours != null ? `${Number(a.averageDispatchHours).toFixed(1)} h` : "-"}
          hint="payment to shipping"
        />
      </div>
      <div className="grid-main">
        <Panel title="Sales and gross profit">
          <TrendChart
            data={a.trend}
            type="line"
            series={[
              { key: "revenue", label: "Sales" },
              { key: "profit", label: "Gross profit" },
            ]}
          />
        </Panel>
        <Panel title="Stock" sub={`${formatNumber(s.products)} products in ${formatNumber(s.warehouses)} warehouses`}>
          <KeyValues
            items={[
              { label: "Listed on the marketplace", value: formatNumber(s.listedProducts) },
              { label: "Inventory only", value: formatNumber(s.inventoryOnlyProducts) },
              { label: "Units on hand", value: formatNumber(s.unitsOnHand) },
              { label: "Reserved for orders", value: formatNumber(s.unitsReserved) },
              { label: "Incoming from suppliers", value: formatNumber(s.unitsIncoming) },
              { label: "Low / out of stock", value: `${formatNumber(s.lowStockProducts)} / ${formatNumber(s.outOfStockProducts)}` },
              { label: "Open purchase orders", value: formatNumber(s.openPurchaseOrders) },
              { label: "Stock value at cost", value: formatMoney(s.stockValueAtCost), total: true },
              { label: "Stock value at price", value: formatMoney(s.stockValueAtPrice) },
            ]}
          />
        </Panel>
      </div>
      <div className="grid-3">
        <Panel title="Best-selling products">
          <HBarList items={a.topProducts.slice(0, 6).map((p) => ({ label: p.productName, value: p.revenue, hint: `${formatNumber(p.unitsSold)} sold` }))} />
        </Panel>
        <Panel title="Departments">
          <HBarList
            items={a.categories.map((c) => ({ label: c.category, value: c.revenue, hint: formatPercent(c.sharePercent, 0) }))}
            color="var(--series-3)"
          />
        </Panel>
        <Panel title="Top customers" flush>
          <DataTable
            rowKey="customerId"
            rows={a.topCustomers.slice(0, 6)}
            onRowClick={(c) => navigate(`/customers/${c.customerId}`)}
            columns={[
              { key: "c", header: "Customer", render: (c) => c.customerUsername },
              { key: "o", header: "Orders", align: "right", render: (c) => formatNumber(c.orders) },
              { key: "s", header: "Spent", align: "right", render: (c) => formatMoney(c.spent) },
            ]}
            empty={<Empty title="No customers in this period" />}
          />
        </Panel>
      </div>
    </div>
  );
}

function StoreStock({ id }) {
  const [tab, setTab] = useState("levels");
  const warehouses = useLoader(() => api(`/api/admin/inventory/sellers/${id}/warehouses`), [id]);
  return (
    <div className="cc-stack">
      <Panel title="Warehouses" flush>
        {warehouses.loading && !warehouses.data ? (
          <Loader />
        ) : (
          <DataTable
            rows={warehouses.data || []}
            columns={[
              {
                key: "w",
                header: "Warehouse",
                render: (w) => (
                  <div>
                    <div className="cell-title">
                      {w.code} - {w.name}
                    </div>
                    <div className="cell-sub">{[w.address, w.city].filter(Boolean).join(", ") || "No address"}</div>
                  </div>
                ),
              },
              {
                key: "d",
                header: "Role",
                render: (w) => (w.defaultWarehouse ? <StatusBadge status="ACTIVE">Ships orders first</StatusBadge> : w.active ? "Active" : "Closed"),
              },
              { key: "p", header: "Products", align: "right", render: (w) => formatNumber(w.productLines) },
              { key: "u", header: "Units", align: "right", render: (w) => formatNumber(w.units) },
              { key: "v", header: "Value at cost", align: "right", render: (w) => formatMoney(w.stockValueAtCost) },
            ]}
          />
        )}
      </Panel>
      <Tabs
        tabs={[
          { id: "levels", label: "Stock levels" },
          { id: "transfers", label: "Transfers" },
          { id: "counts", label: "Stock counts" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "levels" && (
        <PagedPanel
          title="Stock levels"
          path={`/api/admin/inventory/sellers/${id}/stock`}
          query={{ sort: "name" }}
          rowKey="productId"
          size={15}
          columns={[
            {
              key: "p",
              header: "Product",
              render: (r) => (
                <div>
                  <div className="cell-title">{r.productName}</div>
                  <div className="cell-sub">
                    {r.sku}
                    {r.categoryName ? ` - ${r.categoryName}` : ""}
                  </div>
                </div>
              ),
            },
            { key: "a", header: "Available", align: "right", render: (r) => <span className="strong">{formatNumber(r.availableQuantity)}</span> },
            { key: "r", header: "Reserved", align: "right", render: (r) => formatNumber(r.reservedQuantity) },
            { key: "o", header: "On hand", align: "right", render: (r) => formatNumber(r.onHandQuantity) },
            { key: "i", header: "Incoming", align: "right", render: (r) => (r.incomingQuantity ? `+${formatNumber(r.incomingQuantity)}` : "-") },
            { key: "rp", header: "Reorder at", align: "right", render: (r) => formatNumber(r.reorderPoint) },
            { key: "s", header: "Status", render: (r) => <StatusBadge status={r.stockStatus} /> },
            { key: "v", header: "Value at cost", align: "right", render: (r) => formatMoney(r.stockValueAtCost) },
          ]}
        />
      )}
      {tab === "transfers" && (
        <PagedPanel
          title="Transfers between warehouses"
          path={`/api/admin/inventory/sellers/${id}/transfers`}
          empty="No transfers"
          columns={[
            { key: "n", header: "Transfer", render: (t) => <span className="cell-ref">{t.number}</span> },
            { key: "r", header: "From and to", render: (t) => `${t.fromWarehouse} to ${t.toWarehouse}` },
            { key: "u", header: "Units", align: "right", render: (t) => formatNumber(t.totalUnits) },
            { key: "note", header: "Note", render: (t) => t.note || "-" },
            { key: "by", header: "By", render: (t) => t.createdBy },
            { key: "at", header: "When", render: (t) => <span className="nowrap">{formatDate(t.createdAt)}</span> },
          ]}
        />
      )}
      {tab === "counts" && (
        <PagedPanel
          title="Stock counts"
          path={`/api/admin/inventory/sellers/${id}/stock-counts`}
          empty="No stock counts"
          columns={[
            { key: "n", header: "Count", render: (c) => <span className="cell-ref">{c.number}</span> },
            { key: "w", header: "Warehouse", render: (c) => c.warehouseCode },
            { key: "s", header: "Status", render: (c) => <StatusBadge status={c.status} /> },
            { key: "l", header: "Lines counted", align: "right", render: (c) => `${formatNumber(c.countedLines)} of ${formatNumber(c.lines)}` },
            { key: "d", header: "Differences", align: "right", render: (c) => formatNumber(c.linesWithDifference) },
            {
              key: "v",
              header: "Variance value",
              align: "right",
              render: (c) => <span className={Number(c.varianceValue) < 0 ? "bad" : ""}>{formatMoney(c.varianceValue)}</span>,
            },
            { key: "at", header: "Started", render: (c) => <span className="nowrap">{formatDate(c.createdAt)}</span> },
          ]}
        />
      )}
    </div>
  );
}

function StorePurchasing({ id }) {
  const suppliers = useLoader(() => api(`/api/admin/inventory/sellers/${id}/suppliers`), [id]);
  return (
    <div className="cc-stack">
      <PagedPanel
        title="Purchase orders"
        path={`/api/admin/inventory/sellers/${id}/purchase-orders`}
        empty="No purchase orders"
        columns={[
          { key: "n", header: "Purchase order", render: (p) => <span className="cell-ref">{p.number}</span> },
          { key: "s", header: "Supplier", render: (p) => p.supplierName },
          { key: "w", header: "Deliver to", render: (p) => p.warehouseCode },
          {
            key: "st",
            header: "Status",
            render: (p) => (
              <div className="row row-wrap" style={{ gap: 6 }}>
                <StatusBadge status={p.status} />
                {p.overdue && <StatusBadge status="FAILED">Overdue</StatusBadge>}
              </div>
            ),
          },
          { key: "d", header: "Ordered", render: (p) => (p.orderDate ? formatDay(p.orderDate) : "-") },
          { key: "e", header: "Expected", render: (p) => (p.expectedDate ? formatDay(p.expectedDate) : "-") },
          { key: "u", header: "Units", align: "right", render: (p) => `${formatNumber(p.totalUnits - p.outstandingUnits)} of ${formatNumber(p.totalUnits)}` },
          { key: "a", header: "Amount", align: "right", render: (p) => formatMoney(p.totalAmount) },
        ]}
      />
      <div className="grid-2">
        <PagedPanel
          title="Goods receipts"
          path={`/api/admin/inventory/sellers/${id}/goods-receipts`}
          size={8}
          empty="Nothing received yet"
          columns={[
            {
              key: "n",
              header: "Receipt",
              render: (g) => (
                <div>
                  <div className="cell-ref">{g.number}</div>
                  <div className="cell-sub">{g.purchaseOrderNumber || "Without a purchase order"}</div>
                </div>
              ),
            },
            { key: "s", header: "Supplier", render: (g) => g.supplierName || "-" },
            { key: "u", header: "Units", align: "right", render: (g) => formatNumber(g.totalUnits) },
            { key: "a", header: "Amount", align: "right", render: (g) => formatMoney(g.totalAmount) },
            { key: "at", header: "Received", render: (g) => <span className="nowrap">{formatDay(g.receivedAt)}</span> },
          ]}
        />
        <Panel title="Suppliers" flush>
          {suppliers.loading && !suppliers.data ? (
            <Loader />
          ) : (
            <DataTable
              rows={suppliers.data || []}
              empty={<Empty title="No suppliers" />}
              columns={[
                {
                  key: "n",
                  header: "Supplier",
                  render: (s) => (
                    <div>
                      <div className="cell-title">{s.name}</div>
                      <div className="cell-sub">{[s.city, s.country].filter(Boolean).join(", ")}</div>
                    </div>
                  ),
                },
                { key: "l", header: "Lead time", align: "right", render: (s) => `${s.leadTimeDays} days` },
                { key: "o", header: "Ordered", align: "right", render: (s) => formatMoney(s.orderedAmount) },
                { key: "t", header: "On time", align: "right", render: (s) => (s.onTimeDeliveryPercent == null ? "-" : `${s.onTimeDeliveryPercent}%`) },
              ]}
            />
          )}
        </Panel>
      </div>
    </div>
  );
}

function StoreCustomers({ id, navigate }) {
  return (
    <PagedPanel
      title="The store's customers"
      sub="Spend and frequency at this store only"
      path={`/api/admin/sales/sellers/${id}/customers`}
      query={{ sort: "spent" }}
      rowKey="customerId"
      size={15}
      onRowClick={(c) => navigate(`/customers/${c.customerId}`)}
      columns={[
        {
          key: "c",
          header: "Customer",
          render: (c) => (
            <div>
              <div className="cell-title">{c.username}</div>
              <div className="cell-sub">{c.email}</div>
            </div>
          ),
        },
        { key: "seg", header: "Segment", render: (c) => <StatusBadge status={c.segment} /> },
        { key: "o", header: "Orders", align: "right", render: (c) => formatNumber(c.orders) },
        { key: "s", header: "Spent", align: "right", render: (c) => <span className="strong">{formatMoney(c.spent)}</span> },
        { key: "l", header: "Last order", render: (c) => <span className="nowrap">{formatDay(c.lastOrderAt)}</span> },
      ]}
    />
  );
}

function StoreMoney({ id, range, setRange }) {
  const data = useLoader(async () => {
    const [earnings, pl] = await Promise.all([api(`/api/admin/sellers/${id}/earnings${qs(range)}`), api(`/api/admin/sellers/${id}/profit-loss${qs(range)}`)]);
    return { earnings, pl };
  }, [id, range.from, range.to]);
  if (data.loading && !data.data) return <Loader text="Loading payments and profit" />;
  if (data.error && !data.data) return <ErrorState message={data.error} onRetry={data.reload} />;
  const { earnings: e, pl } = data.data;
  const profit = Number(pl.netProfit) >= 0;
  return (
    <div className="cc-stack" style={{ opacity: data.loading ? 0.6 : 1 }}>
      <div className="row-between">
        <span className="small muted">Payments and profit {periodName(range.from, range.to)}.</span>
        <PeriodPicker from={range.from} to={range.to} onChange={setRange} />
      </div>
      <div className="stats">
        <Stat label="Paid by customers" value={formatMoney(e.grossSales)} hint={`${formatNumber(e.payments)} payments`} />
        <Stat label="Nexora fees" value={formatMoney(e.platformFees)} hint={`${formatPercent(e.feeRatePercent)} fee rate`} />
        <Stat label="Store earnings" value={formatMoney(e.netEarnings)} hint={`${formatMoney(e.awaitingCollection)} cash still to collect`} />
        <Stat label="Refunded" value={formatMoney(e.refunded)} hint={`${formatNumber(e.refunds)} refunds`} upIsGood={false} />
      </div>
      <div className="grid-2">
        <Panel title="Profit and loss" sub="The store's own statement: sales, cost of goods, Nexora fees and its expenses">
          <table className="statement">
            <tbody>
              <tr>
                <td>Sales (VAT included)</td>
                <td className="num">{formatMoney(pl.revenue)}</td>
              </tr>
              <tr className="indent">
                <td>Less cost of goods sold</td>
                <td className="num">-{formatMoney(pl.costOfGoodsSold)}</td>
              </tr>
              <tr className="sub">
                <td>Gross profit</td>
                <td className="num">{formatMoney(pl.grossProfit)}</td>
              </tr>
              <tr className="indent">
                <td>Less Nexora fees</td>
                <td className="num">-{formatMoney(pl.platformFees)}</td>
              </tr>
              <tr className="indent">
                <td>Less operating expenses</td>
                <td className="num">-{formatMoney(pl.operatingExpenses)}</td>
              </tr>
              <tr className="grand">
                <td>{profit ? "Net profit" : "Net loss"}</td>
                <td className={`num ${profit ? "good" : "bad"}`}>{formatMoney(pl.netProfit)}</td>
              </tr>
            </tbody>
          </table>
        </Panel>
        <Panel title="Month by month" flush>
          <DataTable
            rowKey="month"
            rows={pl.months || []}
            columns={[
              { key: "m", header: "Month", render: (m) => longMonth(m.month) },
              { key: "r", header: "Sales", align: "right", render: (m) => formatMoney(m.revenue) },
              { key: "e", header: "Expenses", align: "right", render: (m) => formatMoney(m.operatingExpenses) },
              {
                key: "n",
                header: "Net",
                align: "right",
                render: (m) => (
                  <span className={`strong ${Number(m.netProfit) < 0 ? "bad" : Number(m.netProfit) > 0 ? "good" : ""}`}>{formatMoney(m.netProfit)}</span>
                ),
              },
            ]}
          />
        </Panel>
      </div>
      <Panel title="Payments by method">
        <HBarList
          items={(e.byMethod || []).map((m) => ({ label: methodName(m.method), value: m.amount, hint: `${formatNumber(m.payments)} payments` }))}
          color="var(--series-2)"
        />
      </Panel>
      <PagedPanel
        title="Payments"
        path="/api/admin/payments"
        query={{ sellerId: id, ...range }}
        columns={paymentColumns({ store: false })}
        empty="No payments in this period"
      />
    </div>
  );
}

export function StorePage({ params, route, navigate }) {
  const id = params.id;
  const [f, set] = useFilters(route, navigate, { tab: "overview" });
  const [dialog, setDialog] = useState(false);
  const detail = useLoader(() => api(`/api/admin/users/${id}`), [id]);
  const range = f.from && f.to ? { from: f.from, to: f.to } : f.tab === "money" ? lastMonths(6) : lastDays(30);
  const setRange = (r) => set({ from: r.from, to: r.to });

  if (detail.loading && !detail.data) return <Loader text="Loading the store" />;
  if (detail.error && !detail.data) return <ErrorState message={detail.error} onRetry={detail.reload} />;
  const u = detail.data.user;
  if (u.role !== "SELLER") {
    return (
      <Empty icon={Store} title="This account is not a store" action={<a href={`#/customers/${u.id}`}>Open the customer's page</a>}>
        {u.username} shops on Nexora as a customer.
      </Empty>
    );
  }
  const s = u.store || {};

  return (
    <>
      <PageHead
        title={storeName(u)}
        badges={
          <>
            <AccountBadge user={u} />
            <VatBadge store={s} />
          </>
        }
        back={{ to: "/stores", label: "Stores" }}
        sub={[s.city && `Ships from ${s.city}`, `on Nexora since ${formatDay(s.memberSince || u.createdAt)}`, `owner ${u.fullName || u.username}`]
          .filter(Boolean)
          .join(", ")}
        actions={
          <>
            <a className="btn btn-secondary" href={`${MARKETPLACE_URL}/#/store/${u.id}`} target="_blank" rel="noreferrer">
              <ExternalLink /> Store page
            </a>
            {u.active ? (
              <Button variant="danger" icon={Ban} onClick={() => setDialog(true)}>
                Suspend store
              </Button>
            ) : (
              <Button variant="primary" icon={RotateCcw} onClick={() => setDialog(true)}>
                Reactivate store
              </Button>
            )}
          </>
        }
      />
      {!u.active && (
        <div style={{ marginBottom: 16 }}>
          <SuspensionNotice user={u} />
        </div>
      )}
      <Tabs className="page-tabs" tabs={TABS} value={f.tab} onChange={(tab) => set({ tab, from: "", to: "" })} />
      <div style={{ marginTop: 16 }}>
        {f.tab === "overview" && <StoreOverview id={id} range={range} setRange={setRange} navigate={navigate} />}
        {f.tab === "orders" && (
          <PagedPanel
            title="Orders"
            path="/api/admin/orders"
            query={{ sellerId: id }}
            size={15}
            columns={orderColumns({ store: false })}
            onRowClick={(o) => navigate(`/orders/${o.id}`)}
            empty="No orders yet"
            actions={
              <a className="btn btn-ghost btn-sm" href={`#/orders?sellerId=${id}`}>
                Filter in Orders
              </a>
            }
          />
        )}
        {f.tab === "products" && (
          <PagedPanel
            title="Products"
            path="/api/admin/catalog/products"
            query={{ sellerId: id, sort: "bestselling" }}
            size={15}
            columns={productColumns({ store: false })}
            onRowClick={(p) => navigate(`/catalogue${qs({ sellerId: id, product: p.id })}`)}
            empty="No products yet"
            actions={
              <a className="btn btn-ghost btn-sm" href={`#/catalogue?sellerId=${id}`}>
                Filter in Catalogue
              </a>
            }
          />
        )}
        {f.tab === "stock" && <StoreStock id={id} />}
        {f.tab === "purchasing" && <StorePurchasing id={id} />}
        {f.tab === "customers" && <StoreCustomers id={id} navigate={navigate} />}
        {f.tab === "reviews" && <StoreReviewsPanel sellerId={id} />}
        {f.tab === "money" && <StoreMoney id={id} range={range} setRange={setRange} />}
        {f.tab === "account" && (
          <div className="grid-main">
            <RecentLogins detail={detail.data} />
            <div className="cc-stack">
              <Panel title="Store profile">
                <KeyValues
                  items={[
                    { label: "Store name", value: s.storeName },
                    { label: "Business phone", value: s.phone || "-" },
                    { label: "Support e-mail", value: s.supportEmail || "-" },
                    { label: "Ships from", value: [s.city, s.country].filter(Boolean).join(", ") || "-" },
                    { label: "Trade licence", value: s.tradeLicenseNumber || "-" },
                    { label: "VAT TRN", value: s.vatNumber ? <span className="ref">{s.vatNumber}</span> : "Not registered" },
                  ]}
                />
                {s.description && (
                  <p className="small muted" style={{ marginTop: 12 }}>
                    {s.description}
                  </p>
                )}
              </Panel>
              <Panel title="Sign-in account">
                <KeyValues
                  items={[
                    { label: "Username", value: u.username },
                    { label: "Owner", value: u.fullName || "-" },
                    { label: "E-mail", value: <a href={`mailto:${u.email}`}>{u.email}</a> },
                    { label: "Phone", value: u.phone || "-" },
                    { label: "Created", value: formatDate(u.createdAt) },
                    { label: "Last sign-in", value: u.lastLoginAt ? formatDate(u.lastLoginAt) : "Never" },
                    u.statusChangedAt && { label: "Status changed", value: formatDate(u.statusChangedAt) },
                  ]}
                />
              </Panel>
            </div>
          </div>
        )}
      </div>
      {dialog && (
        <AccountStatusDialog
          user={u}
          open
          onClose={() => setDialog(false)}
          onDone={() => {
            setDialog(false);
            detail.reload();
          }}
        />
      )}
    </>
  );
}
