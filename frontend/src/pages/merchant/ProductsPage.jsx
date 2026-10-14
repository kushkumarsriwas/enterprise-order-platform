import { useEffect, useState } from "react";
import { Archive, Eye, Package, PackagePlus, Pencil, Search } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatMoney, formatNumber, formatPercent } from "../../format.js";
import { useDebounced, useLoader } from "../../hooks.jsx";
import { Alert, Badge, Empty, Field, Modal, PageHeader, ProductArt, Segmented, Spinner, Switch } from "../../components/ui.jsx";

const BLANK = { sku: "", name: "", description: "", categoryId: "", costPrice: "", price: "", initialStock: "0", reorderLevel: "5", supplier: "", imageUrl: "", listed: true };

/** Product catalogue of the store: cost, price, margin and whether each product is listed on the marketplace. */
export default function MerchantProducts({ route, navigate, toast }) {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const debounced = useDebounced(search);
  const products = useLoader(
    () => api(`/api/merchant/products${qs({ search: debounced, listed: filter === "all" ? "" : String(filter === "listed") })}`),
    [debounced, filter]
  );
  const categories = useLoader(() => api("/api/categories", { auth: false }), []);
  const [editing, setEditing] = useState(route.query.new ? { ...BLANK } : null);

  useEffect(() => {
    if (route.query.new) navigate("/merchant/products", { replace: true, keepScroll: true });
  }, [route.query.new, navigate]);

  const toggleListing = async (product, listed) => {
    try {
      await api(`/api/merchant/products/${product.id}/listing`, { method: "PATCH", body: { listed } });
      toast.show(listed ? `${product.name} is now on the marketplace` : `${product.name} is now inventory only`);
      products.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };
  const archive = async (product) => {
    try {
      await api(`/api/merchant/products/${product.id}`, { method: "DELETE" });
      toast.show(`${product.name} archived`);
      products.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };

  const list = products.data || [];
  const listedCount = list.filter((p) => p.listed).length;

  return (
    <div className="page">
      <PageHeader
        title="Products"
        subtitle="Everything you stock. Listed products are sold on the marketplace; unlisted ones are tracked as inventory only."
        actions={
          <button className="btn btn-primary" onClick={() => setEditing({ ...BLANK })}>
            <PackagePlus size={16} /> Add product
          </button>
        }
      />
      <div className="toolbar">
        <div className="search">
          <Search size={16} />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search by name or SKU" aria-label="Search products" />
        </div>
        <Segmented
          ariaLabel="Listing filter"
          value={filter}
          onChange={setFilter}
          options={[
            { id: "all", label: "All" },
            { id: "listed", label: "On marketplace" },
            { id: "unlisted", label: "Inventory only" },
          ]}
        />
      </div>
      <Alert>{products.error}</Alert>
      {products.loading && !products.data ? (
        <Spinner />
      ) : list.length === 0 ? (
        <Empty icon={Package} title={search || filter !== "all" ? "No products match" : "No products yet"} action={!search && filter === "all" && <button className="btn btn-primary" onClick={() => setEditing({ ...BLANK })}>Add your first product</button>}>
          {search || filter !== "all" ? "Try another search or filter." : "Add the products you sell or keep in stock, with their cost and selling price."}
        </Empty>
      ) : (
        <div className={`card flush ${products.loading ? "refetching" : ""}`}>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Cost</th>
                  <th className="num">Price</th>
                  <th className="num">Margin</th>
                  <th className="num">Available</th>
                  <th className="num">Reserved</th>
                  <th>On marketplace</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <div className="row" style={{ gap: 12 }}>
                        <ProductArt product={p} size="sm" />
                        <div style={{ minWidth: 0 }}>
                          <strong>{p.name}</strong>
                          <span className="sub">
                            {p.sku} - {p.categoryName || "No category"}
                          </span>
                        </div>
                      </div>
                    </td>
                    <td className="num">{formatMoney(p.costPrice)}</td>
                    <td className="num">{formatMoney(p.price)}</td>
                    <td className={`num ${Number(p.marginPercent) < 0 ? "neg" : ""}`}>{formatPercent(p.marginPercent)}</td>
                    <td className="num">
                      {formatNumber(p.availableQuantity)} {p.availableQuantity === 0 ? <Badge value="OUT_OF_STOCK" text="Out" /> : p.lowStock ? <Badge value="LOW_STOCK" text="Low" /> : null}
                    </td>
                    <td className="num">{formatNumber(p.reservedQuantity)}</td>
                    <td>
                      <Switch checked={p.listed} onChange={(listed) => toggleListing(p, listed)} label={p.listed ? "Listed" : "Hidden"} />
                    </td>
                    <td className="nowrap right">
                      {p.listed && (
                        <a className="icon-btn" href={`#/product/${p.id}`} aria-label={`View ${p.name} on the marketplace`} title="View on the marketplace">
                          <Eye size={16} />
                        </a>
                      )}
                      <button className="icon-btn" onClick={() => setEditing({ ...p, categoryId: p.categoryId || "", imageUrl: p.imageUrl || "", description: p.description || "" })} aria-label={`Edit ${p.name}`} title="Edit">
                        <Pencil size={16} />
                      </button>
                      <button className="icon-btn danger" onClick={() => setEditing({ ...p, archive: true })} aria-label={`Archive ${p.name}`} title="Archive">
                        <Archive size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {list.length > 0 && (
        <p className="muted small">
          {list.length} product{list.length === 1 ? "" : "s"}, {listedCount} on the marketplace. Stock levels are managed under Inventory.
        </p>
      )}

      {editing && !editing.archive && (
        <ProductForm
          initial={editing}
          categories={categories.data || []}
          onCategoryCreated={categories.reload}
          onClose={() => setEditing(null)}
          onSaved={(saved, created) => {
            setEditing(null);
            toast.show(created ? `${saved.name} added` : `${saved.name} saved`);
            products.reload();
          }}
        />
      )}
      {editing?.archive && (
        <Modal title={`Archive ${editing.name}?`} onClose={() => setEditing(null)}>
          <div className="stack">
            <p>The product is removed from the marketplace and from your active catalogue. Past orders and reports keep it.</p>
            <div className="row end">
              <button className="btn btn-ghost" onClick={() => setEditing(null)}>
                Keep product
              </button>
              <button
                className="btn btn-danger"
                onClick={() => {
                  archive(editing);
                  setEditing(null);
                }}
              >
                Archive product
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

function ProductForm({ initial, categories, onCategoryCreated, onClose, onSaved }) {
  const creating = !initial.id;
  const [form, setForm] = useState({ ...initial, costPrice: String(initial.costPrice ?? ""), price: String(initial.price ?? "") });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [newCategory, setNewCategory] = useState(null);
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const cost = Number(form.costPrice);
  const price = Number(form.price);
  const margin = price > 0 && form.costPrice !== "" ? ((price - cost) / price) * 100 : null;

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    const common = {
      name: form.name.trim(),
      description: form.description?.trim() || null,
      price: form.price,
      costPrice: form.costPrice,
      categoryId: form.categoryId || null,
      imageUrl: form.imageUrl?.trim() || null,
      listed: form.listed,
    };
    try {
      const saved = creating
        ? await api("/api/merchant/products", {
            method: "POST",
            body: { ...common, sku: form.sku.trim(), initialStock: Number(form.initialStock || 0), reorderLevel: Number(form.reorderLevel || 0), supplier: form.supplier?.trim() || null },
          })
        : await api(`/api/merchant/products/${initial.id}`, { method: "PUT", body: common });
      onSaved(saved, creating);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
      setBusy(false);
    }
  };

  const createCategory = async () => {
    try {
      const category = await api("/api/categories", { method: "POST", body: { name: newCategory.trim() } });
      onCategoryCreated();
      setForm({ ...form, categoryId: category.id });
      setNewCategory(null);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <Modal title={creating ? "Add a product" : `Edit ${initial.name}`} onClose={onClose} wide>
      <form className="stack" onSubmit={save}>
        <div className="form-grid">
          <Field label="Product name" error={fieldErrors.name} className="span-2">
            <input value={form.name} onChange={set("name")} maxLength={150} required />
          </Field>
          {creating && (
            <Field label="SKU (your product code)" error={fieldErrors.sku} hint="Letters, digits and '-', unique in your store">
              <input value={form.sku} onChange={set("sku")} maxLength={40} required />
            </Field>
          )}
          <Field label="Category" error={fieldErrors.categoryId}>
            {newCategory === null ? (
              <select
                value={form.categoryId}
                onChange={(e) => (e.target.value === "__new" ? setNewCategory("") : setForm({ ...form, categoryId: e.target.value }))}
              >
                <option value="">No category</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
                <option value="__new">+ New category...</option>
              </select>
            ) : (
              <div className="row">
                <input value={newCategory} onChange={(e) => setNewCategory(e.target.value)} placeholder="Category name" maxLength={80} autoFocus />
                <button type="button" className="btn btn-sm" onClick={createCategory} disabled={!newCategory.trim()}>
                  Add
                </button>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setNewCategory(null)}>
                  Cancel
                </button>
              </div>
            )}
          </Field>
          <Field label="Cost price (AED)" error={fieldErrors.costPrice} hint="What one unit costs you">
            <input type="number" min="0" step="0.01" value={form.costPrice} onChange={set("costPrice")} required />
          </Field>
          <Field
            label="Selling price (AED)"
            error={fieldErrors.price}
            hint={margin === null ? "What customers pay" : `Margin ${formatPercent(margin)} - profit ${formatMoney(price - cost)} per unit`}
          >
            <input type="number" min="0.01" step="0.01" value={form.price} onChange={set("price")} required />
          </Field>
          {creating && (
            <>
              <Field label="Opening stock (units)" error={fieldErrors.initialStock} hint="Recorded as a stock purchase at the cost price">
                <input type="number" min="0" value={form.initialStock} onChange={set("initialStock")} required />
              </Field>
              <Field label="Reorder level" error={fieldErrors.reorderLevel} hint="You get an alert when stock falls to this level">
                <input type="number" min="0" value={form.reorderLevel} onChange={set("reorderLevel")} />
              </Field>
              <Field label="Supplier" error={fieldErrors.supplier} hint="Optional">
                <input value={form.supplier} onChange={set("supplier")} maxLength={100} />
              </Field>
            </>
          )}
          <Field label="Image URL" error={fieldErrors.imageUrl} hint="Optional link to a product photo" className={creating ? "" : "span-2"}>
            <input value={form.imageUrl} onChange={set("imageUrl")} maxLength={500} placeholder="https://..." />
          </Field>
          <Field label="Description" error={fieldErrors.description} className="span-2">
            <textarea rows={3} value={form.description} onChange={set("description")} maxLength={1000} />
          </Field>
        </div>
        <Switch checked={form.listed} onChange={(listed) => setForm({ ...form, listed })} label="List on the marketplace (customers can buy it)" />
        {margin !== null && margin < 0 && <Alert kind="warning">The selling price is below the cost price: every sale makes a loss.</Alert>}
        <Alert onClose={() => setError("")}>{error}</Alert>
        <div className="row end">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Saving..." : creating ? "Add product" : "Save changes"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
