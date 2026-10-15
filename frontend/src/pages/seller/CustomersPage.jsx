import { Crown, Repeat, TriangleAlert, Users } from "lucide-react";
import { api, qs } from "../../api.js";
import { DataTable, Empty, ErrorState, ExportMenu, Loader, Pagination, Panel, PillToggle, Select, Stat, StatusBadge } from "../../components/ui.jsx";
import { formatDay, formatMoney, formatNumber, formatPercent } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { DebouncedSearch, PageHead, Toolbar, useFilters } from "./common.jsx";

const SEGMENTS = [
  { value: "", label: "Everyone" },
  { value: "NEW", label: "New" },
  { value: "REPEAT", label: "Repeat" },
  { value: "VIP", label: "VIP" },
  { value: "AT_RISK", label: "At risk" },
];

/** Who buys from the store: spend, frequency, recency and a simple segment (new, repeat, VIP, at risk). */
export function SellerCustomersPage({ route, navigate }) {
  const [f, set] = useFilters(route, navigate, { sort: "spent" });
  const page = Number(f.page || 0);
  const query = { q: f.q, segment: f.segment, sort: f.sort };
  const customers = useLoader(() => api(`/api/seller/customers${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const data = customers.data;
  const repeatShare = data && data.customers ? (data.repeatCustomers / data.customers) * 100 : 0;

  return (
    <>
      <PageHead
        title="Customers"
        sub="Everyone who ordered from your store. VIP: 5 or more orders or over AED 5,000 spent. At risk: no order for 90 days."
        actions={<ExportMenu path="/api/seller/customers/export/{format}" query={qs(query)} />}
      />
      {data && (
        <div className="stats" style={{ marginBottom: 16 }}>
          <Stat label="Customers" icon={Users} value={formatNumber(data.customers)} />
          <Stat label="Came back" icon={Repeat} value={formatNumber(data.repeatCustomers)} hint={`${formatPercent(repeatShare, 0)} ordered more than once`} />
          <Stat label="VIP" icon={Crown} value={formatNumber(data.vipCustomers)} />
          <Stat
            label="At risk"
            icon={TriangleAlert}
            value={formatNumber(data.atRiskCustomers)}
            hint="No order in 90 days"
            attention={data.atRiskCustomers > 0}
          />
        </div>
      )}
      <Panel flush>
        <Toolbar>
          <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Customer name or email" />
          <PillToggle label="Segment" options={SEGMENTS} value={f.segment || ""} onChange={(segment) => set({ segment })} />
          <Select
            className="select-sm"
            value={f.sort}
            options={[
              { value: "spent", label: "Most spent" },
              { value: "orders", label: "Most orders" },
              { value: "recent", label: "Ordered most recently" },
              { value: "name", label: "Name" },
            ]}
            onChange={(e) => set({ sort: e.target.value })}
            aria-label="Sort"
          />
        </Toolbar>
        {customers.loading && !data ? (
          <Loader text="Loading customers" />
        ) : customers.error && !data ? (
          <ErrorState message={customers.error} onRetry={customers.reload} />
        ) : (
          <div style={{ opacity: customers.loading ? 0.6 : 1 }}>
            <DataTable
              rowKey="customerId"
              rows={data.content}
              onRowClick={(c) => navigate(`/seller/orders${qs({ q: c.username })}`)}
              empty={<Empty icon={Users} title={f.q || f.segment ? "No customer matches" : "No customers yet"} />}
              columns={[
                {
                  key: "name",
                  header: "Customer",
                  render: (c) => (
                    <div>
                      <div className="cell-title">{c.username}</div>
                      <div className="cell-sub">{c.email}</div>
                    </div>
                  ),
                },
                { key: "segment", header: "Segment", render: (c) => <StatusBadge status={c.segment} /> },
                { key: "orders", header: "Orders", align: "right", render: (c) => formatNumber(c.orders) },
                { key: "spent", header: "Spent", align: "right", render: (c) => <span className="strong">{formatMoney(c.spent)}</span> },
                { key: "aov", header: "Average order", align: "right", render: (c) => formatMoney(c.averageOrderValue) },
                { key: "first", header: "First order", render: (c) => formatDay(c.firstOrderAt) },
                {
                  key: "last",
                  header: "Last order",
                  render: (c) => (
                    <div>
                      <div>{formatDay(c.lastOrderAt)}</div>
                      <div className="cell-sub">
                        {c.daysSinceLastOrder === 0 ? "today" : c.daysSinceLastOrder === 1 ? "yesterday" : `${formatNumber(c.daysSinceLastOrder)} days ago`}
                      </div>
                    </div>
                  ),
                },
              ]}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
    </>
  );
}
