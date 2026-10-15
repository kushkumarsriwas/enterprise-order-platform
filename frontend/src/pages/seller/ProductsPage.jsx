import { useState } from "react";
import { Archive, CircleAlert, CircleCheck, Download, EyeOff, FileUp, MoreHorizontal, PackagePlus, Pencil, Store, Upload } from "lucide-react";
import { api, download, qs, upload } from "../../api.js";
import { ProductImage } from "../../components/brand.jsx";
import {
  Badge,
  Button,
  Confirm,
  DataTable,
  Empty,
  ErrorState,
  ExportMenu,
  Field,
  IconButton,
  Input,
  Loader,
  Menu,
  Modal,
  Notice,
  Pagination,
  Panel,
  Select,
  StatusBadge,
  Switch,
} from "../../components/ui.jsx";
import { formatMoney, formatNumber, formatPercent, plural } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import {
  ActiveFilters,
  DebouncedSearch,
  PageHead,
  Toolbar,
  invalidate,
  useBrands,
  useCategories,
  useFilters,
  useSuppliers,
  useWarehouses,
  warehouseOptions,
} from "./common.jsx";

export const PRODUCT_SORTS = [
  { value: "name", label: "Name (A to Z)" },
  { value: "sku", label: "SKU" },
  { value: "newest", label: "Newest first" },
  { value: "updated", label: "Recently updated" },
  { value: "price", label: "Price: low to high" },
  { value: "priceDesc", label: "Price: high to low" },
  { value: "available", label: "Least stock first" },
  { value: "availableDesc", label: "Most stock first" },
  { value: "value", label: "Highest stock value" },
  { value: "bestselling", label: "Best selling" },
  { value: "margin", label: "Highest margin" },
];
export const STOCK_STATUSES = [
  { value: "IN_STOCK", label: "In stock" },
  { value: "LOW_STOCK", label: "Low stock" },
  { value: "OUT_OF_STOCK", label: "Out of stock" },
];
const PAGE_SIZE = 25;

/** The filter keys shared by the product list, stock levels and their exports. */
export const productQuery = (f) => ({
  search: f.search,
  categoryId: f.categoryId,
  listed: f.listed,
  stockStatus: f.stockStatus,
  supplierId: f.supplierId,
  brand: f.brand,
  warehouseId: f.warehouseId,
  minPrice: f.minPrice,
  maxPrice: f.maxPrice,
  sort: f.sort,
});

/** Filter row used by both the product list and stock levels. */
export function ProductFilters({ f, set, clear, right }) {
  const categories = useCategories();
  const suppliers = useSuppliers();
  const warehouses = useWarehouses();
  const brands = useBrands();
  const moreCount = [f.supplierId, f.brand, f.warehouseId, f.minPrice || f.maxPrice].filter(Boolean).length;
  const name = (list, id, key = "name") => list.find((x) => String(x.id) === String(id))?.[key];
  return (
    <>
      <Toolbar
        right={right}
        moreCount={moreCount}
        more={
          <>
            <Field label="Supplier">
              <Select
                value={f.supplierId || ""}
                placeholder="Any supplier"
                options={suppliers.map((s) => ({ value: String(s.id), label: s.name }))}
                onChange={(e) => set({ supplierId: e.target.value })}
              />
            </Field>
            <Field label="Brand">
              <Select
                value={f.brand || ""}
                placeholder="Any brand"
                options={brands.map((b) => ({ value: b, label: b }))}
                onChange={(e) => set({ brand: e.target.value })}
              />
            </Field>
            <Field label="Warehouse">
              <Select
                value={f.warehouseId || ""}
                placeholder="Any warehouse"
                options={warehouseOptions(warehouses, { activeOnly: false })}
                onChange={(e) => set({ warehouseId: e.target.value })}
              />
            </Field>
            <Field label="Selling price (AED)">
              <div className="range-pair">
                <Input
                  key={`min-${f.minPrice || ""}`}
                  type="number"
                  min="0"
                  placeholder="Min"
                  defaultValue={f.minPrice || ""}
                  onBlur={(e) => e.target.value !== (f.minPrice || "") && set({ minPrice: e.target.value })}
                  aria-label="Minimum price"
                />
                <Input
                  key={`max-${f.maxPrice || ""}`}
                  type="number"
                  min="0"
                  placeholder="Max"
                  defaultValue={f.maxPrice || ""}
                  onBlur={(e) => e.target.value !== (f.maxPrice || "") && set({ maxPrice: e.target.value })}
                  aria-label="Maximum price"
                />
              </div>
            </Field>
          </>
        }
      >
        <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="Name, SKU, barcode or brand" />
        <Select
          className="select-sm"
          value={f.categoryId || ""}
          placeholder="All departments"
          options={categories.map((c) => ({ value: String(c.id), label: c.name }))}
          onChange={(e) => set({ categoryId: e.target.value })}
          aria-label="Department"
        />
        <Select
          className="select-sm"
          value={f.listed || ""}
          placeholder="Listed and unlisted"
          options={[
            { value: "true", label: "Listed for sale" },
            { value: "false", label: "Inventory only" },
          ]}
          onChange={(e) => set({ listed: e.target.value })}
          aria-label="Listing"
        />
        <Select
          className="select-sm"
          value={f.stockStatus || ""}
          placeholder="Any stock level"
          options={STOCK_STATUSES}
          onChange={(e) => set({ stockStatus: e.target.value })}
          aria-label="Stock level"
        />
        <Select className="select-sm" value={f.sort} options={PRODUCT_SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />
      </Toolbar>
      <ActiveFilters
        onClear={() => clear(["sort"])}
        chips={[
          { key: "search", label: `"${f.search}"`, value: f.search, onRemove: () => set({ search: "" }) },
          { key: "cat", label: name(categories, f.categoryId) || "Department", value: f.categoryId, onRemove: () => set({ categoryId: "" }) },
          { key: "listed", label: f.listed === "true" ? "Listed for sale" : "Inventory only", value: f.listed, onRemove: () => set({ listed: "" }) },
          { key: "stock", label: STOCK_STATUSES.find((s) => s.value === f.stockStatus)?.label, value: f.stockStatus, onRemove: () => set({ stockStatus: "" }) },
          { key: "supplier", label: name(suppliers, f.supplierId) || "Supplier", value: f.supplierId, onRemove: () => set({ supplierId: "" }) },
          { key: "brand", label: f.brand, value: f.brand, onRemove: () => set({ brand: "" }) },
          { key: "wh", label: name(warehouses, f.warehouseId, "code") || "Warehouse", value: f.warehouseId, onRemove: () => set({ warehouseId: "" }) },
          {
            key: "price",
            label: `AED ${f.minPrice || 0} to ${f.maxPrice || "any"}`,
            value: f.minPrice || f.maxPrice,
            onRemove: () => set({ minPrice: "", maxPrice: "" }),
          },
        ]}
      />
    </>
  );
}

function BulkBar({ selected, categories, onDone, onClear }) {
  const toast = useToast();
  const [busy, setBusy] = useState(null);
  const [categoryId, setCategoryId] = useState("");
  const [percent, setPercent] = useState("");
  const [reorderPoint, setReorderPoint] = useState("");
  const [confirmArchive, setConfirmArchive] = useState(false);
  const run = async (action, extra = {}) => {
    setBusy(action);
    try {
      const result = await api("/api/seller/products/bulk", { method: "POST", body: { productIds: selected, action, ...extra } });
      toast(
        `${plural(result.updated, "product")} updated${result.skipped.length ? `; skipped: ${result.skipped.join(", ")}` : ""}`,
        result.skipped.length ? "info" : "good",
      );
      onDone();
    } catch (error) {
      toast(error.message, "bad");
    } finally {
      setBusy(null);
      setConfirmArchive(false);
    }
  };
  return (
    <div className="bulk-bar" role="region" aria-label="Bulk actions">
      <strong>{plural(selected.length, "product")} selected</strong>
      <Button size="sm" loading={busy === "LIST"} onClick={() => run("LIST")}>
        List for sale
      </Button>
      <Button size="sm" loading={busy === "UNLIST"} onClick={() => run("UNLIST")}>
        Keep as inventory only
      </Button>
      <span className="row" style={{ gap: 4 }}>
        <Select
          className="select-sm"
          value={categoryId}
          placeholder="Move to department"
          options={categories.map((c) => ({ value: String(c.id), label: c.name }))}
          onChange={(e) => setCategoryId(e.target.value)}
          aria-label="Department"
        />
        <Button size="sm" disabled={!categoryId} loading={busy === "SET_CATEGORY"} onClick={() => run("SET_CATEGORY", { categoryId: Number(categoryId) })}>
          Apply
        </Button>
      </span>
      <span className="row" style={{ gap: 4 }}>
        <Input
          className="input-sm"
          style={{ width: 120 }}
          type="number"
          min="-90"
          max="500"
          placeholder="Price %"
          value={percent}
          onChange={(e) => setPercent(e.target.value)}
          aria-label="Change prices by percent"
        />
        <Button
          size="sm"
          disabled={!percent || Number(percent) === 0}
          loading={busy === "ADJUST_PRICE_PERCENT"}
          onClick={() => run("ADJUST_PRICE_PERCENT", { percent: Number(percent) })}
        >
          Change prices
        </Button>
      </span>
      <span className="row" style={{ gap: 4 }}>
        <Input
          className="input-sm"
          style={{ width: 130 }}
          type="number"
          min="0"
          placeholder="Reorder point"
          value={reorderPoint}
          onChange={(e) => setReorderPoint(e.target.value)}
          aria-label="Reorder point"
        />
        <Button
          size="sm"
          disabled={reorderPoint === ""}
          loading={busy === "SET_REORDER_POINT"}
          onClick={() => run("SET_REORDER_POINT", { reorderPoint: Number(reorderPoint) })}
        >
          Set
        </Button>
      </span>
      <Button size="sm" variant="ghost" icon={Archive} onClick={() => setConfirmArchive(true)}>
        Archive
      </Button>
      <Button size="sm" variant="ghost" onClick={onClear}>
        Clear selection
      </Button>
      <Confirm
        open={confirmArchive}
        title={`Archive ${plural(selected.length, "product")}?`}
        confirmLabel="Archive"
        danger
        busy={busy === "ARCHIVE"}
        onConfirm={() => run("ARCHIVE")}
        onClose={() => setConfirmArchive(false)}
      >
        Archived products leave your catalogue and the marketplace. Their order history and stock ledger are kept.
      </Confirm>
    </div>
  );
}

function ImportDialog({ open, onClose, onDone }) {
  const toast = useToast();
  const [file, setFile] = useState(null);
  const [check, setCheck] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const send = async (dryRun) => {
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const result = await upload(`/api/seller/products/import${qs({ dryRun })}`, form);
      if (dryRun) setCheck(result);
      else {
        toast(`Imported: ${result.created} new, ${result.updated} updated`);
        onDone();
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const close = () => {
    setFile(null);
    setCheck(null);
    setError(null);
    onClose();
  };
  return (
    <Modal
      open={open}
      onClose={close}
      title="Import products from Excel"
      sub="Add many products at once or update existing ones by SKU. Every row is checked first; nothing is saved unless the whole file is valid."
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            Cancel
          </Button>
          {check && check.errors.length === 0 ? (
            <Button variant="primary" icon={Upload} loading={busy} onClick={() => send(false)}>
              Import {plural(check.rows, "row")}
            </Button>
          ) : (
            <Button variant="primary" disabled={!file} loading={busy} onClick={() => send(true)}>
              Check the file
            </Button>
          )}
        </>
      }
    >
      <div className="stack">
        <div>
          <Button size="sm" icon={Download} onClick={() => download("/api/seller/products/import/template").catch((e) => toast(e.message, "bad"))}>
            Download the template
          </Button>
        </div>
        <Field label="Excel file (.xlsx)">
          <input
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(event) => {
              setFile(event.target.files?.[0] || null);
              setCheck(null);
            }}
          />
        </Field>
        {check && (
          <Notice tone={check.errors.length ? "bad" : "good"} icon={check.errors.length ? CircleAlert : CircleCheck}>
            {check.errors.length === 0 ? (
              `${plural(check.rows, "row")} checked: ${check.created} new products, ${check.updated} updates. Ready to import.`
            ) : (
              <>
                <strong>{plural(check.errors.length, "problem")} found. Fix the file and check it again.</strong>
                <ul style={{ margin: "6px 0 0", paddingLeft: 18, maxHeight: 180, overflowY: "auto" }}>
                  {check.errors.slice(0, 50).map((e) => (
                    <li key={`${e.row}-${e.message}`}>
                      Row {e.row}: {e.message}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Notice>
        )}
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

export function ProductsPage({ route, navigate }) {
  const toast = useToast();
  const [f, set, clear] = useFilters(route, navigate, { sort: "name" });
  const page = Number(f.page || 0);
  const query = productQuery(f);
  const products = useLoader(() => api(`/api/seller/products${qs({ ...query, page, size: PAGE_SIZE })}`), [JSON.stringify(query), page]);
  const categories = useCategories();
  const [selected, setSelected] = useState([]);
  const [importing, setImporting] = useState(false);
  const [archiving, setArchiving] = useState(null);
  const [toggling, setToggling] = useState(null);

  const toggleListing = async (product) => {
    setToggling(product.id);
    try {
      const updated = await api(`/api/seller/products/${product.id}/listing`, { method: "PATCH", body: { listed: !product.listed } });
      products.setData((data) => ({ ...data, content: data.content.map((p) => (p.id === updated.id ? updated : p)) }));
      toast(updated.listed ? `${updated.name} is listed for sale` : `${updated.name} is now inventory only`);
    } catch (error) {
      toast(error.message, "bad");
    } finally {
      setToggling(null);
    }
  };

  const archive = async () => {
    try {
      await api(`/api/seller/products/${archiving.id}`, { method: "DELETE" });
      toast(`${archiving.name} archived`);
      setArchiving(null);
      products.reload();
    } catch (error) {
      toast(error.message, "bad");
    }
  };

  const columns = [
    {
      key: "name",
      header: "Product",
      render: (p) => (
        <div className="product-cell">
          <span className="thumb">
            <ProductImage product={p} iconSize={18} />
          </span>
          <div style={{ minWidth: 0 }}>
            <a href={`#/seller/products/${p.id}`} className="cell-title" onClick={(e) => e.stopPropagation()}>
              {p.name}
            </a>
            <div className="cell-sub">
              {p.sku}
              {p.brand ? ` - ${p.brand}` : ""}
            </div>
          </div>
        </div>
      ),
    },
    { key: "category", header: "Department", render: (p) => p.categoryName || <span className="muted">None</span> },
    {
      key: "price",
      header: "Price",
      align: "right",
      render: (p) => (
        <>
          {formatMoney(p.price)}
          {p.discountPercent > 0 && (
            <div className="cell-sub">
              -{p.discountPercent}% off {formatMoney(p.compareAtPrice)}
            </div>
          )}
        </>
      ),
    },
    { key: "cost", header: "Cost", align: "right", render: (p) => formatMoney(p.costPrice) },
    {
      key: "margin",
      header: "Margin",
      align: "right",
      render: (p) => <span className={Number(p.marginPercent) < 10 ? "warn strong" : ""}>{formatPercent(p.marginPercent)}</span>,
    },
    {
      key: "stock",
      header: "Available",
      align: "right",
      render: (p) => (
        <>
          <span className="strong">{formatNumber(p.availableQuantity)}</span>
          <div className="cell-sub">{formatNumber(p.onHandQuantity)} on hand</div>
        </>
      ),
    },
    { key: "status", header: "Stock", render: (p) => <StatusBadge status={p.stockStatus} /> },
    {
      key: "listed",
      header: "For sale",
      render: (p) => (
        <span onClick={(e) => e.stopPropagation()} className="row" style={{ gap: 6 }}>
          <Switch checked={p.listed} disabled={toggling === p.id || !p.active} onChange={() => toggleListing(p)}>
            <span className="sr-only">
              {p.listed ? "Unlist" : "List"} {p.name}
            </span>
          </Switch>
          {p.listed && !p.visibleToCustomers && (
            <Badge tone="amber" icon={EyeOff}>
              Hidden
            </Badge>
          )}
        </span>
      ),
    },
    { key: "sold", header: "Sold", align: "right", render: (p) => formatNumber(p.unitsSold) },
    {
      key: "actions",
      header: "",
      width: 48,
      render: (p) => (
        <span onClick={(e) => e.stopPropagation()}>
          <Menu trigger={({ toggle }) => <IconButton icon={MoreHorizontal} label={`Actions for ${p.name}`} onClick={toggle} />}>
            <a className="menu-item" href={`#/seller/products/${p.id}`}>
              <Pencil /> Edit
            </a>
            <a className="menu-item" href={`#/seller/inventory?search=${encodeURIComponent(p.sku)}`}>
              <PackagePlus /> Stock and warehouses
            </a>
            {p.listed && p.visibleToCustomers && (
              <a className="menu-item" href={`#/product/${p.id}`}>
                <Store /> View on Nexora
              </a>
            )}
            <div className="menu-sep" />
            <button type="button" className="menu-item" onClick={() => setArchiving(p)}>
              <Archive /> Archive
            </button>
          </Menu>
        </span>
      ),
    },
  ];

  const data = products.data;
  const exportQuery = qs(query);

  return (
    <>
      <PageHead
        title="Products"
        sub="Your whole catalogue. Switch 'For sale' on to list a product on the marketplace; everything else stays inventory only."
        actions={
          <>
            <Button icon={FileUp} onClick={() => setImporting(true)}>
              Import
            </Button>
            <ExportMenu path="/api/seller/products/export/{format}" query={exportQuery} />
            <Button variant="primary" icon={PackagePlus} onClick={() => navigate("/seller/products/new")}>
              Add product
            </Button>
          </>
        }
      />
      <Panel flush>
        <ProductFilters f={f} set={set} clear={clear} right={data && <span className="small muted">{plural(data.totalElements, "product")}</span>} />
        {selected.length > 0 && (
          <BulkBar
            selected={selected}
            categories={categories}
            onClear={() => setSelected([])}
            onDone={() => {
              setSelected([]);
              invalidate("brands");
              products.reload();
            }}
          />
        )}
        {products.loading && !data ? (
          <Loader text="Loading products" />
        ) : products.error && !data ? (
          <ErrorState message={products.error} onRetry={products.reload} />
        ) : (
          <div style={{ opacity: products.loading ? 0.6 : 1 }}>
            <DataTable
              columns={columns}
              rows={data.content}
              selectable
              selected={selected}
              onSelect={setSelected}
              onRowClick={(p) => navigate(`/seller/products/${p.id}`)}
              empty={
                <Empty
                  icon={PackagePlus}
                  title={Object.keys(route.query).some((k) => k !== "sort") ? "No product matches these filters" : "Add your first product"}
                  action={
                    <Button variant="primary" onClick={() => navigate("/seller/products/new")}>
                      Add product
                    </Button>
                  }
                >
                  Products can be kept as inventory only or listed for sale on the marketplace.
                </Empty>
              }
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={PAGE_SIZE} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>

      <ImportDialog
        open={importing}
        onClose={() => setImporting(false)}
        onDone={() => {
          setImporting(false);
          invalidate("brands");
          products.reload();
        }}
      />
      <Confirm open={!!archiving} title={`Archive ${archiving?.name}?`} confirmLabel="Archive" danger onConfirm={archive} onClose={() => setArchiving(null)}>
        The product leaves your catalogue and the marketplace. Its order history and stock ledger are kept.
      </Confirm>
    </>
  );
}
