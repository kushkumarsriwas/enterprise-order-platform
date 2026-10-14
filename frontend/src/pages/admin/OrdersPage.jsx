import { useState } from "react";
import { ClipboardList } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDate, formatMoney } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { Alert, Badge, Empty, PageHeader, Pagination, Spinner, Tabs } from "../../components/ui.jsx";

export default function AdminOrders() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const orders = useLoader(() => api(`/api/orders${qs({ status, page, size: 20 })}`), [status, page]);
  return (
    <div className="page">
      <PageHeader title="All orders" subtitle="Every order on the marketplace, across all stores." />
      <Tabs
        value={status}
        onChange={(s) => { setStatus(s); setPage(0); }}
        tabs={[
          { id: "", label: "All" },
          { id: "PENDING", label: "Awaiting payment" },
          { id: "CONFIRMED", label: "Paid, to ship" },
          { id: "SHIPPED", label: "Shipped" },
          { id: "DELIVERED", label: "Delivered" },
          { id: "CANCELLED", label: "Cancelled" },
        ]}
      />
      <Alert>{orders.error}</Alert>
      {orders.loading && !orders.data ? (
        <Spinner />
      ) : orders.data?.content.length === 0 ? (
        <Empty icon={ClipboardList} title="No orders" />
      ) : (
        <div className={`card flush ${orders.loading ? "refetching" : ""}`}>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Store</th>
                  <th>Customer</th>
                  <th>Status</th>
                  <th>Payment</th>
                  <th className="num">Total</th>
                </tr>
              </thead>
              <tbody>
                {orders.data?.content.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <strong>{o.orderNumber}</strong>
                      <span className="sub">{formatDate(o.createdAt)}</span>
                    </td>
                    <td>{o.merchantName}</td>
                    <td>{o.customerUsername}</td>
                    <td>
                      <Badge value={o.status} friendly />
                    </td>
                    <td>
                      <Badge value={o.paymentState} friendly />
                    </td>
                    <td className="num">{formatMoney(o.totalAmount, o.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <Pagination page={orders.data} onChange={setPage} />
    </div>
  );
}
