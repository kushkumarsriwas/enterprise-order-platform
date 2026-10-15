import { ExternalLink, Tags } from "lucide-react";
import { api, qs } from "../api.js";
import { ProductThumb } from "../components/brand.jsx";
import {
  ActiveFilters,
  DebouncedSearch,
  KeyValues,
  MARKETPLACE_URL,
  PageHead,
  Toolbar,
  storeOptions,
  useDepartments,
  useFilters,
  useStores,
} from "../components/common.jsx";
import { Quantity, movementName, productColumns } from "../components/tables.jsx";
import {
  DataTable,
  Drawer,
  Empty,
  ErrorState,
  ExportMenu,
  Field,
  Input,
  Loader,
  Pagination,
  Panel,
  PillToggle,
  Select,
  Stars,
  StatusBadge,
} from "../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, formatPercent, label } from "../format.js";
import { useLoader } from "../hooks.jsx";

const SORTS = [
  { value: "bestselling", label: "Best selling" },
  { value: "name", label: "Name (A to Z)" },
  { value: "newest", label: "Newest first" },
  { value: "updated", label: "Recently updated" },
  { value: "price", label: "Price: low to high" },
  { value: "priceDesc", label: "Price: high to low" },
  { value: "available", label: "Least stock first" },
  { value: "value", label: "Highest stock value" },
  { value: "margin", label: "Highest margin" },
];

const LISTED = [
  { value: "", label: "All" },
  { value: "true", label: "Listed" },
  { value: "false", label: "Inventory only" },
];

const STOCK = [
  { value: "", label: "Any stock level" },
  { value: "IN_STOCK", label: "In stock" },
  { value: "LOW_STOCK", label: "Low stock" },
  { value: "OUT_OF_STOCK", label: "Out of stock" },
];

/** One product as the store keeps it: prices, stock per warehouse, demand and the latest ledger rows. */
function ProductDrawer({ id, onClose }) {
  const detail = useLoader(() => api(`/api/admin/catalog/products/${id}`), [id]);
  const d = detail.data;
  const p = d?.product;
  return (
    <Drawer open wide title={p ? p.name : "Product"} sub={p ? `${p.sku} from ${p.sellerName}` : null} onClose={onClose}>
      {detail.loading && !d ? (
        <Loader />
      ) : detail.error && !d ? (
        <ErrorState message={detail.error} onRetry={detail.reload} />
      ) : (
        <div className="stack">
          <div className="row" style={{ gap: 14, alignItems: "flex-start" }}>
            <ProductThumb product={p} large />
            <div className="stack-sm" style={{ gap: 6 }}>
              <div className="row row-wrap" style={{ gap: 8 }}>
                <StatusBadge status={p.stockStatus} />
                <StatusBadge status={p.storeSuspended ? "SUSPENDED" : p.listed ? "ACTIVE" : "DRAFT"}>
                  {p.storeSuspended ? "Store suspended" : p.listed ? "Listed" : "Inventory only"}
                </StatusBadge>
              </div>
              {p.ratingCount > 0 ? <Stars value={Number(p.ratingAverage)} count={p.ratingCount} /> : <span className="small muted">No reviews yet</span>}
              <div className="row row-wrap" style={{ gap: 12 }}>
                <a className="small" href={`#/stores/${p.sellerId}`}>
                  {p.sellerName}
                </a>
                {p.visibleToCustomers && (
                  <a className="small" href={`${MARKETPLACE_URL}/#/product/${p.id}`} target="_blank" rel="noreferrer">
                    <ExternalLink size={13} style={{ verticalAlign: "-2px" }} /> On the marketplace
                  </a>
                )}
              </div>
            </div>
          </div>
          <div className="grid-2">
            <Panel title="Price">
              <KeyValues
                items={[
                  { label: "Selling price", value: formatMoney(p.price) },
                  p.compareAtPrice && { label: "Was", value: formatMoney(p.compareAtPrice) },
                  { label: "Cost", value: formatMoney(p.costPrice) },
                  { label: "Margin", value: formatPercent(p.marginPercent), total: true },
                ]}
              />
            </Panel>
            <Panel title="Demand, last 30 days">
              <KeyValues
                items={[
                  { label: "Units sold", value: formatNumber(d.unitsSoldLast30Days) },
                  { label: "Sales", value: formatMoney(d.revenueLast30Days) },
                  { label: "Per day", value: Number(d.averageDailySales || 0).toFixed(1) },
                  { label: "Days of stock left", value: d.daysOfCover == null ? "-" : formatNumber(d.daysOfCover), total: true },
                ]}
              />
            </Panel>
          </div>
          <Panel
            title="Stock by warehouse"
            sub={`${formatNumber(p.availableQuantity)} available, ${formatNumber(p.reservedQuantity)} reserved, ${formatNumber(p.incomingQuantity)} on the way`}
            flush
          >
            <DataTable
              rowKey="warehouseId"
              rows={d.warehouses}
              columns={[
                { key: "w", header: "Warehouse", render: (w) => `${w.code} - ${w.name}` },
                { key: "b", header: "Bin", render: (w) => w.binLocation || "-" },
                { key: "o", header: "On hand", align: "right", render: (w) => <span className="strong">{formatNumber(w.onHand)}</span> },
              ]}
            />
          </Panel>
          {d.openPurchaseOrders?.length > 0 && (
            <Panel title="Ordered from suppliers" flush>
              <DataTable
                rowKey="purchaseOrderId"
                rows={d.openPurchaseOrders}
                columns={[
                  { key: "n", header: "Purchase order", render: (o) => <span className="cell-ref">{o.number}</span> },
                  { key: "s", header: "Supplier", render: (o) => o.supplierName },
                  { key: "e", header: "Expected", render: (o) => o.expectedDate || "-" },
                  { key: "q", header: "Units to come", align: "right", render: (o) => formatNumber(o.quantityOutstanding) },
                ]}
              />
            </Panel>
          )}
          <Panel title="Latest stock movements" flush>
            <DataTable
              rows={d.recentMovements}
              empty={<Empty title="No movements" />}
              columns={[
                { key: "t", header: "When", render: (m) => <span className="nowrap">{formatDate(m.createdAt)}</span> },
                {
                  key: "k",
                  header: "Movement",
                  render: (m) => (
                    <div>
                      <div>{movementName(m.type)}</div>
                      <div className="cell-sub">{[m.reference, m.reason && label(m.reason)].filter(Boolean).join(" - ")}</div>
                    </div>
                  ),
                },
                { key: "w", header: "Warehouse", render: (m) => m.warehouseCode || "-" },
                { key: "q", header: "Units", align: "right", render: (m) => <Quantity value={m.quantity} /> },
                { key: "b", header: "Balance", align: "right", render: (m) => (m.balanceAfter == null ? "-" : formatNumber(m.balanceAfter)) },
              ]}
            />
          </Panel>
          {p.description && (
            <Panel title="Description">
              <p className="small">{p.description}</p>
            </Panel>
          )}
        </div>
      )}
    </Drawer>
  );
}

/** Every product of every store, including stock that is not listed on the marketplace. */
export function CataloguePage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, { sort: "bestselling" });
  const stores = useStores();
  const departments = useDepartments();
  const page = Number(f.page || 0);
  const query = {
    search: f.search,
    sellerId: f.sellerId,
    categoryId: f.categoryId,
    listed: f.listed,
    stockStatus: f.stockStatus,
    minPrice: f.minPrice,
    maxPrice: f.maxPrice,
    sort: f.sort,
  };
  const products = useLoader(() => api(`/api/admin/catalog/products${qs({ ...query, page, size: 25 })}`), [JSON.stringify(query), page]);
  const data = products.data;
  const department = departments.data?.find((c) => String(c.id) === f.categoryId);

  return (
    <>
      <PageHead
        title="Catalogue"
        sub="Every product of every store, including stock a store keeps without listing it. Products of a suspended store stay here but are hidden from customers."
        actions={<ExportMenu path="/api/admin/catalog/products/export/{format}" query={qs(query)} />}
      />
      <Panel flush>
        <Toolbar
          moreCount={[f.minPrice, f.maxPrice, f.categoryId].filter(Boolean).length}
          more={
            <>
              <Field label="Department">
                <Select
                  value={f.categoryId || ""}
                  options={[{ value: "", label: "Every department" }, ...(departments.data || []).map((c) => ({ value: String(c.id), label: c.name }))]}
                  onChange={(e) => set({ categoryId: e.target.value })}
                />
              </Field>
              <Field label="Price (AED)">
                <div className="range-pair">
                  <Input type="number" min="0" placeholder="Min" value={f.minPrice || ""} onChange={(e) => set({ minPrice: e.target.value })} />
                  <Input type="number" min="0" placeholder="Max" value={f.maxPrice || ""} onChange={(e) => set({ maxPrice: e.target.value })} />
                </div>
              </Field>
            </>
          }
          right={<Select className="select-sm" value={f.sort} options={SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />}
        >
          <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="Name, SKU, barcode or brand" />
          <Select
            className="select-sm"
            value={f.sellerId || ""}
            options={storeOptions(stores.data)}
            onChange={(e) => set({ sellerId: e.target.value })}
            aria-label="Store"
          />
          <Select className="select-sm" value={f.stockStatus || ""} options={STOCK} onChange={(e) => set({ stockStatus: e.target.value })} aria-label="Stock" />
          <PillToggle label="Listing" options={LISTED} value={f.listed || ""} onChange={(listed) => set({ listed })} />
        </Toolbar>
        <ActiveFilters
          onClear={() => clear()}
          chips={[
            { key: "q", label: `"${f.search}"`, value: f.search, onRemove: () => set({ search: "" }) },
            {
              key: "store",
              label: stores.data?.find((s) => String(s.id) === f.sellerId)?.store?.storeName || "Store",
              value: f.sellerId,
              onRemove: () => set({ sellerId: "" }),
            },
            { key: "dep", label: department?.name || "Department", value: f.categoryId, onRemove: () => set({ categoryId: "" }) },
            { key: "stock", label: STOCK.find((s) => s.value === f.stockStatus)?.label, value: f.stockStatus, onRemove: () => set({ stockStatus: "" }) },
            { key: "min", label: `From ${formatMoney(f.minPrice)}`, value: f.minPrice, onRemove: () => set({ minPrice: "" }) },
            { key: "max", label: `Up to ${formatMoney(f.maxPrice)}`, value: f.maxPrice, onRemove: () => set({ maxPrice: "" }) },
          ]}
        />
        {products.loading && !data ? (
          <Loader text="Loading the catalogue" />
        ) : products.error && !data ? (
          <ErrorState message={products.error} onRetry={products.reload} />
        ) : (
          <div style={{ opacity: products.loading ? 0.6 : 1 }}>
            <DataTable
              rows={data.content}
              columns={productColumns()}
              onRowClick={(p) => set({ product: String(p.id), page: f.page })}
              empty={<Empty icon={Tags} title="No product matches these filters" />}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={25} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
      {f.product && <ProductDrawer id={f.product} onClose={() => set({ product: "", page: f.page })} />}
    </>
  );
}
