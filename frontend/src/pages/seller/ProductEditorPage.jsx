import { useState } from "react";
import { Archive, CircleAlert, ImagePlus, Save, Store, Trash2 } from "lucide-react";
import { api, upload } from "../../api.js";
import { ProductImage } from "../../components/brand.jsx";
import {
  Badge,
  Button,
  Confirm,
  ErrorState,
  Field,
  Input,
  Loader,
  Modal,
  MoneyInput,
  Notice,
  Panel,
  Select,
  StatusBadge,
  Switch,
  Textarea,
} from "../../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, formatPercent, label, unitName } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import { fieldErrorsOf } from "../account/ProfilePage.jsx";
import { PageHead, defaultWarehouseId, invalidate, useCategories, useSuppliers, useWarehouses, warehouseOptions } from "./common.jsx";
import { useSeller } from "./SellerLayout.jsx";

const UNITS = ["PIECE", "PACK", "BOX", "CARTON", "PAIR", "SET", "KG", "GRAM", "LITRE", "METRE"];
const SKU = /^[A-Za-z0-9][A-Za-z0-9-]{0,39}$/;
const BARCODE = /^$|^\d{8,14}$/;
const MAX_PHOTO = 2 * 1024 * 1024;

const EMPTY = {
  sku: "",
  barcode: "",
  name: "",
  brand: "",
  description: "",
  unit: "PIECE",
  categoryId: "",
  price: "",
  compareAtPrice: "",
  costPrice: "",
  reorderPoint: "5",
  reorderQuantity: "20",
  preferredSupplierId: "",
  listed: false,
  initialStock: "0",
  warehouseId: "",
};

const fromProduct = (p) => ({
  sku: p.sku,
  barcode: p.barcode || "",
  name: p.name,
  brand: p.brand || "",
  description: p.description || "",
  unit: p.unit || "PIECE",
  categoryId: p.categoryId ? String(p.categoryId) : "",
  price: String(p.price),
  compareAtPrice: p.compareAtPrice ? String(p.compareAtPrice) : "",
  costPrice: String(p.costPrice),
  reorderPoint: String(p.reorderPoint),
  reorderQuantity: String(p.reorderQuantity),
  preferredSupplierId: p.preferredSupplierId ? String(p.preferredSupplierId) : "",
  listed: p.listed,
});

function validate(form, creating) {
  const e = {};
  if (creating && !SKU.test(form.sku.trim())) e.sku = "Letters, digits and dashes, up to 40 characters";
  if (!form.name.trim()) e.name = "Give the product a name";
  if (!BARCODE.test(form.barcode.trim())) e.barcode = "8 to 14 digits (EAN or UPC)";
  const price = Number(form.price);
  if (!form.price || !(price > 0) || price > 1000000) e.price = "Enter a selling price above 0";
  if (form.compareAtPrice && !(Number(form.compareAtPrice) > price)) e.compareAtPrice = "The 'was' price must be higher than the selling price";
  if (form.costPrice === "" || Number(form.costPrice) < 0) e.costPrice = "Enter what one unit costs you (0 if nothing)";
  if (form.listed && !form.categoryId) e.categoryId = "Choose a department before listing the product";
  ["reorderPoint", "reorderQuantity", "initialStock"].forEach((key) => {
    if (form[key] !== undefined && form[key] !== "" && !(Number.isInteger(Number(form[key])) && Number(form[key]) >= 0))
      e[key] = "Use a whole number, 0 or more";
  });
  return e;
}

function NewDepartment({ onClose, onCreated }) {
  const toast = useToast();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  return (
    <Modal
      open
      onClose={onClose}
      title="Add a marketplace department"
      sub="Departments are shared by every store; check the list first so customers find similar products together."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={busy}
            disabled={!name.trim()}
            onClick={async () => {
              setBusy(true);
              try {
                const created = await api("/api/categories", { method: "POST", body: { name: name.trim(), description: description.trim() || null } });
                invalidate("categories");
                toast(`Department ${created.name} added`);
                onCreated(created);
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            Add department
          </Button>
        </>
      }
    >
      <div className="stack">
        <Field label="Name">
          <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={80} />
        </Field>
        <Field label="Description (optional)">
          <Input value={description} onChange={(e) => setDescription(e.target.value)} maxLength={255} />
        </Field>
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

function PhotoPanel({ product, onChange }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const choose = async (file) => {
    if (!file) return;
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
      toast("Use a JPEG, PNG or WebP photo", "bad");
      return;
    }
    if (file.size > MAX_PHOTO) {
      toast("The photo is larger than 2 MB. Please use a smaller one.", "bad");
      return;
    }
    setBusy(true);
    try {
      const form = new FormData();
      form.append("file", file);
      onChange(await upload(`/api/seller/products/${product.id}/image`, form, { method: "PUT" }));
      toast("Photo saved");
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Panel title="Photo" sub="JPEG, PNG or WebP up to 2 MB. Square photos on a plain background look best.">
      <div className="photo-box">
        <ProductImage product={product} iconSize={64} />
        <div className="row row-wrap">
          <label className={`btn btn-secondary btn-sm ${busy ? "is-busy" : ""}`}>
            <ImagePlus />
            {product.imageUrl ? "Replace photo" : "Upload photo"}
            <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={busy} onChange={(e) => choose(e.target.files?.[0])} />
          </label>
          {product.imageUrl && (
            <Button
              size="sm"
              variant="ghost"
              icon={Trash2}
              loading={busy}
              onClick={async () => {
                setBusy(true);
                try {
                  onChange(await api(`/api/seller/products/${product.id}/image`, { method: "DELETE" }));
                  toast("Photo removed");
                } catch (e) {
                  toast(e.message, "bad");
                } finally {
                  setBusy(false);
                }
              }}
            >
              Remove
            </Button>
          )}
        </div>
      </div>
    </Panel>
  );
}

function StockPanel({ detail }) {
  const p = detail.product;
  return (
    <Panel
      title="Stock"
      sub={
        detail.daysOfCover !== null && detail.daysOfCover !== undefined
          ? `About ${formatNumber(detail.daysOfCover)} days of sales left`
          : "No recent sales to estimate cover"
      }
      actions={
        <a className="btn btn-ghost btn-sm" href={`#/seller/inventory?search=${encodeURIComponent(p.sku)}`}>
          Adjust
        </a>
      }
    >
      <div className="stack">
        <div className="mini-stats">
          <span>
            <b>{formatNumber(p.availableQuantity)}</b>available
          </span>
          <span>
            <b>{formatNumber(p.reservedQuantity)}</b>reserved
          </span>
          <span>
            <b>{formatNumber(p.incomingQuantity)}</b>incoming
          </span>
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Warehouse</th>
                <th>Bin</th>
                <th className="num">On hand</th>
              </tr>
            </thead>
            <tbody>
              {detail.warehouses.map((w) => (
                <tr key={w.warehouseId}>
                  <td>
                    <span className="strong">{w.code}</span> <span className="muted small">{w.name}</span>
                  </td>
                  <td className="muted">{w.binLocation || "-"}</td>
                  <td className="num">{formatNumber(w.onHand)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <dl className="kv-list">
          <div>
            <dt>Sold in the last 30 days</dt>
            <dd>{formatNumber(detail.unitsSoldLast30Days)}</dd>
          </div>
          <div>
            <dt>Revenue in the last 30 days</dt>
            <dd>{formatMoney(detail.revenueLast30Days)}</dd>
          </div>
          <div>
            <dt>Average sold per day</dt>
            <dd>{Number(detail.averageDailySales || 0).toFixed(2)}</dd>
          </div>
          <div>
            <dt>Stock value at cost</dt>
            <dd>{formatMoney(p.stockValueAtCost)}</dd>
          </div>
        </dl>
      </div>
    </Panel>
  );
}

export function ProductEditorPage({ id, navigate }) {
  const detail = useLoader(() => (id ? api(`/api/seller/products/${id}`) : Promise.resolve(null)), [id]);
  if (id && detail.loading && !detail.data) return <Loader text="Loading the product" />;
  if (id && detail.error && !detail.data) return <ErrorState message={detail.error} onRetry={detail.reload} />;
  return <ProductForm id={id} detail={detail} navigate={navigate} />;
}

/** The editor itself; its form state starts from the loaded product (the page mounts it once the product is there). */
function ProductForm({ id, detail, navigate }) {
  const toast = useToast();
  const { settings } = useSeller();
  const creating = !id;
  const categories = useCategories();
  const suppliers = useSuppliers();
  const warehouses = useWarehouses();
  const [extraCategories, setExtraCategories] = useState([]);
  const [form, setForm] = useState(() => (detail.data ? fromProduct(detail.data.product) : EMPTY));
  const [errors, setErrors] = useState({});
  const [problem, setProblem] = useState(null);
  const [saving, setSaving] = useState(false);
  const [addingDepartment, setAddingDepartment] = useState(false);
  const [archiving, setArchiving] = useState(false);

  const product = detail.data?.product;
  const set = (key) => (event) => setForm({ ...form, [key]: event?.target ? event.target.value : event });
  const allCategories = [...categories, ...extraCategories.filter((c) => !categories.some((x) => x.id === c.id))];
  const warehouseId = form.warehouseId || defaultWarehouseId(warehouses);
  // reservations are not stock leaving the shelf; the side panel shows physical moves only
  const physicalMoves = (detail.data?.recentMovements || []).filter((m) => !["RESERVED", "RELEASED", "EXPIRED"].includes(m.type));
  const price = Number(form.price) || 0;
  const cost = Number(form.costPrice) || 0;
  const margin = price - cost;
  const marginPercent = price > 0 ? (margin / price) * 100 : 0;

  const save = async (event) => {
    event.preventDefault();
    const found = validate(form, creating);
    setErrors(found);
    setProblem(null);
    if (Object.keys(found).length) {
      setProblem("Some fields need your attention.");
      return;
    }
    const body = {
      barcode: form.barcode.trim() || null,
      name: form.name.trim(),
      brand: form.brand.trim() || null,
      description: form.description.trim() || null,
      unit: form.unit,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      price: Number(form.price),
      compareAtPrice: form.compareAtPrice ? Number(form.compareAtPrice) : null,
      costPrice: Number(form.costPrice),
      reorderPoint: Number(form.reorderPoint || 0),
      reorderQuantity: Number(form.reorderQuantity || 0),
      preferredSupplierId: form.preferredSupplierId ? Number(form.preferredSupplierId) : null,
      listed: form.listed,
      ...(creating
        ? {
            sku: form.sku.trim(),
            initialStock: Number(form.initialStock || 0),
            warehouseId: warehouseId ? Number(warehouseId) : null,
          }
        : { imageUrl: product.imageUrl && !product.imageUrl.startsWith("/api/") ? product.imageUrl : null }),
    };
    setSaving(true);
    try {
      if (creating) {
        const created = await api("/api/seller/products", { method: "POST", body });
        invalidate("brands");
        toast(`${created.name} added${created.listed ? " and listed for sale" : " to your inventory"}`);
        navigate(`/seller/products/${created.id}`, { replace: true });
      } else {
        const updated = await api(`/api/seller/products/${id}`, { method: "PUT", body });
        invalidate("brands");
        toast("Changes saved");
        setForm(fromProduct(updated));
        detail.reload();
      }
    } catch (e) {
      const fields = fieldErrorsOf(e);
      setErrors(fields);
      setProblem(Object.keys(fields).length ? "Some fields need your attention." : e.message);
    } finally {
      setSaving(false);
    }
  };

  const archive = async () => {
    try {
      await api(`/api/seller/products/${id}`, { method: "DELETE" });
      toast(`${product.name} archived`);
      navigate("/seller/products", { replace: true });
    } catch (e) {
      toast(e.message, "bad");
    }
  };

  const hiddenReason =
    product?.listed && !product.visibleToCustomers
      ? product.storeSuspended
        ? "Your store is suspended, so customers cannot see it."
        : settings?.vacationMode
          ? "Vacation mode is on, so customers cannot see it."
          : product.availableQuantity <= 0
            ? "It is out of stock; customers see it as sold out."
            : "Customers cannot see it right now."
      : null;

  return (
    <>
      <form onSubmit={save} noValidate>
        <PageHead
          title={creating ? "Add a product" : product.name}
          sub={creating ? "Add it to your inventory. List it for sale now or later." : `SKU ${product.sku} - updated ${formatDate(product.updatedAt)}`}
          back={{ to: "/seller/products", label: "Products" }}
          actions={
            <>
              {!creating && product.listed && product.visibleToCustomers && (
                <Button icon={Store} onClick={() => navigate(`/product/${product.id}`)}>
                  View on Nexora
                </Button>
              )}
              {!creating && (
                <Button variant="ghost" icon={Archive} onClick={() => setArchiving(true)}>
                  Archive
                </Button>
              )}
              <Button type="submit" variant="primary" icon={Save} loading={saving}>
                {creating ? "Save product" : "Save changes"}
              </Button>
            </>
          }
        />

        <div className="editor-grid">
          <div className="stack">
            <Panel title="Product">
              <div className="form-grid">
                <Field label="Name" className="span-2" error={errors.name}>
                  <Input value={form.name} onChange={set("name")} maxLength={150} autoFocus={creating} />
                </Field>
                <Field label="Brand" error={errors.brand}>
                  <Input value={form.brand} onChange={set("brand")} maxLength={60} />
                </Field>
                <Field label="Sold as" error={errors.unit}>
                  <Select value={form.unit} onChange={set("unit")} options={UNITS.map((u) => ({ value: u, label: unitName(u) }))} />
                </Field>
                <Field
                  label="Marketplace department"
                  className="span-2"
                  error={errors.categoryId}
                  hint={
                    <>
                      Customers browse by department.{" "}
                      <button type="button" className="link-btn" onClick={() => setAddingDepartment(true)}>
                        Department missing?
                      </button>
                    </>
                  }
                >
                  <Select
                    value={form.categoryId}
                    onChange={set("categoryId")}
                    placeholder="No department"
                    options={allCategories.map((c) => ({ value: String(c.id), label: c.name }))}
                  />
                </Field>
                <Field label="Description" className="span-2" hint="What it is, sizes, materials, what is in the box" error={errors.description}>
                  <Textarea value={form.description} onChange={set("description")} maxLength={2000} rows={5} />
                </Field>
              </div>
            </Panel>

            <Panel title="Pricing" sub="Prices include 5% VAT, as customers see them.">
              <div className="stack">
                <div className="form-grid">
                  <Field label="Selling price" error={errors.price}>
                    <MoneyInput value={form.price} onChange={set("price")} />
                  </Field>
                  <Field label="'Was' price (optional)" error={errors.compareAtPrice} hint="Shows a discount when higher than the selling price">
                    <MoneyInput value={form.compareAtPrice} onChange={set("compareAtPrice")} />
                  </Field>
                  <Field label="Your cost per unit" error={errors.costPrice} hint="Used for margins, stock value and profit. Customers never see it.">
                    <MoneyInput value={form.costPrice} onChange={set("costPrice")} />
                  </Field>
                </div>
                {price > 0 && (
                  <div className="margin-hint">
                    <span>
                      Margin <b className={margin < 0 ? "bad" : ""}>{formatMoney(margin)}</b> per unit ({formatPercent(marginPercent)})
                    </span>
                    <span>
                      VAT in the price <b>{formatMoney((price * 5) / 105)}</b>
                    </span>
                    {Number(form.compareAtPrice) > price && (
                      <span>
                        Shown as <b>{Math.round(((Number(form.compareAtPrice) - price) / Number(form.compareAtPrice)) * 100)}% off</b>
                      </span>
                    )}
                  </div>
                )}
              </div>
            </Panel>

            <Panel title="Identifiers and stock planning">
              <div className="form-grid">
                <Field label="SKU" error={errors.sku} hint={creating ? "Your own product code; it cannot be changed later" : "The SKU cannot be changed"}>
                  <Input value={form.sku} onChange={set("sku")} disabled={!creating} maxLength={40} autoCapitalize="characters" spellCheck={false} />
                </Field>
                <Field label="Barcode (optional)" error={errors.barcode}>
                  <Input value={form.barcode} onChange={set("barcode")} inputMode="numeric" maxLength={14} />
                </Field>
                <Field label="Reorder point" error={errors.reorderPoint} hint="Low-stock alert when available stock reaches this">
                  <Input type="number" min="0" value={form.reorderPoint} onChange={set("reorderPoint")} />
                </Field>
                <Field label="Reorder quantity" error={errors.reorderQuantity} hint="How many you usually order from the supplier">
                  <Input type="number" min="0" value={form.reorderQuantity} onChange={set("reorderQuantity")} />
                </Field>
                <Field label="Preferred supplier" className="span-2">
                  <Select
                    value={form.preferredSupplierId}
                    onChange={set("preferredSupplierId")}
                    placeholder="None"
                    options={suppliers
                      .filter((s) => s.active || String(s.id) === form.preferredSupplierId)
                      .map((s) => ({ value: String(s.id), label: s.name }))}
                  />
                </Field>
                {creating && (
                  <>
                    <Field label="Opening stock" error={errors.initialStock} hint="Units you already have; recorded at your cost price">
                      <Input type="number" min="0" value={form.initialStock} onChange={set("initialStock")} />
                    </Field>
                    <Field label="Stored in">
                      <Select value={warehouseId} onChange={set("warehouseId")} options={warehouseOptions(warehouses)} />
                    </Field>
                  </>
                )}
              </div>
            </Panel>

            {problem && (
              <Notice tone="bad" icon={CircleAlert}>
                {problem}
              </Notice>
            )}
          </div>

          <aside className="editor-side">
            <Panel title="Marketplace">
              <div className="stack">
                <Switch checked={form.listed} onChange={(listed) => setForm({ ...form, listed })}>
                  <span>
                    <span className="strong" style={{ display: "block" }}>
                      List for sale on Nexora
                    </span>
                    <span className="tiny muted">Off keeps it as inventory only: you can still stock, buy and count it.</span>
                  </span>
                </Switch>
                {product && (
                  <div className="row row-wrap" style={{ gap: 6 }}>
                    <StatusBadge status={product.stockStatus} />
                    {product.listed ? (
                      product.visibleToCustomers ? (
                        <Badge tone="good" dot>
                          Visible to customers
                        </Badge>
                      ) : (
                        <Badge tone="amber" dot>
                          Hidden
                        </Badge>
                      )
                    ) : (
                      <Badge dot>Inventory only</Badge>
                    )}
                  </div>
                )}
                {hiddenReason && (
                  <p className="small soft" style={{ margin: 0 }}>
                    {hiddenReason}
                  </p>
                )}
                {product && product.ratingCount > 0 && (
                  <p className="small muted" style={{ margin: 0 }}>
                    Rated {Number(product.ratingAverage).toFixed(1)} from {formatNumber(product.ratingCount)} reviews; {formatNumber(product.unitsSold)} sold in
                    total.
                  </p>
                )}
              </div>
            </Panel>

            {product ? (
              <PhotoPanel product={product} onChange={(updated) => detail.setData((d) => ({ ...d, product: updated }))} />
            ) : (
              <Panel title="Photo">
                <p className="small muted" style={{ margin: 0 }}>
                  Save the product first, then upload its photo here.
                </p>
              </Panel>
            )}

            {detail.data && <StockPanel detail={detail.data} />}

            {detail.data?.openPurchaseOrders?.length > 0 && (
              <Panel title="On order">
                <div className="stack-sm">
                  {detail.data.openPurchaseOrders.map((po) => (
                    <a key={po.purchaseOrderId} href={`#/seller/purchase-orders/${po.purchaseOrderId}`} className="row-between small">
                      <span>
                        {po.number} <span className="muted">from {po.supplierName}</span>
                      </span>
                      <span className="strong">{formatNumber(po.quantityOutstanding)}</span>
                    </a>
                  ))}
                </div>
              </Panel>
            )}

            {physicalMoves.length > 0 && (
              <Panel
                title="Recent stock movements"
                actions={
                  <a className="btn btn-ghost btn-sm" href={`#/seller/inventory?tab=ledger&productId=${id}`}>
                    Full ledger
                  </a>
                }
              >
                <div className="stack-sm">
                  {physicalMoves.slice(0, 8).map((m) => (
                    <div key={m.id} className="row-between small">
                      <span>
                        {label(m.type)} <span className="muted">{m.warehouseCode}</span>
                        <div className="tiny muted">{formatDate(m.createdAt)}</div>
                      </span>
                      <span className={`strong num ${m.quantity < 0 ? "bad" : "good"}`}>
                        {m.quantity > 0 ? "+" : ""}
                        {formatNumber(m.quantity)}
                      </span>
                    </div>
                  ))}
                </div>
              </Panel>
            )}
          </aside>
        </div>
      </form>

      {addingDepartment && (
        <NewDepartment
          onClose={() => setAddingDepartment(false)}
          onCreated={(category) => {
            setExtraCategories((list) => [...list, category]);
            setForm((f) => ({ ...f, categoryId: String(category.id) }));
            setAddingDepartment(false);
          }}
        />
      )}
      <Confirm open={archiving} title={`Archive ${product?.name}?`} confirmLabel="Archive" danger onConfirm={archive} onClose={() => setArchiving(false)}>
        It leaves your catalogue and the marketplace. Its order history and stock ledger are kept.
      </Confirm>
    </>
  );
}
