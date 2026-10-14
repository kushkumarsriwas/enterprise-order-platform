import { AlertTriangle, PackageCheck, ShoppingBag, TrendingUp } from "lucide-react";
import { api } from "../../api.js";
import { formatMoney } from "../../format.js";
import { Alert, Badge, Spinner } from "../../components/ui.jsx";
import { useLoader } from "../../hooks.jsx";

export default function DashboardPage({ navigate }) {
  const stats = useLoader(() => api("/api/orders/stats"), []);
  const lowStock = useLoader(() => api("/api/inventory/low-stock"), []);

  if (stats.loading && !stats.data) return <div className="page"><Spinner label="Loading dashboard..." /></div>;
  const s = stats.data;
  const maxRevenue = Math.max(1, ...(s?.last7Days || []).map((d) => Number(d.revenue)));

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Operations dashboard</h1>
          <p className="muted">Figures are computed with SQL aggregates in the order service.</p>
        </div>
      </div>
      <Alert>{stats.error}</Alert>
      {s && (
        <>
          <div className="kpis">
            <div className="kpi"><TrendingUp size={18} /><span>Revenue</span><strong>{formatMoney(s.revenue, s.currency)}</strong></div>
            <div className="kpi"><ShoppingBag size={18} /><span>Orders</span><strong>{s.totalOrders}</strong></div>
            <div className="kpi"><PackageCheck size={18} /><span>Awaiting shipment</span><strong>{s.ordersByStatus.CONFIRMED}</strong></div>
            <div className="kpi"><AlertTriangle size={18} /><span>Low-stock products</span><strong>{lowStock.data?.length ?? "-"}</strong></div>
          </div>
          <div className="two-col even">
            <div className="card">
              <h3>Orders by status</h3>
              <div className="status-bars">
                {Object.entries(s.ordersByStatus).map(([status, count]) => (
                  <div key={status} className="status-bar">
                    <Badge value={status} />
                    <div className="bar"><span style={{ width: `${s.totalOrders ? (count / s.totalOrders) * 100 : 0}%` }} /></div>
                    <strong>{count}</strong>
                  </div>
                ))}
              </div>
            </div>
            <div className="card">
              <h3>Revenue, last 7 days</h3>
              {s.last7Days.length === 0 ? (
                <p className="muted">No sales in the last 7 days.</p>
              ) : (
                <div className="day-chart">
                  {s.last7Days.map((d) => (
                    <div key={d.date} className="day" title={`${d.orders} orders`}>
                      <div className="day-bar" style={{ height: `${(Number(d.revenue) / maxRevenue) * 100}%` }} />
                      <span className="small">{d.date.slice(5)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div className="two-col even">
            <div className="card">
              <h3>Best sellers</h3>
              <table className="table">
                <thead><tr><th>Product</th><th>Units</th><th className="right">Revenue</th></tr></thead>
                <tbody>
                  {s.topProducts.map((p) => (
                    <tr key={p.productId}><td>{p.productName}</td><td>{p.unitsSold}</td><td className="right">{formatMoney(p.revenue)}</td></tr>
                  ))}
                  {s.topProducts.length === 0 && <tr><td colSpan={3} className="muted">No sales yet</td></tr>}
                </tbody>
              </table>
            </div>
            <div className="card">
              <h3>Low stock</h3>
              <table className="table clickable">
                <thead><tr><th>Product</th><th>Available</th><th>Reorder at</th></tr></thead>
                <tbody>
                  {(lowStock.data || []).map((i) => (
                    <tr key={i.productId} onClick={() => navigate("/admin/products")}>
                      <td>{i.productName}<div className="muted small">{i.sku}</div></td>
                      <td><Badge value={String(i.availableQuantity)} tone={i.availableQuantity === 0 ? "red" : "amber"} /></td>
                      <td>{i.reorderLevel}</td>
                    </tr>
                  ))}
                  {lowStock.data?.length === 0 && <tr><td colSpan={3} className="muted">All products are well stocked</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
