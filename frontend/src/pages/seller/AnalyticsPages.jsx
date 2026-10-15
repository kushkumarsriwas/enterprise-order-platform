import { useState } from "react";
import { Archive, Boxes, Clock, Coins, Gauge, Percent, Receipt, RefreshCw, ShoppingBag, TrendingUp, Truck, Users, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { HBarList, SegmentBar, TrendChart } from "../../components/charts.jsx";
import { DataTable, ErrorState, ExportMenu, Loader, Panel, PillToggle, Select, Stat } from "../../components/ui.jsx";
import { compact, compactMoney, formatDay, formatMoney, formatNumber, formatPercent, lastDays, label, shortDay, plural } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { METHOD_LABELS } from "./OrdersPages.jsx";
import { PageHead, PeriodPicker, useFilters } from "./common.jsx";

const STATUS_COLORS = {
  PENDING: "var(--series-5)",
  CONFIRMED: "var(--series-3)",
  SHIPPED: "var(--series-6)",
  DELIVERED: "var(--series-1)",
  CANCELLED: "var(--line-strong)",
};
const STATUS_NAMES = { PENDING: "Awaiting payment", CONFIRMED: "To ship", SHIPPED: "Shipped", DELIVERED: "Delivered", CANCELLED: "Cancelled" };

/** Period in the URL, defaulting to the last 30 days. */
function usePeriod(route, navigate, extra = {}) {
  const fallback = lastDays(30);
  const [f, set] = useFilters(route, navigate, { from: fallback.from, to: fallback.to, ...extra });
  return [f, set];
}

function Comparison({ a }) {
  const p = a.previousPeriod;
  if (!p) return null;
  const rows = [
    ["Revenue", formatMoney(a.revenue), formatMoney(p.revenue), a.growth?.revenuePercent],
    ["Orders", formatNumber(a.orders), formatNumber(p.orders), a.growth?.ordersPercent],
    ["Units sold", formatNumber(a.unitsSold), formatNumber(p.unitsSold), null],
    ["Gross profit", formatMoney(a.grossProfit), formatMoney(p.grossProfit), a.growth?.grossProfitPercent],
    ["Gross margin", formatPercent(a.grossMarginPercent), formatPercent(p.grossMarginPercent), null],
    ["Average order", formatMoney(a.averageOrderValue), formatMoney(p.averageOrderValue), a.growth?.averageOrderValuePercent],
  ];
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th />
            <th className="num">This period</th>
            <th className="num">Previous period</th>
            <th className="num">Change</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([name, now, before, change]) => (
            <tr key={name}>
              <td>{name}</td>
              <td className="num strong">{now}</td>
              <td className="num">{before}</td>
              <td className="num">
                {change === null || change === undefined ? (
                  <span className="muted">-</span>
                ) : (
                  <span className={Number(change) >= 0 ? "good strong" : "bad strong"}>
                    {Number(change) >= 0 ? "+" : ""}
                    {formatPercent(change)}
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function SalesAnalyticsPage({ route, navigate }) {
  const [f, set] = usePeriod(route, navigate, { granularity: "auto" });
  const [metric, setMetric] = useState("money");
  const analytics = useLoader(() => api(`/api/seller/analytics${qs({ from: f.from, to: f.to, granularity: f.granularity })}`), [f.from, f.to, f.granularity]);
  const a = analytics.data;

  const statusSegments = a
    ? Object.entries(a.ordersByStatus || {}).map(([status, value]) => ({
        label: STATUS_NAMES[status] || label(status),
        value: value.orders,
        color: STATUS_COLORS[status] || "var(--line-strong)",
      }))
    : [];

  return (
    <>
      <PageHead
        title="Sales analytics"
        sub={
          a ? `${formatDay(a.from)} to ${formatDay(a.to)}, compared with the same number of days before.` : "Revenue, profit, products, regions and customers."
        }
        actions={
          <>
            <PeriodPicker from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
            <Select
              className="select-sm"
              value={f.granularity}
              aria-label="Group by"
              onChange={(e) => set({ granularity: e.target.value })}
              options={[
                { value: "auto", label: "Group automatically" },
                { value: "day", label: "By day" },
                { value: "week", label: "By week" },
                { value: "month", label: "By month" },
              ]}
            />
            <ExportMenu path="/api/seller/reports/sales/{format}" query={qs({ from: f.from, to: f.to })} label="Sales report" />
          </>
        }
      />
      {analytics.loading && !a ? (
        <Loader text="Crunching the numbers" />
      ) : analytics.error && !a ? (
        <ErrorState message={analytics.error} onRetry={analytics.reload} />
      ) : (
        <div className="stack" style={{ opacity: analytics.loading ? 0.6 : 1 }}>
          <div className="stats">
            <Stat label="Revenue" icon={TrendingUp} value={formatMoney(a.revenue)} delta={a.growth?.revenuePercent} />
            <Stat
              label="Orders"
              icon={ShoppingBag}
              value={formatNumber(a.orders)}
              delta={a.growth?.ordersPercent}
              hint={`${formatNumber(a.unitsSold)} units`}
            />
            <Stat label="Gross profit" icon={Coins} value={formatMoney(a.grossProfit)} delta={a.growth?.grossProfitPercent} />
            <Stat label="Gross margin" icon={Percent} value={formatPercent(a.grossMarginPercent)} hint={`cost of goods ${compactMoney(a.costOfGoodsSold)}`} />
            <Stat label="Average order" icon={Receipt} value={formatMoney(a.averageOrderValue)} delta={a.growth?.averageOrderValuePercent} />
          </div>
          <div className="stats">
            <Stat label="Customers" icon={Users} value={formatNumber(a.customers)} hint={`${formatPercent(a.repeatCustomerRatePercent, 0)} came back`} />
            <Stat label="Cancelled" icon={XCircle} value={formatPercent(a.cancellationRatePercent)} hint="of orders" upIsGood={false} />
            <Stat
              label="Time to dispatch"
              icon={Truck}
              value={a.averageDispatchHours === null || a.averageDispatchHours === undefined ? "-" : `${Number(a.averageDispatchHours).toFixed(1)} h`}
              hint="payment to shipping, on average"
            />
            <Stat label="VAT collected" icon={Receipt} value={formatMoney(a.vatCollected)} hint="5% included in prices" />
            <Stat
              label="To ship now"
              icon={Clock}
              value={formatNumber(a.ordersToShip)}
              hint={`${formatNumber(a.ordersAwaitingPayment)} awaiting payment`}
              attention={a.ordersToShip > 0}
            />
          </div>

          <Panel
            title={metric === "money" ? "Revenue and gross profit" : "Orders and units"}
            sub={`Per ${a.granularity}`}
            actions={
              <PillToggle
                label="Chart"
                value={metric}
                onChange={setMetric}
                options={[
                  { value: "money", label: "Money" },
                  { value: "volume", label: "Volume" },
                ]}
              />
            }
          >
            {a.orders === 0 ? (
              <p className="small muted" style={{ padding: "48px 0", textAlign: "center" }}>
                No orders in this period.
              </p>
            ) : metric === "money" ? (
              <TrendChart
                data={a.trend}
                type={a.trend.length > 45 ? "line" : "bar"}
                height={290}
                series={[
                  { key: "revenue", label: "Revenue" },
                  { key: "profit", label: "Gross profit" },
                ]}
              />
            ) : (
              <TrendChart
                data={a.trend}
                type="bar"
                height={290}
                format={(v) => formatNumber(v)}
                axisFormat={compact}
                series={[
                  { key: "orders", label: "Orders" },
                  { key: "units", label: "Units" },
                ]}
              />
            )}
          </Panel>

          <div className="sc-grid-2">
            <Panel title="Compared with the previous period">
              <Comparison a={a} />
            </Panel>
            <Panel title="Orders by status" sub="Orders placed in the period, by where they are now">
              <SegmentBar segments={statusSegments} />
            </Panel>
          </div>

          <div className="sc-grid-3">
            <Panel title="Departments" sub="Revenue and share">
              <HBarList
                items={(a.categories || []).map((c) => ({
                  key: c.category,
                  label: c.category || "No department",
                  value: c.revenue,
                  hint: formatPercent(c.sharePercent, 0),
                }))}
                format={compactMoney}
              />
            </Panel>
            <Panel title="Emirates" sub="Where your customers are">
              <HBarList
                items={(a.regions || []).map((r) => ({ key: r.region, label: r.region || "Unknown", value: r.revenue, hint: plural(r.orders, "order") }))}
                format={compactMoney}
              />
            </Panel>
            <Panel title="Payment methods">
              <HBarList
                items={Object.entries(a.paymentMethods || {}).map(([method, value]) => ({
                  key: method,
                  label: METHOD_LABELS[method] || label(method),
                  value: value.amount,
                  hint: plural(value.orders, "order"),
                }))}
                format={compactMoney}
              />
            </Panel>
          </div>

          <div className="sc-grid-2">
            <Panel title="Best-selling products" flush>
              <DataTable
                rowKey="productId"
                rows={a.topProducts || []}
                onRowClick={(p) => navigate(`/seller/products/${p.productId}`)}
                empty={
                  <p className="small muted" style={{ padding: 16 }}>
                    No sales in this period.
                  </p>
                }
                columns={[
                  { key: "name", header: "Product", render: (p) => <span className="cell-title">{p.productName}</span> },
                  { key: "units", header: "Sold", align: "right", render: (p) => formatNumber(p.unitsSold) },
                  { key: "revenue", header: "Revenue", align: "right", render: (p) => formatMoney(p.revenue) },
                  { key: "profit", header: "Profit", align: "right", render: (p) => formatMoney(p.grossProfit) },
                  { key: "margin", header: "Margin", align: "right", render: (p) => formatPercent(p.marginPercent) },
                ]}
              />
            </Panel>
            <Panel title="Top customers" flush>
              <DataTable
                rowKey="customerId"
                rows={a.topCustomers || []}
                onRowClick={(c) => navigate(`/seller/orders${qs({ q: c.customerUsername })}`)}
                empty={
                  <p className="small muted" style={{ padding: 16 }}>
                    No customers in this period.
                  </p>
                }
                columns={[
                  { key: "name", header: "Customer", render: (c) => <span className="cell-title">{c.customerUsername}</span> },
                  { key: "orders", header: "Orders", align: "right", render: (c) => formatNumber(c.orders) },
                  { key: "spent", header: "Spent", align: "right", render: (c) => formatMoney(c.spent) },
                ]}
              />
            </Panel>
          </div>
        </div>
      )}
    </>
  );
}

const INVENTORY_REPORTS = [
  { id: "valuation", label: "Valuation" },
  { id: "abc", label: "ABC analysis" },
  { id: "dead-stock", label: "Dead stock" },
  { id: "shrinkage", label: "Adjustments by reason" },
  { id: "flow", label: "Stock in and out" },
];

export function InventoryAnalyticsPage({ route, navigate }) {
  const [f, set] = usePeriod(route, navigate, { deadStockDays: "90" });
  const [report, setReport] = useState("valuation");
  const query = { from: f.from, to: f.to, deadStockDays: f.deadStockDays };
  const analytics = useLoader(() => api(`/api/seller/inventory/analytics${qs(query)}`), [JSON.stringify(query)]);
  const a = analytics.data;

  return (
    <>
      <PageHead
        title="Inventory analytics"
        sub="Stock value, how fast stock turns, what sells (ABC), what does not (dead stock) and where stock is lost."
        actions={
          <>
            <PeriodPicker from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
            <Select
              className="select-sm"
              value={report}
              onChange={(e) => setReport(e.target.value)}
              options={INVENTORY_REPORTS.map((r) => ({ value: r.id, label: r.label }))}
              aria-label="Report"
            />
            <ExportMenu path={`/api/seller/reports/inventory/${report}/{format}`} query={qs(query)} label="Download" />
          </>
        }
      />
      {analytics.loading && !a ? (
        <Loader text="Analysing your stock" />
      ) : analytics.error && !a ? (
        <ErrorState message={analytics.error} onRetry={analytics.reload} />
      ) : (
        <div className="stack" style={{ opacity: analytics.loading ? 0.6 : 1 }}>
          <div className="stats">
            <Stat
              label="Stock value at cost"
              icon={Boxes}
              value={formatMoney(a.stockValueAtCost)}
              hint={`${formatMoney(a.stockValueAtPrice)} at selling price`}
            />
            <Stat label="Turnover" icon={RefreshCw} value={`${Number(a.turnoverRatio || 0).toFixed(2)}x`} hint="cost of goods sold / stock value" />
            <Stat
              label="Days of inventory"
              icon={Clock}
              value={a.daysOfInventory === null || a.daysOfInventory === undefined ? "-" : formatNumber(a.daysOfInventory)}
              hint="at the current pace of sales"
            />
            <Stat
              label="Sell-through"
              icon={Gauge}
              value={formatPercent(a.sellThroughPercent)}
              hint={`${formatNumber(a.unitsSold)} sold of what was available`}
            />
            <Stat
              label="Dead stock"
              icon={Archive}
              value={formatMoney(a.deadStockValue)}
              hint={`not sold for ${f.deadStockDays} days`}
              attention={Number(a.deadStockValue) > 0}
            />
          </div>

          <div className="sc-grid-2">
            <Panel title="Stock health" sub={`${formatNumber(a.unitsOnHand)} units on hand`}>
              <div className="stack">
                <SegmentBar
                  segments={[
                    { label: "In stock", value: a.health.inStock, color: "var(--good)" },
                    { label: "Low", value: a.health.lowStock, color: "var(--warn)" },
                    { label: "Out of stock", value: a.health.outOfStock, color: "var(--bad)" },
                  ]}
                />
                <p className="small muted" style={{ margin: 0 }}>
                  {plural(a.health.excessStock, "product holds", "products hold")} more than 90 days of sales. {formatNumber(a.health.listed)} listed on the
                  marketplace, {formatNumber(a.health.inventoryOnly)} kept as inventory only.
                </p>
              </div>
            </Panel>
            <Panel title="Sales and purchases" sub="In the period">
              <dl className="kv-list">
                <div>
                  <dt>Goods received from suppliers</dt>
                  <dd>{formatMoney(a.purchaseSpend)}</dd>
                </div>
                <div>
                  <dt>Revenue</dt>
                  <dd>{formatMoney(a.revenue)}</dd>
                </div>
                <div>
                  <dt>Cost of goods sold</dt>
                  <dd>{formatMoney(a.costOfGoodsSold)}</dd>
                </div>
                <div className="total">
                  <dt>Gross profit</dt>
                  <dd>{formatMoney(a.grossProfit)}</dd>
                </div>
              </dl>
            </Panel>
          </div>

          <Panel title="Stock in and out" sub="Units received and returned against units sold and removed, per day">
            {a.flow?.some((p) => p.unitsIn || p.unitsOut) ? (
              <TrendChart
                data={a.flow.map((p) => ({ ...p, label: shortDay(p.date) }))}
                type="bar"
                height={240}
                format={(v) => `${formatNumber(v)} units`}
                axisFormat={compact}
                series={[
                  { key: "unitsIn", label: "In" },
                  { key: "unitsOut", label: "Out" },
                ]}
              />
            ) : (
              <p className="small muted" style={{ padding: "36px 0", textAlign: "center" }}>
                No stock movements in this period.
              </p>
            )}
          </Panel>

          <div className="sc-grid-2">
            <Panel title="Value by department">
              <HBarList
                items={(a.valueByCategory || []).map((v) => ({ key: v.label, label: v.label, value: v.valueAtCost, hint: formatPercent(v.sharePercent, 0) }))}
                format={compactMoney}
                empty="No stock"
              />
            </Panel>
            <Panel title="Value by warehouse">
              <HBarList
                items={(a.valueByWarehouse || []).map((v) => ({ key: v.label, label: v.label, value: v.valueAtCost, hint: `${formatNumber(v.units)} units` }))}
                format={compactMoney}
                empty="No stock"
              />
            </Panel>
          </div>

          <div className="sc-grid-main">
            <Panel
              title="ABC analysis"
              sub="A: products that make the first 80% of revenue. B: the next 15%. C: the rest. Keep A products in stock first."
              flush
            >
              <DataTable
                rowKey="abcClass"
                rows={a.abcSummary || []}
                columns={[
                  { key: "class", header: "Class", render: (c) => <span className="strong">{c.abcClass}</span> },
                  { key: "products", header: "Products", align: "right", render: (c) => formatNumber(c.products) },
                  { key: "revenue", header: "Revenue", align: "right", render: (c) => formatMoney(c.revenue) },
                  { key: "share", header: "Share", align: "right", render: (c) => formatPercent(c.revenueSharePercent) },
                  { key: "value", header: "Stock value", align: "right", render: (c) => formatMoney(c.stockValueAtCost) },
                ]}
              />
            </Panel>
            <Panel title="Adjustments by reason" sub="Stock written off or found, at cost">
              <HBarList
                items={(a.shrinkage || []).map((s) => ({ key: s.reason, label: label(s.reason), value: s.valueAtCost, hint: plural(s.units, "unit") }))}
                format={formatMoney}
                color="var(--series-2)"
                empty="No adjustments in this period"
              />
            </Panel>
          </div>

          <div className="sc-grid-2">
            <Panel title="Fastest movers" flush>
              <DataTable
                rowKey="productId"
                rows={a.topMovers || []}
                onRowClick={(p) => navigate(`/seller/products/${p.productId}`)}
                empty={
                  <p className="small muted" style={{ padding: 16 }}>
                    No sales in this period.
                  </p>
                }
                columns={[
                  {
                    key: "name",
                    header: "Product",
                    render: (p) => (
                      <div>
                        <div className="cell-title">{p.name}</div>
                        <div className="cell-sub">
                          {p.sku} - class {p.abcClass}
                        </div>
                      </div>
                    ),
                  },
                  { key: "sold", header: "Sold", align: "right", render: (p) => formatNumber(p.unitsSold) },
                  { key: "onHand", header: "On hand", align: "right", render: (p) => formatNumber(p.onHand) },
                  {
                    key: "cover",
                    header: "Days left",
                    align: "right",
                    render: (p) => (p.daysOfCover === null || p.daysOfCover === undefined ? "-" : formatNumber(p.daysOfCover)),
                  },
                ]}
              />
            </Panel>
            <Panel
              title={`Dead stock (no sale for ${f.deadStockDays} days)`}
              flush
              actions={
                <Select
                  className="select-sm"
                  value={f.deadStockDays}
                  onChange={(e) => set({ deadStockDays: e.target.value })}
                  aria-label="Days without a sale"
                  options={["30", "60", "90", "180", "365"].map((d) => ({ value: d, label: `${d} days` }))}
                />
              }
            >
              <DataTable
                rowKey="productId"
                rows={a.deadStock || []}
                onRowClick={(p) => navigate(`/seller/products/${p.productId}`)}
                empty={
                  <p className="small muted" style={{ padding: 16 }}>
                    Everything you stock has sold recently.
                  </p>
                }
                columns={[
                  {
                    key: "name",
                    header: "Product",
                    render: (p) => (
                      <div>
                        <div className="cell-title">{p.name}</div>
                        <div className="cell-sub">{p.sku}</div>
                      </div>
                    ),
                  },
                  { key: "onHand", header: "On hand", align: "right", render: (p) => formatNumber(p.onHand) },
                  { key: "value", header: "Value", align: "right", render: (p) => formatMoney(p.stockValueAtCost) },
                  { key: "last", header: "Last sold", render: (p) => (p.lastSoldAt ? formatDay(p.lastSoldAt) : <span className="muted">Never</span>) },
                ]}
              />
            </Panel>
          </div>

          <div className="sc-grid-2">
            <Panel title="Purchases by supplier">
              <HBarList
                items={(a.spendBySupplier || []).map((s) => ({ key: s.label, label: s.label, value: s.amount, hint: `${formatNumber(s.units)} units` }))}
                format={compactMoney}
                empty="No goods received in this period"
              />
            </Panel>
            <Panel title="Purchases by month">
              <HBarList
                items={(a.spendByMonth || []).map((s) => ({
                  key: s.label,
                  label: s.label,
                  value: s.amount,
                  hint: plural(s.receipts, "delivery", "deliveries"),
                }))}
                format={compactMoney}
                color="var(--series-3)"
                empty="No goods received in this period"
              />
            </Panel>
          </div>
        </div>
      )}
    </>
  );
}
