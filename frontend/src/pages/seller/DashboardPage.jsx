import {
  Boxes,
  CircleCheck,
  ClipboardList,
  Clock,
  Coins,
  MessageSquare,
  Package,
  PackagePlus,
  Percent,
  Repeat,
  ShoppingBag,
  Star,
  Store,
  TrendingUp,
  Truck,
  Wallet,
} from "lucide-react";
import { api, qs } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { HBarList, SegmentBar, TrendChart } from "../../components/charts.jsx";
import { Button, DataTable, ErrorState, Loader, Meter, Panel, Stat, StatusBadge } from "../../components/ui.jsx";
import { compactMoney, formatMoney, formatNumber, formatPercent, lastDays, longMonth, timeAgo } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { PageHead } from "./common.jsx";
import { useSeller } from "./SellerLayout.jsx";

function Todo({ href, icon: Icon, label, count, tone }) {
  const done = !count;
  return (
    <a className={`todo ${done ? "done" : tone || ""}`} href={href}>
      <span className="todo-icon">{done ? <CircleCheck /> : <Icon />}</span>
      <span>{label}</span>
      <b>{formatNumber(count || 0)}</b>
    </a>
  );
}

/** First steps for a store that has not sold anything yet. */
function GettingStarted({ summary, navigate }) {
  const steps = [
    {
      done: summary.products > 0,
      title: "Add your products",
      text: "Everything you stock, with cost and selling price.",
      action: () => navigate("/seller/products/new"),
      label: "Add a product",
    },
    {
      done: summary.unitsOnHand > 0,
      title: "Record your stock",
      text: "Opening stock, or receive goods from a supplier.",
      action: () => navigate("/seller/receipts"),
      label: "Receive stock",
    },
    {
      done: summary.listedProducts > 0,
      title: "List products on the marketplace",
      text: "Keep the rest as inventory only; customers see listed products.",
      action: () => navigate("/seller/products?listed=false"),
      label: "Choose products to list",
    },
  ];
  return (
    <Panel title="Get your store ready" sub="Three steps and customers across the UAE can order from you.">
      <ol className="todo-list" style={{ margin: 0, padding: 0, listStyle: "none" }}>
        {steps.map((step) => (
          <li key={step.title} className={`todo ${step.done ? "done" : ""}`} style={{ cursor: "default" }}>
            <span className="todo-icon">{step.done ? <CircleCheck /> : <PackagePlus />}</span>
            <span className="grow">
              <span className="strong" style={{ display: "block" }}>
                {step.title}
              </span>
              <span className="tiny muted">{step.text}</span>
            </span>
            {!step.done && (
              <Button size="sm" variant="primary" onClick={step.action}>
                {step.label}
              </Button>
            )}
          </li>
        ))}
      </ol>
    </Panel>
  );
}

export function DashboardPage({ navigate }) {
  const { user } = useAuth();
  const { settings } = useSeller();
  const period = lastDays(30);
  const data = useLoader(async () => {
    const safe = (promise, fallback = null) => promise.catch(() => fallback);
    const [analytics, summary, payments, orders, reviews, budgets, reorder, overdue] = await Promise.all([
      api(`/api/seller/analytics${qs(period)}`),
      api("/api/seller/inventory/summary"),
      safe(api(`/api/seller/payments/summary${qs(period)}`)),
      safe(api(`/api/seller/orders${qs({ size: 6 })}`), { content: [] }),
      safe(api(`/api/seller/reviews${qs({ size: 1 })}`), { awaitingReply: 0 }),
      safe(api("/api/seller/budgets")),
      safe(api("/api/seller/replenishment"), []),
      safe(api(`/api/seller/purchase-orders${qs({ overdue: true, size: 1 })}`), { totalElements: 0 }),
    ]);
    const unread = await safe(api("/api/conversations/unread-count"), { unread: 0 });
    return { analytics, summary, payments, orders, reviews, budgets, reorder, overdue, unread };
  }, []);

  if (data.loading && !data.data) return <Loader text="Loading your dashboard" />;
  if (data.error && !data.data) return <ErrorState message={data.error} onRetry={data.reload} />;

  const { analytics: a, summary: s, payments: p, orders, reviews, budgets, reorder, overdue, unread } = data.data;
  const inStock = Math.max(0, s.products - s.lowStockProducts - s.outOfStockProducts);
  const newStore = s.products === 0 || s.listedProducts === 0;

  return (
    <>
      <PageHead
        title="Dashboard"
        sub={`${user.store?.storeName || "Your store"} over the last 30 days, compared with the 30 days before.${settings?.vacationMode ? " Vacation mode is on." : ""}`}
        actions={
          <>
            <Button icon={Store} onClick={() => navigate(`/store/${user.id}`)}>
              View store
            </Button>
            <Button variant="primary" icon={PackagePlus} onClick={() => navigate("/seller/products/new")}>
              Add product
            </Button>
          </>
        }
      />

      <div className="stack">
        {newStore && <GettingStarted summary={s} navigate={navigate} />}

        <div className="stats">
          <Stat label="Revenue" icon={TrendingUp} value={formatMoney(a.revenue)} delta={a.growth?.revenuePercent} />
          <Stat label="Orders" icon={ShoppingBag} value={formatNumber(a.orders)} delta={a.growth?.ordersPercent} />
          <Stat
            label="Gross profit"
            icon={Coins}
            value={formatMoney(a.grossProfit)}
            delta={a.growth?.grossProfitPercent}
            hint={`${formatPercent(a.grossMarginPercent)} margin`}
          />
          <Stat label="Average order" icon={Percent} value={formatMoney(a.averageOrderValue)} delta={a.growth?.averageOrderValuePercent} />
          <Stat
            label="Net earnings"
            icon={Wallet}
            value={formatMoney(p?.netEarnings || 0)}
            hint={p?.awaitingCollection > 0 ? `${formatMoney(p.awaitingCollection)} cash to collect` : "after Nexora fees"}
          />
        </div>

        <div className="sc-grid-main">
          <Panel
            title="Sales"
            sub="Revenue and gross profit per day"
            actions={
              <Button size="sm" variant="ghost" onClick={() => navigate("/seller/analytics")}>
                Open analytics
              </Button>
            }
          >
            {a.orders > 0 ? (
              <TrendChart
                data={a.trend}
                type={a.trend.length > 45 ? "line" : "bar"}
                height={280}
                series={[
                  { key: "revenue", label: "Revenue" },
                  { key: "profit", label: "Gross profit" },
                ]}
              />
            ) : (
              <p className="small muted" style={{ padding: "40px 0", textAlign: "center" }}>
                Your sales appear here after the first order.
              </p>
            )}
          </Panel>

          <Panel title="Needs your attention">
            <div className="todo-list">
              <Todo href="#/seller/orders?status=CONFIRMED" icon={Truck} label="Orders to ship" count={a.ordersToShip} tone="hot" />
              <Todo href="#/seller/orders?status=PENDING" icon={Clock} label="Orders awaiting payment" count={a.ordersAwaitingPayment} />
              <Todo href="#/seller/inventory?stockStatus=LOW_STOCK" icon={Boxes} label="Products low on stock" count={s.lowStockProducts} tone="warn" />
              <Todo href="#/seller/inventory?stockStatus=OUT_OF_STOCK" icon={Package} label="Products out of stock" count={s.outOfStockProducts} tone="warn" />
              <Todo href="#/seller/replenishment" icon={Repeat} label="Products to reorder" count={reorder.length} />
              <Todo
                href="#/seller/purchase-orders?overdue=true"
                icon={ClipboardList}
                label="Overdue purchase orders"
                count={overdue.totalElements}
                tone="warn"
              />
              <Todo href="#/seller/reviews?replied=false" icon={Star} label="Reviews without a reply" count={reviews.awaitingReply} />
              <Todo href="#/seller/messages" icon={MessageSquare} label="Unread messages" count={unread.unread} tone="hot" />
            </div>
          </Panel>
        </div>

        <div className="sc-grid-3">
          <Panel
            title="Inventory"
            sub={`${formatNumber(s.products)} products in ${formatNumber(s.warehouses)} ${s.warehouses === 1 ? "warehouse" : "warehouses"}`}
            actions={
              <Button size="sm" variant="ghost" onClick={() => navigate("/seller/inventory")}>
                Stock levels
              </Button>
            }
          >
            <div className="stack">
              <dl className="kv-list">
                <div>
                  <dt>Units on hand</dt>
                  <dd>{formatNumber(s.unitsOnHand)}</dd>
                </div>
                <div>
                  <dt>Reserved for orders</dt>
                  <dd>{formatNumber(s.unitsReserved)}</dd>
                </div>
                <div>
                  <dt>Incoming from suppliers</dt>
                  <dd>{formatNumber(s.unitsIncoming)}</dd>
                </div>
                <div>
                  <dt>Stock value at cost</dt>
                  <dd>{formatMoney(s.stockValueAtCost)}</dd>
                </div>
                <div className="total">
                  <dt>Profit in stock</dt>
                  <dd>{formatMoney(s.potentialGrossProfit)}</dd>
                </div>
              </dl>
              <SegmentBar
                segments={[
                  { label: "In stock", value: inStock, color: "var(--good)" },
                  { label: "Low", value: s.lowStockProducts, color: "var(--warn)" },
                  { label: "Out", value: s.outOfStockProducts, color: "var(--bad)" },
                ]}
              />
              <p className="tiny muted" style={{ margin: 0 }}>
                {formatNumber(s.listedProducts)} listed on the marketplace, {formatNumber(s.inventoryOnlyProducts)} kept as inventory only.
              </p>
            </div>
          </Panel>

          <Panel title="Best sellers" sub="By revenue, last 30 days">
            <HBarList
              items={(a.topProducts || []).slice(0, 6).map((product) => ({
                key: product.productId,
                label: product.productName,
                value: product.revenue,
                hint: `${formatNumber(product.unitsSold)} sold`,
              }))}
              format={compactMoney}
              empty="No sales in the last 30 days"
            />
          </Panel>

          <Panel
            title="Budget"
            sub={budgets ? longMonth(budgets.month) : "This month"}
            actions={
              <Button size="sm" variant="ghost" onClick={() => navigate("/seller/finance/budgets")}>
                Budgets
              </Button>
            }
          >
            {budgets && Number(budgets.totalBudget) > 0 ? (
              <div className="stack">
                <div>
                  <div className="row-between small">
                    <span>
                      {formatMoney(budgets.totalSpent)} of {formatMoney(budgets.totalBudget)}
                    </span>
                    <span className="strong">{formatPercent(budgets.percentUsed, 0)}</span>
                  </div>
                  <Meter percent={budgets.percentUsed} tone={Number(budgets.percentUsed) >= 100 ? "bad" : Number(budgets.percentUsed) >= 80 ? "warn" : null} />
                </div>
                {budgets.lines
                  .filter((line) => Number(line.budget) > 0)
                  .sort((x, y) => Number(y.percentUsed) - Number(x.percentUsed))
                  .slice(0, 4)
                  .map((line) => (
                    <div key={line.category} className="row-between small">
                      <span>{line.categoryLabel}</span>
                      <StatusBadge status={line.status}>{formatPercent(line.percentUsed, 0)}</StatusBadge>
                    </div>
                  ))}
              </div>
            ) : (
              <div className="stack-sm">
                <p className="small muted">Set a monthly budget for rent, marketing, shipping and more. Nexora warns you at 80% and when you go over.</p>
                <div>
                  <Button size="sm" onClick={() => navigate("/seller/finance/budgets")}>
                    Set budgets
                  </Button>
                </div>
              </div>
            )}
          </Panel>
        </div>

        <Panel
          title="Latest orders"
          flush
          actions={
            <Button size="sm" variant="ghost" onClick={() => navigate("/seller/orders")}>
              All orders
            </Button>
          }
        >
          <DataTable
            rows={orders.content}
            onRowClick={(order) => navigate(`/seller/orders/${order.id}`)}
            empty={
              <p className="small muted" style={{ padding: 18 }}>
                No orders yet.
              </p>
            }
            columns={[
              { key: "orderNumber", header: "Order", render: (o) => <span className="cell-title">{o.orderNumber}</span> },
              { key: "customer", header: "Customer", render: (o) => o.recipientName || o.customerUsername },
              { key: "units", header: "Items", align: "right", render: (o) => formatNumber(o.units) },
              { key: "total", header: "Total", align: "right", render: (o) => formatMoney(o.totalAmount) },
              { key: "profit", header: "Profit", align: "right", render: (o) => formatMoney(o.grossProfit) },
              { key: "status", header: "Status", render: (o) => <StatusBadge status={o.status} /> },
              { key: "when", header: "Placed", render: (o) => <span className="muted">{timeAgo(o.createdAt)}</span> },
            ]}
          />
        </Panel>
      </div>
    </>
  );
}
