import { Boxes, PackageCheck, PackageX, Store, Tags, TriangleAlert, Wallet } from "lucide-react";
import { api, qs } from "../api.js";
import { SegmentBar } from "../components/charts.jsx";
import { ActiveFilters, DebouncedSearch, PageHead, PeriodPicker, Toolbar, storeOptions, useFilters, useStores } from "../components/common.jsx";
import { MOVEMENT_NAMES, Quantity, movementName } from "../components/tables.jsx";
import { Checkbox, DataTable, Empty, ErrorState, Loader, Pagination, Panel, Select, Stat, Tabs } from "../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, label } from "../format.js";
import { useLoader } from "../hooks.jsx";

const PHYSICAL = ["OPENING_STOCK", "RECEIVED", "SOLD", "RETURNED", "ADJUSTMENT", "COUNT_ADJUSTMENT", "TRANSFER_OUT", "TRANSFER_IN"];

function ByStore({ navigate }) {
  const summary = useLoader(() => api("/api/admin/inventory/summary"), []);
  const s = summary.data;
  if (summary.loading && !s) return <Loader text="Counting stock across stores" />;
  if (summary.error && !s) return <ErrorState message={summary.error} onRetry={summary.reload} />;
  const inStock = s.products - s.lowStock - s.outOfStock;
  return (
    <div className="cc-stack">
      <div className="stats">
        <Stat label="Stores" icon={Store} value={formatNumber(s.sellers)} />
        <Stat label="Products" icon={Tags} value={formatNumber(s.products)} hint={`${formatNumber(s.listed)} listed on the marketplace`} />
        <Stat label="Units on hand" icon={Boxes} value={formatNumber(s.unitsOnHand)} />
        <Stat label="Stock value at cost" icon={Wallet} value={formatMoney(s.stockValueAtCost)} hint={`${formatMoney(s.stockValueAtPrice)} at selling price`} />
        <Stat label="Low stock" icon={TriangleAlert} value={formatNumber(s.lowStock)} attention={s.lowStock > 0} hint="at or below the reorder point" />
        <Stat label="Out of stock" icon={PackageX} value={formatNumber(s.outOfStock)} attention={s.outOfStock > 0} />
      </div>
      <Panel title="Stock health" sub="Every product of every store">
        <SegmentBar
          segments={[
            { label: "In stock", value: inStock, color: "var(--good)" },
            { label: "Low", value: s.lowStock, color: "var(--warn)" },
            { label: "Out", value: s.outOfStock, color: "var(--bad)" },
          ]}
        />
      </Panel>
      <Panel title="By store" sub="Open a store to see its warehouses, stock levels, transfers and counts" flush>
        <DataTable
          rowKey="sellerId"
          rows={s.bySeller}
          onRowClick={(r) => navigate(`/stores/${r.sellerId}?tab=stock`)}
          columns={[
            { key: "s", header: "Store", render: (r) => <span className="cell-title">{r.storeName}</span> },
            { key: "p", header: "Products", align: "right", render: (r) => `${formatNumber(r.listed)} listed of ${formatNumber(r.products)}` },
            { key: "u", header: "Units on hand", align: "right", render: (r) => formatNumber(r.unitsOnHand) },
            { key: "c", header: "Value at cost", align: "right", render: (r) => <span className="strong">{formatMoney(r.stockValueAtCost)}</span> },
            { key: "v", header: "Value at price", align: "right", render: (r) => formatMoney(r.stockValueAtPrice) },
            { key: "l", header: "Low", align: "right", render: (r) => <span className={r.lowStock ? "strong" : "muted"}>{formatNumber(r.lowStock)}</span> },
            {
              key: "o",
              header: "Out",
              align: "right",
              render: (r) => <span className={r.outOfStock ? "bad strong" : "muted"}>{formatNumber(r.outOfStock)}</span>,
            },
          ]}
          empty={<Empty icon={PackageCheck} title="No store has stock yet" />}
        />
      </Panel>
    </div>
  );
}

const TYPES = [{ value: "", label: "Any movement" }, ...Object.entries(MOVEMENT_NAMES).map(([value, text]) => ({ value, label: text }))];

function Ledger({ f, set, clear }) {
  const stores = useStores();
  const page = Number(f.page || 0);
  const type = f.type ? [f.type] : f.reservations === "true" ? [] : PHYSICAL;
  const query = { sellerId: f.sellerId, search: f.search, from: f.from, to: f.to, type };
  const movements = useLoader(() => api(`/api/admin/inventory/movements${qs({ ...query, page, size: 30 })}`), [JSON.stringify(query), page]);
  const data = movements.data;
  return (
    <Panel flush>
      <Toolbar>
        <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="Product, SKU or reference" />
        <Select
          className="select-sm"
          value={f.sellerId || ""}
          options={storeOptions(stores.data)}
          onChange={(e) => set({ sellerId: e.target.value })}
          aria-label="Store"
        />
        <Select className="select-sm" value={f.type || ""} options={TYPES} onChange={(e) => set({ type: e.target.value })} aria-label="Movement" />
        <PeriodPicker from={f.from} to={f.to} allowEmpty onChange={(r) => set(r)} />
        {!f.type && (
          <Checkbox checked={f.reservations === "true"} onChange={(checked) => set({ reservations: checked ? "true" : "" })}>
            Include reservations
          </Checkbox>
        )}
      </Toolbar>
      <ActiveFilters
        onClear={() => clear(["view"])}
        chips={[
          { key: "q", label: `"${f.search}"`, value: f.search, onRemove: () => set({ search: "" }) },
          {
            key: "store",
            label: stores.data?.find((s) => String(s.id) === f.sellerId)?.store?.storeName || "Store",
            value: f.sellerId,
            onRemove: () => set({ sellerId: "" }),
          },
          { key: "type", label: movementName(f.type), value: f.type, onRemove: () => set({ type: "" }) },
          { key: "period", label: `${f.from} to ${f.to}`, value: f.from, onRemove: () => set({ from: "", to: "" }) },
        ]}
      />
      {movements.loading && !data ? (
        <Loader text="Reading the stock ledgers" />
      ) : movements.error && !data ? (
        <ErrorState message={movements.error} onRetry={movements.reload} />
      ) : (
        <div style={{ opacity: movements.loading ? 0.6 : 1 }}>
          <DataTable
            rows={data.content}
            empty={<Empty icon={Boxes} title="No stock movement matches" />}
            columns={[
              { key: "t", header: "When", render: (m) => <span className="nowrap">{formatDate(m.createdAt)}</span> },
              {
                key: "p",
                header: "Product",
                render: (m) => (
                  <div>
                    <div className="cell-title">{m.productName}</div>
                    <div className="cell-sub">{m.sku}</div>
                  </div>
                ),
              },
              {
                key: "k",
                header: "Movement",
                render: (m) => (
                  <div>
                    <div>{movementName(m.type)}</div>
                    <div className="cell-sub">{[m.reason && label(m.reason), m.note].filter(Boolean).join(" - ")}</div>
                  </div>
                ),
              },
              { key: "r", header: "Reference", render: (m) => (m.reference ? <span className="ref nowrap">{m.reference}</span> : "-") },
              { key: "w", header: "Warehouse", render: (m) => m.warehouseCode || "-" },
              { key: "q", header: "Units", align: "right", render: (m) => <Quantity value={m.quantity} /> },
              { key: "b", header: "Balance", align: "right", render: (m) => (m.balanceAfter == null ? "-" : formatNumber(m.balanceAfter)) },
              { key: "v", header: "Value", align: "right", render: (m) => (m.value == null ? "-" : formatMoney(m.value)) },
              { key: "by", header: "By", render: (m) => m.performedBy || "system" },
            ]}
          />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={30} onPage={(p) => set({ page: p })} />
        </div>
      )}
    </Panel>
  );
}

/** Stock across the marketplace: totals per store and every store's stock ledger in one place. */
export function InventoryPage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, { view: "stores" });
  return (
    <>
      <PageHead
        title="Inventory"
        sub="Stock across every store and warehouse. Each store manages its own stock in Seller Central; here you see all of it, read-only."
      />
      <Tabs
        tabs={[
          { id: "stores", label: "By store" },
          { id: "ledger", label: "Stock ledger" },
        ]}
        value={f.view}
        onChange={(view) => navigate(view === "ledger" ? "/inventory?view=ledger" : "/inventory", { replace: true })}
      />
      <div style={{ marginTop: 16 }}>{f.view === "ledger" ? <Ledger f={f} set={set} clear={clear} /> : <ByStore navigate={navigate} />}</div>
    </>
  );
}
