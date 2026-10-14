import { useMemo, useState } from "react";
import { ArrowDownToLine, Boxes, ClipboardCheck, PackageOpen, Search } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDate, formatMoney, formatNumber, label } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { Alert, Badge, Empty, Field, Modal, PageHeader, Spinner, StatTile, Tabs } from "../../components/ui.jsx";
import { ReportButtons, usePeriod } from "./common.jsx";

const MOVEMENT_TONE = { INITIAL: "blue", RESTOCK: "green", ADJUSTMENT: "violet", RESERVED: "amber", SOLD: "gray", RELEASED: "blue", RETURNED: "green" };
const MOVEMENT_TEXT = {
  INITIAL: "Opening stock",
  RESTOCK: "Restocked",
  ADJUSTMENT: "Stock count",
  RESERVED: "Reserved for an order",
  SOLD: "Sold",
  RELEASED: "Released (order cancelled or unpaid)",
  RETURNED: "Returned",
};

export default function MerchantInventory({ toast }) {
  const [tab, setTab] = useState("stock");
  const summary = useLoader(() => api("/api/merchant/inventory/summary"), []);
  const stock = useLoader(() => api("/api/merchant/inventory"), []);
  const s = summary.data;
  const refresh = () => {
    summary.reload();
    stock.reload();
  };

  return (
    <div className="page">
      <PageHeader
        title="Inventory"
        subtitle="Stock on hand, what is reserved for open orders, and what your stock is worth. Costs use the weighted average of your purchases."
        actions={<ReportButtons path="/api/merchant/reports/inventory" toast={toast} compact />}
      />
      <Alert>{summary.error}</Alert>
      <div className="stats">
        <StatTile icon={Boxes} label="Units on hand" value={s ? formatNumber(s.unitsOnHand) : "-"} hint={s ? `${s.products} products, ${s.listedProducts} on the marketplace` : null} />
        <StatTile icon={PackageOpen} label="Reserved for orders" value={s ? formatNumber(s.unitsReserved) : "-"} hint="Held until the order is paid or released" />
        <StatTile icon={ArrowDownToLine} label="Stock value at cost" value={s ? formatMoney(s.stockValueAtCost) : "-"} hint={s ? `${formatMoney(s.stockValueAtPrice)} at selling price` : null} />
        <StatTile icon={ClipboardCheck} label="Potential gross profit" value={s ? formatMoney(s.potentialProfit) : "-"} hint={s ? `${s.lowStockProducts} low, ${s.outOfStockProducts} out of stock` : null} />
      </div>
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { id: "stock", label: "Stock levels" },
          { id: "movements", label: "Stock movements" },
          { id: "purchases", label: "Purchases" },
        ]}
      />
      {tab === "stock" && <StockLevels stock={stock} onChanged={refresh} toast={toast} />}
      {tab === "movements" && <Movements products={stock.data || []} />}
      {tab === "purchases" && <Purchases />}
    </div>
  );
}

function StockLevels({ stock, onChanged, toast }) {
  const [search, setSearch] = useState("");
  const [lowOnly, setLowOnly] = useState(false);
  const [dialog, setDialog] = useState(null);
  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (stock.data || []).filter(
      (item) => (!lowOnly || item.lowStock || item.availableQuantity === 0) && (!term || item.productName.toLowerCase().includes(term) || item.sku.toLowerCase().includes(term))
    );
  }, [stock.data, search, lowOnly]);

  if (stock.loading && !stock.data) return <Spinner />;
  if (stock.error) return <Alert>{stock.error}</Alert>;
  if (!stock.data.length)
    return (
      <Empty icon={Boxes} title="No stock yet" action={<a className="btn btn-primary" href="#/merchant/products?new=1">Add a product</a>}>
        Add products with an opening stock, then restock them here when new deliveries arrive.
      </Empty>
    );
  return (
    <>
      <div className="toolbar">
        <div className="search">
          <Search size={16} />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search by name or SKU" aria-label="Search stock" />
        </div>
        <label className="check">
          <input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} /> Low or out of stock only
        </label>
      </div>
      <div className={`card flush ${stock.loading ? "refetching" : ""}`}>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Product</th>
                <th className="num">Available</th>
                <th className="num">Reserved</th>
                <th className="num">Reorder level</th>
                <th className="num">Avg unit cost</th>
                <th className="num">Value at cost</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((item) => (
                <tr key={item.productId}>
                  <td>
                    <strong>{item.productName}</strong>
                    <span className="sub">
                      {item.sku} - {item.listed ? "on the marketplace" : "inventory only"}
                    </span>
                  </td>
                  <td className="num">{formatNumber(item.availableQuantity)}</td>
                  <td className="num">{formatNumber(item.reservedQuantity)}</td>
                  <td className="num">{formatNumber(item.reorderLevel)}</td>
                  <td className="num">{formatMoney(item.unitCost)}</td>
                  <td className="num">{formatMoney(item.stockValueAtCost)}</td>
                  <td>
                    {item.availableQuantity === 0 ? <Badge value="OUT_OF_STOCK" /> : item.lowStock ? <Badge value="LOW_STOCK" /> : <Badge value="ACTIVE" text="Healthy" />}
                  </td>
                  <td className="nowrap right">
                    <button className="btn btn-sm" onClick={() => setDialog({ type: "restock", item })}>
                      Restock
                    </button>{" "}
                    <button className="btn btn-sm btn-ghost" onClick={() => setDialog({ type: "count", item })}>
                      Count
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      {dialog?.type === "restock" && (
        <RestockDialog
          item={dialog.item}
          onClose={() => setDialog(null)}
          onDone={(result) => {
            setDialog(null);
            toast.show(`${result.productName}: ${result.availableQuantity} available, average cost ${formatMoney(result.unitCost)}`);
            onChanged();
          }}
        />
      )}
      {dialog?.type === "count" && (
        <CountDialog
          item={dialog.item}
          onClose={() => setDialog(null)}
          onDone={(result) => {
            setDialog(null);
            toast.show(`${result.productName} stock set to ${result.availableQuantity}`);
            onChanged();
          }}
        />
      )}
    </>
  );
}

function RestockDialog({ item, onClose, onDone }) {
  const [form, setForm] = useState({ quantity: "", unitCost: String(item.unitCost ?? ""), supplier: "", reference: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const quantity = Number(form.quantity || 0);
  const newAverage =
    quantity > 0 && form.unitCost !== ""
      ? (item.availableQuantity * Number(item.unitCost || 0) + quantity * Number(form.unitCost)) / (item.availableQuantity + quantity)
      : null;

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api(`/api/merchant/inventory/${item.productId}/restock`, {
        method: "POST",
        body: { quantity, unitCost: form.unitCost, supplier: form.supplier || null, reference: form.reference || null },
      });
      onDone(result);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
      setBusy(false);
    }
  };

  return (
    <Modal title={`Restock ${item.productName}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <p className="muted">Record a delivery from your supplier. It is saved as a purchase and updates the average unit cost.</p>
        <div className="form-grid">
          <Field label="Quantity received" error={fieldErrors.quantity}>
            <input type="number" min="1" value={form.quantity} onChange={set("quantity")} required autoFocus />
          </Field>
          <Field label="Cost per unit (AED)" error={fieldErrors.unitCost}>
            <input type="number" min="0" step="0.01" value={form.unitCost} onChange={set("unitCost")} required />
          </Field>
          <Field label="Supplier" error={fieldErrors.supplier}>
            <input value={form.supplier} onChange={set("supplier")} maxLength={100} />
          </Field>
          <Field label="Invoice number" error={fieldErrors.reference}>
            <input value={form.reference} onChange={set("reference")} maxLength={60} />
          </Field>
        </div>
        {newAverage !== null && (
          <Alert kind="info">
            Stock after this delivery: {item.availableQuantity + quantity} units. New average cost: {formatMoney(newAverage)} (was {formatMoney(item.unitCost)}).
            Purchase total: {formatMoney(quantity * Number(form.unitCost))}.
          </Alert>
        )}
        <Alert onClose={() => setError("")}>{error}</Alert>
        <div className="row end">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Saving..." : "Record delivery"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function CountDialog({ item, onClose, onDone }) {
  const [form, setForm] = useState({ availableQuantity: String(item.availableQuantity), reorderLevel: String(item.reorderLevel), note: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const difference = Number(form.availableQuantity || 0) - item.availableQuantity;

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api(`/api/merchant/inventory/${item.productId}`, {
        method: "PUT",
        body: { availableQuantity: Number(form.availableQuantity), reorderLevel: Number(form.reorderLevel), note: form.note || null },
      });
      onDone(result);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal title={`Stock count: ${item.productName}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <p className="muted">Correct the stock after a physical count (damaged, lost or found items). Every change is kept in the stock movements.</p>
        <div className="form-grid">
          <Field label="Units actually available" hint={difference ? `${difference > 0 ? "+" : ""}${difference} compared with the system` : "No change"}>
            <input type="number" min="0" value={form.availableQuantity} onChange={set("availableQuantity")} required autoFocus />
          </Field>
          <Field label="Reorder level">
            <input type="number" min="0" value={form.reorderLevel} onChange={set("reorderLevel")} required />
          </Field>
          <Field label="Reason" className="span-2">
            <input value={form.note} onChange={set("note")} maxLength={255} placeholder="For example: 2 units damaged in the warehouse" />
          </Field>
        </div>
        <Alert onClose={() => setError("")}>{error}</Alert>
        <div className="row end">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Saving..." : "Save count"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function Movements({ products }) {
  const [productId, setProductId] = useState("");
  const movements = useLoader(() => api(`/api/merchant/inventory/movements${qs({ productId })}`), [productId]);
  const names = useMemo(() => new Map(products.map((p) => [p.productId, p.productName])), [products]);
  return (
    <>
      <div className="toolbar">
        <select value={productId} onChange={(e) => setProductId(e.target.value)} aria-label="Product">
          <option value="">All products (latest 200)</option>
          {products.map((p) => (
            <option key={p.productId} value={p.productId}>
              {p.productName}
            </option>
          ))}
        </select>
      </div>
      <Alert>{movements.error}</Alert>
      {movements.loading && !movements.data ? (
        <Spinner />
      ) : movements.data?.length === 0 ? (
        <Empty icon={Boxes} title="No stock movements yet">Every change to your stock is recorded here: opening stock, restocks, counts, reservations and sales.</Empty>
      ) : (
        <div className="card flush">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Product</th>
                  <th>Movement</th>
                  <th className="num">Change</th>
                  <th>Reference</th>
                  <th>By</th>
                </tr>
              </thead>
              <tbody>
                {movements.data?.map((m) => (
                  <tr key={m.id}>
                    <td className="nowrap">{formatDate(m.createdAt)}</td>
                    <td>{names.get(m.productId) || `#${m.productId}`}</td>
                    <td>
                      <Badge value={m.type} tone={MOVEMENT_TONE[m.type]} text={MOVEMENT_TEXT[m.type] || label(m.type)} />
                      {m.note && <span className="sub">{m.note}</span>}
                    </td>
                    <td className={`num ${m.quantityChange < 0 ? "neg" : "pos"}`}>
                      {m.quantityChange > 0 ? "+" : ""}
                      {m.quantityChange}
                    </td>
                    <td className="mono">{m.reference || "-"}</td>
                    <td>{m.performedBy}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}

function Purchases() {
  const period = usePeriod("90d", ["30d", "90d", "6m", "ytd"]);
  const { from, to } = period.range;
  const purchases = useLoader(() => api(`/api/merchant/purchases${qs({ from, to })}`), [from, to]);
  const total = (purchases.data || []).reduce((sum, p) => sum + Number(p.totalCost), 0);
  return (
    <>
      <div className="toolbar">{period.picker}</div>
      <Alert>{purchases.error}</Alert>
      {purchases.loading && !purchases.data ? (
        <Spinner />
      ) : purchases.data?.length === 0 ? (
        <Empty icon={ArrowDownToLine} title="No purchases in this period">Opening stock and restocks are recorded as purchases.</Empty>
      ) : (
        <div className="card flush">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Product</th>
                  <th className="num">Quantity</th>
                  <th className="num">Unit cost</th>
                  <th className="num">Total</th>
                  <th>Supplier</th>
                  <th>Invoice</th>
                </tr>
              </thead>
              <tbody>
                {purchases.data?.map((p) => (
                  <tr key={p.id}>
                    <td className="nowrap">{formatDate(p.purchasedAt)}</td>
                    <td>
                      {p.productName}
                      <span className="sub">{p.sku}</span>
                    </td>
                    <td className="num">{formatNumber(p.quantity)}</td>
                    <td className="num">{formatMoney(p.unitCost)}</td>
                    <td className="num">{formatMoney(p.totalCost)}</td>
                    <td>{p.supplier || "-"}</td>
                    <td className="mono">{p.reference || "-"}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={4}>Total purchases</td>
                  <td className="num">{formatMoney(total)}</td>
                  <td colSpan={2} />
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
