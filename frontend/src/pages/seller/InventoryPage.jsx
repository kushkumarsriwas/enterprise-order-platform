import { useState } from "react";
import {
  ArrowRightLeft,
  Boxes,
  CircleAlert,
  CircleCheck,
  ClipboardCheck,
  History,
  MapPin,
  MoreHorizontal,
  PackageMinus,
  PackagePlus,
  Scale,
} from "lucide-react";
import { api, qs } from "../../api.js";
import {
  Badge,
  Button,
  Checkbox,
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
  PillToggle,
  Select,
  Stat,
  StatusBadge,
  Tabs,
  Textarea,
} from "../../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, label } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import {
  ActiveFilters,
  DebouncedSearch,
  MultiSelect,
  PageHead,
  PeriodPicker,
  ProductPicker,
  Toolbar,
  defaultWarehouseId,
  listOf,
  useFilters,
  useWarehouses,
  warehouseOptions,
} from "./common.jsx";
import { ProductFilters, productQuery } from "./ProductsPage.jsx";
import { useSeller } from "./SellerLayout.jsx";

export const MOVEMENT_TYPES = [
  "OPENING_STOCK",
  "RECEIVED",
  "SOLD",
  "RETURNED",
  "ADJUSTMENT",
  "COUNT_ADJUSTMENT",
  "TRANSFER_OUT",
  "TRANSFER_IN",
  "RESERVED",
  "RELEASED",
  "EXPIRED",
];
const MOVEMENT_LABELS = {
  OPENING_STOCK: "Opening stock",
  RECEIVED: "Received",
  SOLD: "Sold",
  RETURNED: "Returned",
  ADJUSTMENT: "Adjustment",
  COUNT_ADJUSTMENT: "Count difference",
  TRANSFER_OUT: "Transfer out",
  TRANSFER_IN: "Transfer in",
  RESERVED: "Reserved for an order",
  RELEASED: "Reservation released",
  EXPIRED: "Reservation expired",
};
const REMOVE_REASONS = ["DAMAGED", "EXPIRED", "LOST", "INTERNAL_USE", "CORRECTION", "OTHER"];
const ADD_REASONS = ["FOUND", "CORRECTION", "OTHER"];
const ALL_REASONS = ["DAMAGED", "EXPIRED", "LOST", "FOUND", "CORRECTION", "INTERNAL_USE", "COUNT", "OTHER"];

/**
 * Change stock by hand: add or remove units with a reason, or set the counted quantity of one warehouse
 * (PUT /api/inventory/{productId}). Every change is written to the stock ledger.
 */
export function AdjustStockDialog({ product: initial, onClose, onDone }) {
  const toast = useToast();
  const warehouses = useWarehouses();
  const [product, setProduct] = useState(initial || null);
  const [mode, setMode] = useState("remove");
  const [warehouseId, setWarehouseId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("DAMAGED");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const position = useLoader(
    () => (product ? api(`/api/seller/inventory/${product.productId ?? product.id}`) : Promise.resolve(null)),
    [product?.productId ?? product?.id],
  );

  const chooseMode = (next) => {
    setMode(next);
    setReason(next === "remove" ? "DAMAGED" : next === "add" ? "FOUND" : "COUNT");
  };
  const chosenWarehouse = warehouseId || defaultWarehouseId(warehouses);
  const productId = product?.productId ?? product?.id;
  const inWarehouse = position.data?.warehouses?.find((w) => String(w.warehouseId) === String(chosenWarehouse))?.onHand ?? 0;
  const amount = parseInt(quantity, 10);
  const valid =
    productId && chosenWarehouse && Number.isInteger(amount) && (mode === "set" ? amount >= 0 : amount > 0) && (mode !== "remove" || amount <= inWarehouse);
  const after = mode === "set" ? amount : mode === "add" ? inWarehouse + (amount || 0) : inWarehouse - (amount || 0);

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      if (mode === "set") {
        await api(`/api/inventory/${productId}`, {
          method: "PUT",
          body: { warehouseId: Number(chosenWarehouse), onHand: amount, reason: "COUNT", note: note.trim() || null },
        });
      } else {
        await api("/api/seller/inventory/adjustments", {
          method: "POST",
          body: { productId, warehouseId: Number(chosenWarehouse), quantityChange: mode === "add" ? amount : -amount, reason, note: note.trim() || null },
        });
      }
      toast(`Stock of ${product.productName || product.name} updated`);
      onDone();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Adjust stock"
      sub="Every change is recorded in the stock ledger with your name and the reason."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} disabled={!valid} onClick={save}>
            Save adjustment
          </Button>
        </>
      }
    >
      <div className="stack">
        {product ? (
          <div className="row-between">
            <div>
              <div className="strong">{product.productName || product.name}</div>
              <div className="tiny muted">
                {product.sku} - {formatNumber(position.data?.availableQuantity ?? product.availableQuantity)} available,{" "}
                {formatNumber(position.data?.reservedQuantity ?? product.reservedQuantity ?? 0)} reserved
              </div>
            </div>
            {!initial && (
              <Button size="sm" variant="ghost" onClick={() => setProduct(null)}>
                Change
              </Button>
            )}
          </div>
        ) : (
          <Field label="Product">
            <ProductPicker onSelect={setProduct} autoFocus />
          </Field>
        )}
        <PillToggle
          label="Type of change"
          value={mode}
          onChange={chooseMode}
          options={[
            { value: "remove", label: "Remove units" },
            { value: "add", label: "Add units" },
            { value: "set", label: "Set counted quantity" },
          ]}
        />
        <div className="form-grid">
          <Field label="Warehouse" hint={product ? `${formatNumber(inWarehouse)} on hand here` : null}>
            <Select value={chosenWarehouse} onChange={(e) => setWarehouseId(e.target.value)} options={warehouseOptions(warehouses)} />
          </Field>
          <Field
            label={mode === "set" ? "Counted quantity" : "Units"}
            error={mode === "remove" && amount > inWarehouse ? `Only ${formatNumber(inWarehouse)} on hand in this warehouse` : null}
            hint={product && Number.isInteger(amount) ? `${formatNumber(inWarehouse)} becomes ${formatNumber(after)}` : null}
          >
            <Input type="number" min="0" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </Field>
          {mode !== "set" && (
            <Field label="Reason" className="span-2">
              <Select
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                options={(mode === "remove" ? REMOVE_REASONS : ADD_REASONS).map((r) => ({ value: r, label: label(r) }))}
              />
            </Field>
          )}
          <Field label="Note (optional)" className="span-2" hint="For example the batch, what happened or who found it">
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} rows={2} />
          </Field>
        </div>
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

function BinDialog({ row, onClose, onDone }) {
  const toast = useToast();
  const warehouses = useWarehouses();
  const position = useLoader(() => api(`/api/seller/inventory/${row.productId}`), [row.productId]);
  const [warehouseId, setWarehouseId] = useState("");
  const [edited, setEdited] = useState(null);
  const [busy, setBusy] = useState(false);
  const chosenWarehouse = warehouseId || defaultWarehouseId(warehouses);
  const current = position.data?.warehouses?.find((w) => String(w.warehouseId) === String(chosenWarehouse));
  const bin = edited ?? current?.binLocation ?? "";
  return (
    <Modal
      open
      onClose={onClose}
      title={`Shelf location of ${row.productName}`}
      sub="Printed on packing slips and count sheets so pickers find it fast."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={busy}
            disabled={!chosenWarehouse}
            onClick={async () => {
              setBusy(true);
              try {
                await api(`/api/seller/inventory/${row.productId}/bin`, {
                  method: "PUT",
                  body: { warehouseId: Number(chosenWarehouse), binLocation: bin.trim() || null },
                });
                toast("Shelf location saved");
                onDone();
              } catch (e) {
                toast(e.message, "bad");
              } finally {
                setBusy(false);
              }
            }}
          >
            Save
          </Button>
        </>
      }
    >
      <div className="form-grid">
        <Field label="Warehouse">
          <Select
            value={chosenWarehouse}
            onChange={(e) => {
              setWarehouseId(e.target.value);
              setEdited(null);
            }}
            options={warehouseOptions(warehouses)}
          />
        </Field>
        <Field label="Bin or shelf" hint="For example A-03-2">
          <Input value={bin} onChange={(e) => setEdited(e.target.value)} maxLength={30} />
        </Field>
      </div>
    </Modal>
  );
}

function SummaryTiles({ summary }) {
  if (!summary) return null;
  return (
    <div className="stats" style={{ marginBottom: 16 }}>
      <Stat label="Units on hand" icon={Boxes} value={formatNumber(summary.unitsOnHand)} hint={`${formatNumber(summary.unitsReserved)} reserved for orders`} />
      <Stat
        label="Incoming"
        icon={PackagePlus}
        value={formatNumber(summary.unitsIncoming)}
        hint={`${formatNumber(summary.openPurchaseOrders)} open purchase orders`}
      />
      <Stat
        label="Stock value at cost"
        icon={Scale}
        value={formatMoney(summary.stockValueAtCost)}
        hint={`${formatMoney(summary.stockValueAtPrice)} at selling price`}
      />
      <Stat
        label="Low or out of stock"
        icon={PackageMinus}
        value={formatNumber(summary.lowStockProducts + summary.outOfStockProducts)}
        hint={`${formatNumber(summary.outOfStockProducts)} out of stock`}
        attention={summary.lowStockProducts + summary.outOfStockProducts > 0}
      />
    </div>
  );
}

function StockLevels({ route, navigate, onAdjust }) {
  const [f, set, clear] = useFilters(route, navigate, { sort: "name", tab: "levels" });
  const page = Number(f.page || 0);
  const query = productQuery(f);
  const rows = useLoader(() => api(`/api/seller/inventory${qs({ ...query, page, size: 25 })}`), [JSON.stringify(query), page]);
  const [bin, setBin] = useState(null);
  const data = rows.data;
  const byWarehouse = !!f.warehouseId;

  return (
    <Panel flush>
      <ProductFilters
        f={f}
        set={set}
        clear={() => clear(["sort", "tab"])}
        right={<ExportMenu path="/api/seller/inventory/export/{format}" query={qs(query)} size="sm" />}
      />
      {rows.loading && !data ? (
        <Loader text="Loading stock levels" />
      ) : rows.error && !data ? (
        <ErrorState message={rows.error} onRetry={rows.reload} />
      ) : (
        <div style={{ opacity: rows.loading ? 0.6 : 1 }}>
          <DataTable
            rowKey="productId"
            rows={data.content}
            onRowClick={(r) => navigate(`/seller/products/${r.productId}`)}
            empty={<Empty icon={Boxes} title="No product matches these filters" />}
            columns={[
              {
                key: "product",
                header: "Product",
                render: (r) => (
                  <div>
                    <div className="cell-title">{r.productName}</div>
                    <div className="cell-sub">
                      {r.sku} - {r.categoryName || "No department"}
                      {!r.listed && " - inventory only"}
                    </div>
                  </div>
                ),
              },
              ...(byWarehouse
                ? [
                    {
                      key: "wh",
                      header: "In this warehouse",
                      align: "right",
                      render: (r) => <span className="strong">{formatNumber(r.warehouseOnHand ?? 0)}</span>,
                    },
                    { key: "bin", header: "Bin", render: (r) => r.binLocation || <span className="muted">-</span> },
                  ]
                : []),
              { key: "available", header: "Available", align: "right", render: (r) => <span className="strong">{formatNumber(r.availableQuantity)}</span> },
              { key: "reserved", header: "Reserved", align: "right", render: (r) => formatNumber(r.reservedQuantity) },
              { key: "onHand", header: "On hand", align: "right", render: (r) => formatNumber(r.onHandQuantity) },
              {
                key: "incoming",
                header: "Incoming",
                align: "right",
                render: (r) => (r.incomingQuantity ? `+${formatNumber(r.incomingQuantity)}` : <span className="muted">-</span>),
              },
              { key: "reorder", header: "Reorder at", align: "right", render: (r) => formatNumber(r.reorderPoint) },
              { key: "status", header: "Status", render: (r) => <StatusBadge status={r.stockStatus} /> },
              { key: "value", header: "Value at cost", align: "right", render: (r) => formatMoney(r.stockValueAtCost) },
              {
                key: "actions",
                header: "",
                width: 48,
                render: (r) => (
                  <span onClick={(e) => e.stopPropagation()}>
                    <Menu trigger={({ toggle }) => <IconButton icon={MoreHorizontal} label={`Actions for ${r.productName}`} onClick={toggle} />}>
                      <button type="button" className="menu-item" onClick={() => onAdjust(r)}>
                        <PackageMinus /> Adjust stock
                      </button>
                      <button type="button" className="menu-item" onClick={() => setBin(r)}>
                        <MapPin /> Shelf location
                      </button>
                      <a className="menu-item" href={`#/seller/warehouses?tab=transfers&product=${r.productId}`}>
                        <ArrowRightLeft /> Move to another warehouse
                      </a>
                      <a className="menu-item" href={`#/seller/inventory?tab=ledger&productId=${r.productId}`}>
                        <History /> Stock ledger
                      </a>
                    </Menu>
                  </span>
                ),
              },
            ]}
          />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={25} onPage={(p) => set({ page: p })} />
        </div>
      )}
      {bin && (
        <BinDialog
          row={bin}
          onClose={() => setBin(null)}
          onDone={() => {
            setBin(null);
            rows.reload();
          }}
        />
      )}
    </Panel>
  );
}

function Ledger({ route, navigate }) {
  const warehouses = useWarehouses();
  const [f, set, clear] = useFilters(route, navigate, { tab: "levels", physicalOnly: "true" });
  const page = Number(f.page || 0);
  const types = listOf(f.type);
  const query = {
    productId: f.productId,
    warehouseId: f.warehouseId,
    type: types,
    reason: f.reason,
    search: f.search,
    from: f.from,
    to: f.to,
    physicalOnly: f.physicalOnly === "true",
  };
  const rows = useLoader(() => api(`/api/seller/inventory/movements${qs({ ...query, page, size: 30 })}`), [JSON.stringify(query), page]);
  const product = useLoader(() => (f.productId ? api(`/api/seller/products/${f.productId}`).catch(() => null) : Promise.resolve(null)), [f.productId]);
  const data = rows.data;

  return (
    <Panel flush>
      <Toolbar right={<ExportMenu path="/api/seller/inventory/movements/export/{format}" query={qs(query)} size="sm" />}>
        <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="SKU, product or reference" />
        <Select
          className="select-sm"
          value={f.warehouseId || ""}
          placeholder="All warehouses"
          options={warehouseOptions(warehouses, { activeOnly: false })}
          onChange={(e) => set({ warehouseId: e.target.value })}
          aria-label="Warehouse"
        />
        <MultiSelect
          label="Movement type"
          options={MOVEMENT_TYPES.map((t) => ({ value: t, label: MOVEMENT_LABELS[t] }))}
          value={types}
          onChange={(list) => set({ type: list.join(",") })}
        />
        <Select
          className="select-sm"
          value={f.reason || ""}
          placeholder="Any reason"
          options={ALL_REASONS.map((r) => ({ value: r, label: label(r) }))}
          onChange={(e) => set({ reason: e.target.value })}
          aria-label="Reason"
        />
        <PeriodPicker allowEmpty from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
        <Checkbox checked={f.physicalOnly !== "true"} onChange={(v) => set({ physicalOnly: v ? "false" : "true" })}>
          Include reservations
        </Checkbox>
      </Toolbar>
      <ActiveFilters
        onClear={() => clear(["tab"])}
        chips={[
          { key: "product", label: product.data ? product.data.product.name : "One product", value: f.productId, onRemove: () => set({ productId: "" }) },
          { key: "search", label: `"${f.search}"`, value: f.search, onRemove: () => set({ search: "" }) },
          { key: "dates", label: `${f.from || "start"} to ${f.to || "today"}`, value: f.from || f.to, onRemove: () => set({ from: "", to: "" }) },
        ]}
      />
      {rows.loading && !data ? (
        <Loader text="Loading the ledger" />
      ) : rows.error && !data ? (
        <ErrorState message={rows.error} onRetry={rows.reload} />
      ) : (
        <div style={{ opacity: rows.loading ? 0.6 : 1 }}>
          <DataTable
            rows={data.content}
            empty={<Empty icon={History} title="No stock movements match" />}
            columns={[
              { key: "when", header: "When", render: (m) => <span className="nowrap">{formatDate(m.createdAt)}</span> },
              {
                key: "product",
                header: "Product",
                render: (m) => (
                  <div>
                    <a className="cell-title" href={`#/seller/products/${m.productId}`}>
                      {m.productName}
                    </a>
                    <div className="cell-sub">{m.sku}</div>
                  </div>
                ),
              },
              { key: "type", header: "Movement", render: (m) => MOVEMENT_LABELS[m.type] || label(m.type) },
              { key: "wh", header: "Warehouse", render: (m) => m.warehouseCode || <span className="muted">-</span> },
              {
                key: "qty",
                header: "Units",
                align: "right",
                render: (m) => (
                  <span className={`strong ${m.quantity < 0 ? "bad" : "good"}`}>
                    {m.quantity > 0 ? "+" : ""}
                    {formatNumber(m.quantity)}
                  </span>
                ),
              },
              { key: "balance", header: "Balance", align: "right", render: (m) => m.balanceAfter ?? "-" },
              { key: "value", header: "Value", align: "right", render: (m) => (m.value ? formatMoney(m.value) : "-") },
              {
                key: "why",
                header: "Reason and reference",
                render: (m) => (
                  <div>
                    {m.reason && <Badge>{label(m.reason)}</Badge>} <span className="small">{m.reference}</span>
                    {m.note && <div className="cell-sub">{m.note}</div>}
                  </div>
                ),
              },
              { key: "by", header: "By", render: (m) => <span className="small muted">{m.performedBy}</span> },
            ]}
          />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={30} onPage={(p) => set({ page: p })} />
        </div>
      )}
    </Panel>
  );
}

function IntegrityCheck() {
  const check = useLoader(() => api("/api/seller/inventory/integrity"), []);
  return (
    <Panel
      title="Reconciliation check"
      sub="For every product, available plus reserved stock must equal the units counted across your warehouses."
      actions={
        <Button size="sm" onClick={check.reload} loading={check.loading}>
          Run again
        </Button>
      }
    >
      {check.loading && !check.data ? (
        <Loader text="Checking every product" />
      ) : check.error && !check.data ? (
        <ErrorState message={check.error} onRetry={check.reload} />
      ) : check.data.length === 0 ? (
        <Notice tone="good" icon={CircleCheck}>
          <strong>Everything adds up.</strong> Stock totals match the warehouse quantities for all your products.
        </Notice>
      ) : (
        <DataTable
          rowKey="productId"
          rows={check.data}
          columns={[
            { key: "sku", header: "SKU", render: (r) => <a href={`#/seller/products/${r.productId}`}>{r.sku}</a> },
            { key: "total", header: "Available + reserved", align: "right", render: (r) => formatNumber(r.availablePlusReserved) },
            { key: "wh", header: "Sum of warehouses", align: "right", render: (r) => formatNumber(r.sumOfWarehouses) },
            {
              key: "diff",
              header: "Difference",
              align: "right",
              render: (r) => <span className="bad strong">{formatNumber(r.availablePlusReserved - r.sumOfWarehouses)}</span>,
            },
          ]}
        />
      )}
    </Panel>
  );
}

export function InventoryPage({ route, navigate }) {
  const { reloadCounts } = useSeller();
  const tab = route.query.tab || "levels";
  const summary = useLoader(() => api("/api/seller/inventory/summary"), []);
  const [adjusting, setAdjusting] = useState(null);
  const [version, setVersion] = useState(0);

  return (
    <>
      <PageHead
        title="Inventory"
        sub="Stock of every product in every warehouse, including products you don't sell online."
        actions={
          <>
            <Button icon={ClipboardCheck} onClick={() => navigate("/seller/stock-counts")}>
              Count stock
            </Button>
            <Button icon={ArrowRightLeft} onClick={() => navigate("/seller/warehouses?tab=transfers")}>
              Transfer
            </Button>
            <Button variant="primary" icon={PackageMinus} onClick={() => setAdjusting({})}>
              Adjust stock
            </Button>
          </>
        }
      />
      <SummaryTiles summary={summary.data} />
      <Tabs
        className="tabs-page"
        value={tab}
        onChange={(id) => navigate(`/seller/inventory${qs({ tab: id === "levels" ? "" : id })}`, { replace: true, keepScroll: true })}
        tabs={[
          { id: "levels", label: "Stock levels" },
          { id: "ledger", label: "Stock ledger" },
          { id: "check", label: "Reconciliation" },
        ]}
      />
      <div style={{ marginTop: 14 }}>
        {tab === "ledger" ? (
          <Ledger key={version} route={route} navigate={navigate} />
        ) : tab === "check" ? (
          <IntegrityCheck />
        ) : (
          <StockLevels key={version} route={route} navigate={navigate} onAdjust={(row) => setAdjusting(row)} />
        )}
      </div>
      {adjusting && (
        <AdjustStockDialog
          product={adjusting.productId ? adjusting : null}
          onClose={() => setAdjusting(null)}
          onDone={() => {
            setAdjusting(null);
            setVersion((v) => v + 1);
            summary.reload();
            reloadCounts();
          }}
        />
      )}
    </>
  );
}
