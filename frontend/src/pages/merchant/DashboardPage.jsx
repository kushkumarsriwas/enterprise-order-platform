import { AlertTriangle, ArrowRight, Boxes, Clock, CreditCard, Package, PackagePlus, ShoppingBag, Store, TrendingUp, Truck, Wallet } from "lucide-react";
import { api, qs } from "../../api.js";
import { compactMoney, eachDay, formatDate, formatMoney, formatNumber, formatPercent, label, shortDay } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { BarList, ChartCard, TrendChart } from "../../components/charts.jsx";
import { Alert, Badge, StatTile } from "../../components/ui.jsx";
import { ReportButtons, usePeriod } from "./common.jsx";

const STATUS_ORDER = ["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"];
const METHOD_NAMES = { CARD: "Card", UPI: "UPI", COD: "Cash on delivery" };

export default function MerchantDashboard({ user, toast, navigate }) {
  const period = usePeriod("30d", ["7d", "30d", "90d", "mtd"]);
  const { from, to } = period.range;
  const analytics = useLoader(() => api(`/api/merchant/analytics${qs({ from, to })}`), [from, to]);
  const stock = useLoader(() => api("/api/merchant/inventory/summary"), []);
  const lowStock = useLoader(() => api("/api/merchant/inventory/low-stock"), []);
  const toShip = useLoader(() => api("/api/merchant/orders?status=CONFIRMED&size=5"), []);
  const a = analytics.data;
  const s = stock.data;

  const byDay = new Map((a?.daily || []).map((d) => [d.date, d]));
  const trend = eachDay(from, to).map((date) => ({
    date,
    revenue: Number(byDay.get(date)?.revenue || 0),
    profit: Number(byDay.get(date)?.profit || 0),
    orders: byDay.get(date)?.orders || 0,
  }));
  const statusItems = STATUS_ORDER.map((status) => ({
    key: status,
    label: label(status === "CONFIRMED" ? "To ship" : status === "PENDING" ? "Awaiting payment" : status),
    value: a?.ordersByStatus?.[status]?.orders || 0,
    note: formatMoney(a?.ordersByStatus?.[status]?.amount || 0),
  }));
  const methods = Object.entries(a?.paymentMethods || {}).map(([method, v]) => ({ key: method, label: METHOD_NAMES[method] || method, value: Number(v.amount), note: `${v.orders} order${v.orders === 1 ? "" : "s"}` }));
  const newStore = s && s.products === 0;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{user.store?.storeName || "Dashboard"}</h1>
          <p className="muted">Sales, profit and stock at a glance. Sales count paid and cash-on-delivery orders.</p>
        </div>
        <div className="row wrap">
          {period.picker}
          <ReportButtons path="/api/merchant/reports/sales" range={{ from, to }} toast={toast} compact />
        </div>
      </div>
      <Alert>{analytics.error || stock.error}</Alert>

      {newStore && (
        <div className="card stack">
          <h2>Welcome to your back office</h2>
          <p className="muted">Three steps to your first sale:</p>
          <ol className="stack tight" style={{ margin: 0, paddingLeft: 20 }}>
            <li>Add your products with their cost and selling price. Keep internal items unlisted to track them as inventory only.</li>
            <li>Turn on "List on marketplace" for the products customers can buy.</li>
            <li>When an order is paid, the stock updates automatically and the order appears under Orders, ready to ship.</li>
          </ol>
          <div className="row">
            <a className="btn btn-primary" href="#/merchant/products?new=1">
              <PackagePlus size={16} /> Add your first product
            </a>
            <a className="btn" href={`#/store/${user.id}`}>
              <Store size={16} /> View your storefront
            </a>
          </div>
        </div>
      )}

      <div className={`stats ${analytics.loading && a ? "refetching" : ""}`}>
        <StatTile hero icon={TrendingUp} label="Revenue" value={a ? formatMoney(a.revenue) : "-"} hint={a ? `${shortDay(from)} to ${shortDay(to)}` : null} />
        <StatTile hero icon={Wallet} label="Gross profit" value={a ? formatMoney(a.grossProfit) : "-"} hint={a ? `${formatPercent(a.grossMarginPercent)} margin after cost of goods` : null} />
        <StatTile icon={ShoppingBag} label="Orders sold" value={a ? formatNumber(a.orders) : "-"} hint={a ? `${formatNumber(a.unitsSold)} units` : null} />
        <StatTile icon={CreditCard} label="Average order value" value={a ? formatMoney(a.averageOrderValue) : "-"} />
      </div>
      <div className="stats">
        <StatTile icon={Truck} label="Orders to ship" value={a ? formatNumber(a.ordersToShip) : "-"} hint={<a className="link" href="#/merchant/orders">Open orders</a>} />
        <StatTile icon={Clock} label="Awaiting payment" value={a ? formatNumber(a.ordersAwaitingPayment) : "-"} hint="Cancelled automatically if unpaid" />
        <StatTile icon={AlertTriangle} label="Low or out of stock" value={s ? formatNumber(s.lowStockProducts) : "-"} hint={s ? `${s.outOfStockProducts} out of stock` : null} />
        <StatTile icon={Boxes} label="Stock value at cost" value={s ? formatMoney(s.stockValueAtCost) : "-"} hint={s ? `${formatNumber(s.unitsOnHand)} units in ${s.products} products` : null} />
      </div>

      <ChartCard
        title="Revenue and gross profit per day"
        subtitle="AED, paid and cash-on-delivery orders"
        loading={analytics.loading && Boolean(a)}
        empty={a && a.orders === 0 ? "No sales in this period yet. Paid orders will appear here." : null}
        table={{
          columns: [
            { key: "date", label: "Date", format: shortDay },
            { key: "orders", label: "Orders", numeric: true },
            { key: "revenue", label: "Revenue", numeric: true, format: (v) => formatMoney(v) },
            { key: "profit", label: "Gross profit", numeric: true, format: (v) => formatMoney(v) },
          ],
          rows: trend.filter((d) => d.orders > 0),
        }}
      >
        <TrendChart
          data={trend}
          xKey="date"
          labelFormat={shortDay}
          valueFormat={(v) => formatMoney(v)}
          series={[
            { key: "revenue", name: "Revenue" },
            { key: "profit", name: "Gross profit" },
          ]}
        />
      </ChartCard>

      <div className="two-col even">
        <ChartCard
          title="Best sellers"
          subtitle="By revenue in this period"
          empty={a && a.topProducts.length === 0 ? "Your best-selling products will be ranked here." : null}
          table={{
            columns: [
              { key: "productName", label: "Product" },
              { key: "unitsSold", label: "Units", numeric: true },
              { key: "revenue", label: "Revenue", numeric: true, format: (v) => formatMoney(v) },
              { key: "grossProfit", label: "Gross profit", numeric: true, format: (v) => formatMoney(v) },
              { key: "marginPercent", label: "Margin", numeric: true, format: formatPercent },
            ],
            rows: a?.topProducts || [],
          }}
        >
          <BarList
            items={(a?.topProducts || []).slice(0, 6).map((p) => ({
              key: p.productId,
              label: p.productName,
              value: Number(p.revenue),
              note: `${p.unitsSold} sold, ${formatPercent(p.marginPercent)} margin`,
            }))}
            valueFormat={compactMoney}
          />
        </ChartCard>
        <ChartCard
          title="Order pipeline"
          subtitle="Orders placed in this period by status"
          table={{
            columns: [
              { key: "label", label: "Status" },
              { key: "value", label: "Orders", numeric: true },
              { key: "note", label: "Value", numeric: true },
            ],
            rows: statusItems,
          }}
        >
          <BarList items={statusItems} valueFormat={formatNumber} />
        </ChartCard>
      </div>

      <div className="two-col even">
        <ChartCard
          title="Payment methods"
          subtitle="Share of sales by how customers paid"
          empty={a && methods.length === 0 ? "No payments in this period yet." : null}
          table={{
            columns: [
              { key: "label", label: "Method" },
              { key: "note", label: "Orders" },
              { key: "value", label: "Sales", numeric: true, format: (v) => formatMoney(v) },
            ],
            rows: methods,
          }}
        >
          <BarList items={methods} valueFormat={compactMoney} />
        </ChartCard>
        <section className="card">
          <div className="card-head">
            <h3>Top customers</h3>
          </div>
          {a?.topCustomers.length ? (
            <table className="table">
              <thead>
                <tr>
                  <th>Customer</th>
                  <th className="num">Orders</th>
                  <th className="num">Spent</th>
                </tr>
              </thead>
              <tbody>
                {a.topCustomers.map((c) => (
                  <tr key={c.customerId}>
                    <td>{c.customerUsername}</td>
                    <td className="num">{c.orders}</td>
                    <td className="num">{formatMoney(c.spent)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="muted">Your most valuable customers will be listed here.</p>
          )}
        </section>
      </div>

      <div className="two-col even">
        <section className="card">
          <div className="card-head">
            <h3>Ready to ship</h3>
            <a className="link small" href="#/merchant/orders">
              All orders <ArrowRight size={13} />
            </a>
          </div>
          {toShip.data?.content.length ? (
            <table className="table clickable">
              <tbody>
                {toShip.data.content.map((o) => (
                  <tr key={o.id} onClick={() => navigate(`/merchant/orders/${o.id}`)}>
                    <td>
                      <strong>{o.orderNumber}</strong>
                      <span className="sub">
                        {o.customerUsername} - {formatDate(o.createdAt)}
                      </span>
                    </td>
                    <td>
                      <Badge value={o.paymentState} friendly />
                    </td>
                    <td className="num">{formatMoney(o.totalAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="muted">Nothing to ship right now.</p>
          )}
        </section>
        <section className="card">
          <div className="card-head">
            <h3>Low stock</h3>
            <a className="link small" href="#/merchant/inventory">
              Inventory <ArrowRight size={13} />
            </a>
          </div>
          {lowStock.data?.length ? (
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Available</th>
                  <th className="num">Reorder at</th>
                </tr>
              </thead>
              <tbody>
                {lowStock.data.slice(0, 6).map((item) => (
                  <tr key={item.productId}>
                    <td>
                      {item.productName}
                      <span className="sub">{item.sku}</span>
                    </td>
                    <td className={`num ${item.availableQuantity === 0 ? "neg" : ""}`}>{item.availableQuantity}</td>
                    <td className="num">{item.reorderLevel}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="muted">
              <Package size={14} /> All products are above their reorder level.
            </p>
          )}
        </section>
      </div>
    </div>
  );
}
