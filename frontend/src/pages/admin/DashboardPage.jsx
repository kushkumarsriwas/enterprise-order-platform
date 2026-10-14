import { Banknote, Percent, ShoppingBag, Store, Undo2, Users } from "lucide-react";
import { api } from "../../api.js";
import { compactMoney, eachDay, formatMoney, formatNumber, label, lastDays, shortDay } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { BarList, ChartCard, ColumnsChart } from "../../components/charts.jsx";
import { Alert, PageHeader, StatTile } from "../../components/ui.jsx";

const METHOD_NAMES = { CARD: "Card", UPI: "UPI", COD: "Cash on delivery" };

/** Marketplace health: sales volume (GMV), the platform's commission revenue, stores, customers and order flow. */
export default function AdminDashboard() {
  const stats = useLoader(() => api("/api/orders/stats"), []);
  const payments = useLoader(() => api("/api/payments/platform-summary"), []);
  const merchants = useLoader(() => api("/api/users?role=MERCHANT&size=1"), []);
  const customers = useLoader(() => api("/api/users?role=CUSTOMER&size=1"), []);
  const s = stats.data;
  const p = payments.data;
  const range = lastDays(14);
  const byDay = new Map((s?.last14Days || []).map((d) => [d.date, d]));
  const days = eachDay(range.from, range.to).map((date) => ({ date, sales: Number(byDay.get(date)?.sales || 0), orders: byDay.get(date)?.orders || 0 }));
  const statusItems = Object.entries(s?.ordersByStatus || {}).map(([status, count]) => ({ key: status, label: label(status === "CONFIRMED" ? "Paid, to ship" : status === "PENDING" ? "Awaiting payment" : status), value: count }));

  return (
    <div className="page">
      <PageHeader title="Platform overview" subtitle="The whole marketplace: sales volume, commission revenue, stores and customers." />
      <Alert>{stats.error || payments.error}</Alert>
      <div className="stats">
        <StatTile hero icon={ShoppingBag} label="Gross merchandise value" value={s ? formatMoney(s.grossMerchandiseValue) : "-"} hint="Value of paid and cash-on-delivery orders" />
        <StatTile hero icon={Percent} label="Platform commission" value={p ? formatMoney(p.platformRevenue) : "-"} hint={p ? `${p.feeRatePercent}% of each sale` : null} />
        <StatTile hero icon={Banknote} label="Payments processed" value={p ? formatMoney(p.totalVolume) : "-"} hint={p ? `${formatNumber(p.payments)} payments, ${p.failedAttempts} declined` : null} />
      </div>
      <div className="stats">
        <StatTile icon={Store} label="Stores" value={merchants.data ? formatNumber(merchants.data.totalElements) : "-"} />
        <StatTile icon={Users} label="Customers" value={customers.data ? formatNumber(customers.data.totalElements) : "-"} hint={s ? `${formatNumber(s.totalOrders)} orders placed` : null} />
        <StatTile icon={Undo2} label="Refunded" value={p ? formatMoney(p.refunded) : "-"} />
      </div>
      <ChartCard
        title="Marketplace sales, last 14 days"
        subtitle="AED, paid and cash-on-delivery orders"
        empty={s && days.every((d) => d.sales === 0) ? "No sales in the last 14 days." : null}
        table={{
          columns: [
            { key: "date", label: "Date", format: shortDay },
            { key: "orders", label: "Orders", numeric: true },
            { key: "sales", label: "Sales", numeric: true, format: (v) => formatMoney(v) },
          ],
          rows: days,
        }}
      >
        <ColumnsChart data={days} xKey="date" labelFormat={shortDay} valueFormat={(v) => formatMoney(v)} series={[{ key: "sales", name: "Sales" }]} />
      </ChartCard>
      <div className="three-col">
        <ChartCard
          title="Top stores"
          subtitle="By sales, all time"
          empty={s && s.topMerchants.length === 0 ? "No sales yet." : null}
          table={{
            columns: [
              { key: "merchantName", label: "Store" },
              { key: "orders", label: "Orders", numeric: true },
              { key: "sales", label: "Sales", numeric: true, format: (v) => formatMoney(v) },
            ],
            rows: s?.topMerchants || [],
          }}
        >
          <BarList items={(s?.topMerchants || []).slice(0, 8).map((m) => ({ key: m.merchantId, label: m.merchantName, value: Number(m.sales), note: `${m.orders} orders` }))} valueFormat={compactMoney} />
        </ChartCard>
        <ChartCard
          title="Orders by status"
          subtitle="All time"
          table={{ columns: [{ key: "label", label: "Status" }, { key: "value", label: "Orders", numeric: true }], rows: statusItems }}
        >
          <BarList items={statusItems} valueFormat={formatNumber} />
        </ChartCard>
        <ChartCard
          title="Payment methods"
          subtitle="Successful and cash-on-delivery payments"
          empty={p && p.byMethod.length === 0 ? "No payments yet." : null}
          table={{
            columns: [
              { key: "method", label: "Method", format: (v) => METHOD_NAMES[v] || v },
              { key: "payments", label: "Payments", numeric: true },
              { key: "amount", label: "Amount", numeric: true, format: (v) => formatMoney(v) },
            ],
            rows: p?.byMethod || [],
          }}
        >
          <BarList items={(p?.byMethod || []).map((m) => ({ key: m.method, label: METHOD_NAMES[m.method] || m.method, value: Number(m.amount), note: `${m.payments} payments` }))} valueFormat={compactMoney} />
        </ChartCard>
      </div>
    </div>
  );
}
