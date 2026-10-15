import {
  ArrowDownRight,
  ArrowUpRight,
  Banknote,
  CircleCheck,
  CreditCard,
  Hourglass,
  KeyRound,
  Landmark,
  MailWarning,
  MessageSquareWarning,
  Package,
  PackageX,
  Receipt,
  ShoppingBag,
  Truck,
  UserX,
} from "lucide-react";
import { api, qs } from "../api.js";
import { HBarList, TrendChart } from "../components/charts.jsx";
import { PageHead, PeriodPicker, periodName, useFilters } from "../components/common.jsx";
import { METHOD_NAMES } from "../components/tables.jsx";
import { DataTable, ErrorState, ExportMenu, Loader, Notice, Panel } from "../components/ui.jsx";
import { compact, formatAmount, formatMoney, formatNumber, formatPercent, label, lastDays, shortDay } from "../format.js";
import { useLoader } from "../hooks.jsx";

/** Each service answers on its own: when one is down, the rest of the overview still shows (with a note). */
const settle = (promise) => promise.then((data) => ({ data })).catch((error) => ({ error: error.message }));

function Delta({ value, upIsGood = true }) {
  if (value === null || value === undefined) return <span>no sales in the period before</span>;
  const n = Number(value);
  const good = upIsGood ? n >= 0 : n <= 0;
  const Icon = n >= 0 ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={good ? "good" : "bad"} style={{ display: "inline-flex", alignItems: "center", gap: 3 }}>
      <Icon /> {formatPercent(Math.abs(n))} vs period before
    </span>
  );
}

/** A big amount with a small currency, so six figures fit in one band. */
function Money({ value }) {
  const n = Number(value || 0);
  return (
    <>
      {n < 0 ? "-" : ""}
      <span className="cur">AED</span>
      {formatAmount(Math.abs(n))}
    </>
  );
}

function PulseItem({ icon: Icon, label: text, value, foot }) {
  return (
    <div className="pulse-item">
      <dt>
        <Icon /> {text}
      </dt>
      <dd>{value}</dd>
      {foot && <div className="pulse-foot">{foot}</div>}
    </div>
  );
}

function QueueItem({ to, icon: Icon, title, sub, count, tone = "hot" }) {
  const clear = !count;
  return (
    <a className={`queue-item ${clear ? "clear" : tone}`} href={`#${to}`}>
      <span className="rail" />
      <span className="q-icon">{clear ? <CircleCheck /> : <Icon />}</span>
      <span className="q-text">
        <b>{title}</b>
        <small>{sub}</small>
      </span>
      <span className="q-count">{formatNumber(count || 0)}</span>
    </a>
  );
}

export function OverviewPage({ route, navigate }) {
  const [f, set] = useFilters(route, navigate, {});
  const range = f.from && f.to ? { from: f.from, to: f.to } : lastDays(30);
  const overview = useLoader(async () => {
    const q = qs(range);
    const [sales, payments, inventory, accounts, messaging, orders, failedEmails] = await Promise.all([
      settle(api(`/api/admin/sales/overview${q}`)),
      settle(api(`/api/admin/payments/summary${q}`)),
      settle(api("/api/admin/inventory/summary")),
      settle(api("/api/admin/users/stats")),
      settle(api("/api/admin/conversations/stats")),
      settle(api("/api/admin/orders/counts")),
      settle(api(`/api/admin/emails${qs({ status: "FAILED", size: 1 })}`)),
    ]);
    return { sales, payments, inventory, accounts, messaging, orders, failedEmails };
  }, [range.from, range.to]);

  const d = overview.data;
  const a = d?.sales.data?.analytics;
  const p = d?.payments.data;
  const inv = d?.inventory.data;
  const acc = d?.accounts.data;
  const msg = d?.messaging.data;
  const counts = d?.orders.data;
  const unavailable = d
    ? [
        d.sales.error && "sales",
        d.payments.error && "payments",
        d.inventory.error && "stock",
        d.accounts.error && "accounts",
        d.messaging.error && "messages",
      ].filter(Boolean)
    : [];

  // one row per store: stock from the inventory service, sales from the order service, fees from the payment service
  const stores = (inv?.bySeller || [])
    .map((s) => {
      const sold = d.sales.data?.topSellers?.find((t) => t.sellerId === s.sellerId);
      const paid = p?.topSellers?.find((t) => t.sellerId === s.sellerId);
      return { ...s, orders: sold?.orders || 0, sales: Number(sold?.sales || 0), fees: Number(paid?.platformFees || 0) };
    })
    .sort((x, y) => y.sales - x.sales);

  const signups = (acc?.signupsLast30Days || []).map((day) => ({ ...day, label: shortDay(day.date) }));

  return (
    <>
      <PageHead
        title="Overview"
        sub={`The whole Nexora marketplace, ${periodName(range.from, range.to)}. Sales include 5% VAT; Nexora keeps a fee on every collected payment.`}
        actions={
          <>
            <PeriodPicker from={range.from} to={range.to} onChange={(r) => set(r)} />
            <ExportMenu path="/api/admin/sales/report/{format}" query={qs(range)} label="Sales report" />
          </>
        }
      />
      {overview.loading && !d ? (
        <Loader text="Reading every service" />
      ) : overview.error && !d ? (
        <ErrorState message={overview.error} onRetry={overview.reload} />
      ) : (
        <div className="cc-stack" style={{ opacity: overview.loading ? 0.6 : 1 }}>
          {unavailable.length > 0 && (
            <Notice tone="warn">
              Some figures are missing because these parts of the platform did not answer: {unavailable.join(", ")}. The rest is up to date.
            </Notice>
          )}

          <dl className="pulse" style={{ margin: 0 }}>
            <PulseItem
              icon={ShoppingBag}
              label="Marketplace sales"
              value={a ? <Money value={a.revenue} /> : "-"}
              foot={a && <Delta value={a.growth?.revenuePercent} />}
            />
            <PulseItem icon={Package} label="Orders" value={a ? formatNumber(a.orders) : "-"} foot={a && <Delta value={a.growth?.ordersPercent} />} />
            <PulseItem
              icon={Receipt}
              label="Average order"
              value={a ? <Money value={a.averageOrderValue} /> : "-"}
              foot={a && <Delta value={a.growth?.averageOrderValuePercent} />}
            />
            <PulseItem
              icon={Landmark}
              label="Nexora fees"
              value={p ? <Money value={p.platformRevenue} /> : "-"}
              foot={p && `${formatPercent(p.feeRatePercent)} of collected payments`}
            />
            <PulseItem
              icon={Banknote}
              label="Paid out to stores"
              value={p ? <Money value={p.paidOutToSellers} /> : "-"}
              foot={p && `${formatMoney(p.awaitingCollection)} cash still to collect`}
            />
            <PulseItem
              icon={CreditCard}
              label="Payment success"
              value={p ? formatPercent(p.successRatePercent) : "-"}
              foot={p && `${formatNumber(p.failedAttempts)} failed, ${formatNumber(p.refunds)} refunded`}
            />
          </dl>

          <div className="grid-main">
            <Panel
              title="Marketplace sales"
              sub={a ? `${GRAIN[a.granularity] || "Daily"} sales across every store, VAT included` : "Not available right now"}
              actions={
                <a className="btn btn-ghost btn-sm" href="#/orders">
                  All orders
                </a>
              }
            >
              {a ? (
                <TrendChart
                  data={a.trend}
                  series={[{ key: "revenue", label: "Sales" }]}
                  height={400}
                  labelFormat={(text, point) => `${text} - ${formatNumber(point?.orders)} orders`}
                />
              ) : (
                <p className="small muted">The order service did not answer. Try again in a moment.</p>
              )}
            </Panel>

            <Panel title="Needs a decision" sub="What waits for you right now" flush>
              <div className="queue">
                <QueueItem
                  to="/messages?reported=true"
                  icon={MessageSquareWarning}
                  title="Reported conversations"
                  sub="A store or customer asked Nexora to look"
                  count={msg?.reportedConversations}
                />
                <QueueItem
                  to="/security?success=false"
                  icon={KeyRound}
                  title="Failed sign-ins, last 24 h"
                  sub={acc ? `${formatNumber(acc.failingIpsLast24h)} IP addresses failing repeatedly` : "Security log"}
                  count={acc?.failedSignInsLast24h}
                  tone="bad"
                />
                <QueueItem
                  to="/orders?status=CONFIRMED"
                  icon={Truck}
                  title="Paid orders not shipped yet"
                  sub="Stores still have to ship these"
                  count={counts?.CONFIRMED}
                />
                <QueueItem
                  to="/orders?status=PENDING"
                  icon={Hourglass}
                  title="Orders awaiting payment"
                  sub="Released after 30 minutes without payment"
                  count={counts?.PENDING}
                />
                <QueueItem
                  to={`/payments${qs({ status: "FAILED", ...range })}`}
                  icon={CreditCard}
                  title="Failed payments in the period"
                  sub="Declined cards and bank transfers"
                  count={p?.failedAttempts}
                  tone="bad"
                />
                <QueueItem
                  to={acc && !acc.suspendedSellers && acc.suspendedCustomers ? "/customers?active=false" : "/stores?active=false"}
                  icon={UserX}
                  title="Suspended accounts"
                  sub={acc ? `${formatNumber(acc.suspendedSellers)} stores, ${formatNumber(acc.suspendedCustomers)} customers` : "Stores and customers"}
                  count={acc ? acc.suspendedSellers + acc.suspendedCustomers : 0}
                />
                <QueueItem
                  to="/catalogue?stockStatus=OUT_OF_STOCK"
                  icon={PackageX}
                  title="Listed products out of stock"
                  sub="Customers cannot buy these"
                  count={inv?.outOfStock}
                />
                <QueueItem
                  to="/outbox?status=FAILED"
                  icon={MailWarning}
                  title="E-mails that could not be sent"
                  sub="Order and account e-mails"
                  count={d.failedEmails.data?.totalElements}
                  tone="bad"
                />
              </div>
            </Panel>
          </div>

          <Panel
            title="Stores"
            sub={`Sales and fees ${periodName(range.from, range.to)}; stock as of now`}
            actions={
              <a className="btn btn-ghost btn-sm" href="#/stores">
                All stores
              </a>
            }
            flush
          >
            <DataTable
              rowKey="sellerId"
              rows={stores}
              onRowClick={(s) => navigate(`/stores/${s.sellerId}`)}
              empty={
                <p className="small muted" style={{ padding: 18 }}>
                  No store has products yet.
                </p>
              }
              columns={[
                { key: "store", header: "Store", render: (s) => <span className="cell-title">{s.storeName}</span> },
                { key: "sales", header: "Sales", align: "right", render: (s) => <span className="strong">{formatMoney(s.sales)}</span> },
                { key: "orders", header: "Orders", align: "right", render: (s) => formatNumber(s.orders) },
                { key: "fees", header: "Nexora fees", align: "right", render: (s) => formatMoney(s.fees) },
                { key: "listed", header: "Listed", align: "right", render: (s) => `${formatNumber(s.listed)} of ${formatNumber(s.products)}` },
                { key: "units", header: "Units on hand", align: "right", render: (s) => formatNumber(s.unitsOnHand) },
                { key: "value", header: "Stock value at cost", align: "right", render: (s) => formatMoney(s.stockValueAtCost) },
                {
                  key: "health",
                  header: "Low / out",
                  align: "right",
                  render: (s) => (
                    <span>
                      <span className={s.lowStock ? "strong" : "muted"}>{formatNumber(s.lowStock)}</span>
                      <span className="muted"> / </span>
                      <span className={s.outOfStock ? "bad strong" : "muted"}>{formatNumber(s.outOfStock)}</span>
                    </span>
                  ),
                },
              ]}
            />
          </Panel>

          <div className="grid-3">
            <Panel title="Sales by department">
              <HBarList
                items={(a?.categories || []).map((c) => ({ label: c.category, value: c.revenue, hint: formatPercent(c.sharePercent, 0) }))}
                color="var(--series-1)"
              />
            </Panel>
            <Panel title="Sales by emirate">
              <HBarList
                items={(a?.regions || []).map((r) => ({ label: r.region || "Not given", value: r.revenue, hint: `${formatNumber(r.orders)} orders` }))}
                color="var(--series-3)"
              />
            </Panel>
            <Panel title="Payment methods" sub="Successful and collected payments">
              <HBarList
                items={(p?.byMethod || []).map((m) => ({
                  label: METHOD_NAMES[m.method] || label(m.method),
                  value: m.amount,
                  hint: `${formatNumber(m.payments)} payments`,
                }))}
                color="var(--series-2)"
              />
            </Panel>
          </div>

          <div className="grid-main">
            <Panel title="New accounts" sub="Sign-ups per day, last 30 days">
              {signups.some((day) => day.customers || day.sellers) ? (
                <TrendChart
                  data={signups}
                  type="bar"
                  height={220}
                  format={formatNumber}
                  axisFormat={compact}
                  series={[
                    { key: "customers", label: "Customers" },
                    { key: "sellers", label: "Stores" },
                  ]}
                />
              ) : (
                <p className="small muted">No new customer or store signed up in the last 30 days.</p>
              )}
            </Panel>
            <Panel title="Accounts and sign-ins">
              {acc ? (
                <dl className="kv-list">
                  <div>
                    <dt>Customers</dt>
                    <dd>{formatNumber(acc.customers)}</dd>
                  </div>
                  <div>
                    <dt>Stores</dt>
                    <dd>{formatNumber(acc.sellers)}</dd>
                  </div>
                  <div>
                    <dt>Suspended</dt>
                    <dd>{formatNumber(acc.suspendedCustomers + acc.suspendedSellers)}</dd>
                  </div>
                  <div className="total">
                    <dt>Sign-ins, last 24 h</dt>
                    <dd>{formatNumber(acc.signInsLast24h)}</dd>
                  </div>
                  <div>
                    <dt>Failed sign-ins, last 24 h</dt>
                    <dd className={acc.failedSignInsLast24h ? "bad" : ""}>{formatNumber(acc.failedSignInsLast24h)}</dd>
                  </div>
                  <div>
                    <dt>Messages, last 24 h</dt>
                    <dd>{msg ? formatNumber(msg.messagesLast24Hours) : "-"}</dd>
                  </div>
                </dl>
              ) : (
                <p className="small muted">The account service did not answer.</p>
              )}
            </Panel>
          </div>
        </div>
      )}
    </>
  );
}

const GRAIN = { DAY: "Daily", WEEK: "Weekly", MONTH: "Monthly" };
