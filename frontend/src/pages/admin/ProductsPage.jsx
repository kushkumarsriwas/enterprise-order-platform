import { useState } from "react";
import { History, PackagePlus, Pencil, Search, Trash2, Warehouse } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDate, formatMoney } from "../../format.js";
import { Alert, Badge, Field, Modal, Pagination, Spinner } from "../../components/ui.jsx";
import { useLoader } from "../../hooks.jsx";

const EMPTY = { sku: "", name: "", description: "", price: "", categoryId: "", imageUrl: "", initialStock: 10, reorderLevel: 5, active: true };

export default function ProductsPage({ toast }) {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState(null);
  const [stockFor, setStockFor] = useState(null);
  const [historyFor, setHistoryFor] = useState(null);
  const products = useLoader(
    () => api(`/api/products${qs({ search, page, size: 10, includeInactive: true, sort: "name,asc" })}`),
    [page, search]
  );
  const categories = useLoader(() => api("/api/categories", { auth: false }), []);

  const deactivate = async (product) => {
    if (!window.confirm(`Deactivate ${product.name}? It disappears from the shop; past orders are kept.`)) return;
    try {
      await api(`/api/products/${product.id}`, { method: "DELETE" });
      toast.show("Product deactivated");
      products.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <h1>Products &amp; inventory</h1>
        <button className="btn btn-primary" onClick={() => setEditing(EMPTY)}><PackagePlus size={16} /> New product</button>
      </div>
      <div className="toolbar">
        <div className="search">
          <Search size={16} />
          <input placeholder="Search products" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
        </div>
      </div>
      <Alert>{products.error}</Alert>
      {products.loading && !products.data ? <Spinner /> : (
        <div className="card">
          <table className="table">
            <thead>
              <tr><th>Product</th><th>Category</th><th>Price</th><th>Available</th><th>Reserved</th><th>Status</th><th className="right">Actions</th></tr>
            </thead>
            <tbody>
              {products.data?.content.map((p) => (
                <tr key={p.id} className={p.active ? "" : "inactive"}>
                  <td><strong>{p.name}</strong><div className="muted small">{p.sku}</div></td>
                  <td>{p.categoryName || "-"}</td>
                  <td>{formatMoney(p.price, p.currency)}</td>
                  <td><Badge value={String(p.availableQuantity)} tone={p.availableQuantity === 0 ? "red" : p.lowStock ? "amber" : "green"} /></td>
                  <td>{p.reservedQuantity}</td>
                  <td><Badge value={p.active ? "ACTIVE" : "INACTIVE"} tone={p.active ? "green" : "gray"} /></td>
                  <td className="right nowrap">
                    <button className="icon-btn" title="Edit" onClick={() => setEditing({ ...p, categoryId: p.categoryId || "" })}><Pencil size={15} /></button>
                    <button className="icon-btn" title="Stock" onClick={() => setStockFor(p)}><Warehouse size={15} /></button>
                    <button className="icon-btn" title="Stock history" onClick={() => setHistoryFor(p)}><History size={15} /></button>
                    {p.active && <button className="icon-btn danger" title="Deactivate" onClick={() => deactivate(p)}><Trash2 size={15} /></button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={products.data} onChange={setPage} />
      {editing && (
        <ProductModal product={editing} categories={categories.data || []} onClose={() => setEditing(null)}
          onSaved={(message) => { setEditing(null); toast.show(message); products.reload(); categories.reload(); }} />
      )}
      {stockFor && (
        <StockModal product={stockFor} onClose={() => setStockFor(null)}
          onSaved={() => { setStockFor(null); toast.show("Stock updated (audited)"); products.reload(); }} />
      )}
      {historyFor && <HistoryModal product={historyFor} onClose={() => setHistoryFor(null)} />}
    </div>
  );
}

function ProductModal({ product, categories, onClose, onSaved }) {
  const isNew = !product.id;
  const [form, setForm] = useState(product);
  const [newCategory, setNewCategory] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  const save = async (event) => {
    event.preventDefault();
    setError("");
    setFieldErrors({});
    try {
      let categoryId = form.categoryId ? Number(form.categoryId) : null;
      if (form.categoryId === "__new") {
        categoryId = (await api("/api/categories", { method: "POST", body: { name: newCategory } })).id;
      }
      const common = { name: form.name, description: form.description, price: Number(form.price), categoryId, imageUrl: form.imageUrl || null };
      if (isNew) {
        await api("/api/products", { method: "POST", body: { ...common, sku: form.sku, initialStock: Number(form.initialStock), reorderLevel: Number(form.reorderLevel) } });
      } else {
        await api(`/api/products/${product.id}`, { method: "PUT", body: { ...common, active: form.active } });
      }
      onSaved(isNew ? "Product created" : "Product updated");
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
    }
  };

  return (
    <Modal title={isNew ? "New product" : `Edit ${product.name}`} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <Alert>{error}</Alert>
        <div className="row">
          <Field label="SKU" error={fieldErrors.sku}><input value={form.sku} onChange={set("sku")} disabled={!isNew} required /></Field>
          <Field label="Price (AED)" error={fieldErrors.price}><input type="number" step="0.01" min="0.01" value={form.price} onChange={set("price")} required /></Field>
        </div>
        <Field label="Name" error={fieldErrors.name}><input value={form.name} onChange={set("name")} required /></Field>
        <Field label="Description" error={fieldErrors.description}><textarea rows={3} value={form.description || ""} onChange={set("description")} /></Field>
        <Field label="Category">
          <select value={form.categoryId} onChange={set("categoryId")}>
            <option value="">No category</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            <option value="__new">+ New category...</option>
          </select>
        </Field>
        {form.categoryId === "__new" && <Field label="New category name"><input value={newCategory} onChange={(e) => setNewCategory(e.target.value)} required /></Field>}
        {isNew ? (
          <div className="row">
            <Field label="Initial stock" error={fieldErrors.initialStock}><input type="number" min="0" value={form.initialStock} onChange={set("initialStock")} required /></Field>
            <Field label="Reorder level" error={fieldErrors.reorderLevel}><input type="number" min="0" value={form.reorderLevel} onChange={set("reorderLevel")} /></Field>
          </div>
        ) : (
          <label className="check"><input type="checkbox" checked={form.active} onChange={set("active")} /> Active (visible in the shop)</label>
        )}
        <button className="btn btn-primary btn-block">{isNew ? "Create product" : "Save changes"}</button>
      </form>
    </Modal>
  );
}

function StockModal({ product, onClose, onSaved }) {
  const [mode, setMode] = useState("restock");
  const [quantity, setQuantity] = useState(10);
  const [available, setAvailable] = useState(product.availableQuantity);
  const [reorderLevel, setReorderLevel] = useState(product.reorderLevel);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");

  const save = async (event) => {
    event.preventDefault();
    try {
      if (mode === "restock") {
        await api(`/api/inventory/${product.id}/restock`, { method: "POST", body: { quantity: Number(quantity), note: note || null } });
      } else {
        await api(`/api/inventory/${product.id}`, { method: "PUT", body: { availableQuantity: Number(available), reorderLevel: Number(reorderLevel), note: note || null } });
      }
      onSaved();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <Modal title={`Stock: ${product.name}`} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <p className="muted">Available {product.availableQuantity} · Reserved for unpaid orders {product.reservedQuantity}</p>
        <div className="method-picker">
          <button type="button" className={mode === "restock" ? "active" : ""} onClick={() => setMode("restock")}>Receive goods</button>
          <button type="button" className={mode === "adjust" ? "active" : ""} onClick={() => setMode("adjust")}>Stock count</button>
        </div>
        {mode === "restock" ? (
          <Field label="Units received"><input type="number" min="1" value={quantity} onChange={(e) => setQuantity(e.target.value)} required /></Field>
        ) : (
          <div className="row">
            <Field label="Counted available units"><input type="number" min="0" value={available} onChange={(e) => setAvailable(e.target.value)} required /></Field>
            <Field label="Reorder level"><input type="number" min="0" value={reorderLevel} onChange={(e) => setReorderLevel(e.target.value)} /></Field>
          </div>
        )}
        <Field label="Note (audit trail)"><input value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. PO-2291 from supplier" /></Field>
        <Alert>{error}</Alert>
        <button className="btn btn-primary btn-block">Save</button>
      </form>
    </Modal>
  );
}

function HistoryModal({ product, onClose }) {
  const movements = useLoader(() => api(`/api/inventory/${product.id}/movements`), [product.id]);
  return (
    <Modal title={`Stock history: ${product.name}`} onClose={onClose} wide>
      {movements.loading ? <Spinner /> : (
        <table className="table">
          <thead><tr><th>When</th><th>Type</th><th>Change</th><th>Reference</th><th>Note</th><th>By</th></tr></thead>
          <tbody>
            {(movements.data || []).map((m) => (
              <tr key={m.id}>
                <td className="nowrap">{formatDate(m.createdAt)}</td>
                <td><Badge value={m.type} tone="blue" /></td>
                <td className={m.quantityChange < 0 ? "neg" : "pos"}>{m.quantityChange > 0 ? `+${m.quantityChange}` : m.quantityChange}</td>
                <td className="mono small">{m.reference || "-"}</td>
                <td>{m.note}</td>
                <td>{m.performedBy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Modal>
  );
}
