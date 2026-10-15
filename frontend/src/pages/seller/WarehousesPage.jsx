import { useEffect, useState } from "react";
import { ArrowRight, ArrowRightLeft, CircleAlert, MapPin, MoreHorizontal, Pencil, Plus, Power, Star, Trash2, Warehouse } from "lucide-react";
import { api, qs } from "../../api.js";
import {
  Badge,
  Button,
  DataTable,
  Drawer,
  Empty,
  ErrorState,
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
  Tabs,
  Textarea,
} from "../../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, plural } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import { PageHead, PeriodPicker, ProductPicker, Toolbar, invalidate, useFilters, warehouseOptions } from "./common.jsx";

const CODE = /^[A-Za-z0-9][A-Za-z0-9-]{0,19}$/;

function WarehouseDialog({ warehouse, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState({
    code: warehouse?.code || "",
    name: warehouse?.name || "",
    city: warehouse?.city || "",
    address: warehouse?.address || "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const valid = CODE.test(form.code.trim()) && form.name.trim();
  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const body = { code: form.code.trim().toUpperCase(), name: form.name.trim(), city: form.city.trim() || null, address: form.address.trim() || null };
      const saved = warehouse
        ? await api(`/api/seller/warehouses/${warehouse.id}`, { method: "PUT", body })
        : await api("/api/seller/warehouses", { method: "POST", body });
      invalidate("warehouses");
      toast(warehouse ? "Warehouse updated" : `Warehouse ${saved.code} added`);
      onSaved(saved);
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
      title={warehouse ? `Edit ${warehouse.code}` : "Add a warehouse"}
      sub="A shop, a store room or a fulfilment centre: any place where you keep stock."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} disabled={!valid} onClick={save}>
            {warehouse ? "Save" : "Add warehouse"}
          </Button>
        </>
      }
    >
      <div className="stack">
        <div className="form-grid">
          <Field label="Code" hint="Short, like DXB-1 or SHOP">
            <Input value={form.code} onChange={set("code")} maxLength={20} autoCapitalize="characters" />
          </Field>
          <Field label="Name">
            <Input value={form.name} onChange={set("name")} maxLength={80} />
          </Field>
          <Field label="City">
            <Input value={form.city} onChange={set("city")} maxLength={60} />
          </Field>
          <Field label="Address">
            <Input value={form.address} onChange={set("address")} maxLength={200} />
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

function WarehouseCards({ navigate }) {
  const toast = useToast();
  const warehouses = useLoader(() => api("/api/seller/warehouses"), []);
  const [editing, setEditing] = useState(null);
  const [busy, setBusy] = useState(null);

  const act = async (warehouse, path, message) => {
    setBusy(warehouse.id);
    try {
      await api(path, { method: "PATCH" });
      invalidate("warehouses");
      toast(message);
      warehouses.reload();
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(null);
    }
  };

  if (warehouses.loading && !warehouses.data) return <Loader text="Loading warehouses" />;
  if (warehouses.error && !warehouses.data) return <ErrorState message={warehouses.error} onRetry={warehouses.reload} />;

  return (
    <>
      <div className="row-between" style={{ marginBottom: 14 }}>
        <span className="small muted">
          {plural(warehouses.data.filter((w) => w.active).length, "active warehouse")}. Orders ship from the default warehouse first.
        </span>
        <Button variant="primary" icon={Plus} onClick={() => setEditing({})}>
          Add warehouse
        </Button>
      </div>
      <div className="card-grid">
        {warehouses.data.map((w) => (
          <article key={w.id} className={`info-card ${w.active ? "" : "muted-card"}`}>
            <div className="row-between" style={{ alignItems: "flex-start" }}>
              <div style={{ minWidth: 0 }}>
                <span className="wh-code">{w.code}</span>
                <h3 className="ellipsis">{w.name}</h3>
              </div>
              {w.defaultWarehouse ? (
                <Badge tone="teal" icon={Star}>
                  Default
                </Badge>
              ) : !w.active ? (
                <Badge>Closed</Badge>
              ) : null}
            </div>
            <p className="row" style={{ gap: 6 }}>
              <MapPin size={14} style={{ flex: "none" }} />
              <span className="ellipsis">{[w.address, w.city].filter(Boolean).join(", ") || "No address yet"}</span>
            </p>
            <dl className="wh-figures">
              <div>
                <dt>Products</dt>
                <dd>{formatNumber(w.productLines)}</dd>
              </div>
              <div>
                <dt>Units</dt>
                <dd>{formatNumber(w.units)}</dd>
              </div>
              <div>
                <dt>Value at cost</dt>
                <dd>{formatMoney(w.stockValueAtCost)}</dd>
              </div>
            </dl>
            <div className="foot">
              <Button size="sm" onClick={() => navigate(`/seller/inventory?warehouseId=${w.id}`)}>
                View stock
              </Button>
              {w.active && (
                <Button size="sm" variant="ghost" icon={Pencil} onClick={() => setEditing(w)}>
                  Edit
                </Button>
              )}
              {!w.defaultWarehouse && (
                <Menu trigger={({ toggle }) => <IconButton icon={MoreHorizontal} label={`More for ${w.code}`} onClick={toggle} />}>
                  {w.active && (
                    <button
                      type="button"
                      className="menu-item"
                      disabled={busy === w.id}
                      onClick={() => act(w, `/api/seller/warehouses/${w.id}/default`, `${w.code} is now the default warehouse`)}
                    >
                      <Star /> Make it the default
                    </button>
                  )}
                  <button
                    type="button"
                    className="menu-item"
                    disabled={busy === w.id}
                    onClick={() =>
                      act(w, `/api/seller/warehouses/${w.id}/active${qs({ active: !w.active })}`, w.active ? `${w.code} closed` : `${w.code} reopened`)
                    }
                  >
                    <Power /> {w.active ? "Close the warehouse" : "Reopen the warehouse"}
                  </button>
                </Menu>
              )}
            </div>
          </article>
        ))}
      </div>
      {editing && (
        <WarehouseDialog
          warehouse={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            warehouses.reload();
          }}
        />
      )}
    </>
  );
}

/** Move stock between two warehouses in one document: every line moves, or nothing does. */
function TransferDialog({ warehouses, initialProductId, onClose, onDone }) {
  const toast = useToast();
  const active = warehouses.filter((w) => w.active);
  const [from, setFrom] = useState(String(active.find((w) => w.defaultWarehouse)?.id || active[0]?.id || ""));
  const [to, setTo] = useState(String(active.find((w) => !w.defaultWarehouse)?.id || ""));
  const [lines, setLines] = useState([]);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const addProduct = async (product) => {
    const position = await api(`/api/seller/inventory/${product.id ?? product.productId}`).catch(() => null);
    setLines((current) =>
      current.some((l) => l.productId === (product.id ?? product.productId))
        ? current
        : [
            ...current,
            {
              productId: product.id ?? product.productId,
              name: product.name ?? product.productName,
              sku: product.sku,
              levels: position?.warehouses || [],
              quantity: "",
            },
          ],
    );
  };

  useEffect(() => {
    if (initialProductId)
      api(`/api/seller/products/${initialProductId}`)
        .then((d) => addProduct(d.product))
        .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialProductId]);

  const onHandIn = (line, warehouseId) => line.levels.find((l) => String(l.warehouseId) === String(warehouseId))?.onHand ?? 0;
  const problems = lines.filter((l) => !(Number(l.quantity) > 0) || Number(l.quantity) > onHandIn(l, from));
  const valid = from && to && from !== to && lines.length > 0 && problems.length === 0;

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const transfer = await api("/api/seller/transfers", {
        method: "POST",
        body: {
          fromWarehouseId: Number(from),
          toWarehouseId: Number(to),
          lines: lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity) })),
          note: note.trim() || null,
        },
      });
      invalidate("warehouses");
      toast(`Transfer ${transfer.number}: ${plural(transfer.totalUnits, "unit")} moved`);
      onDone(transfer);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      title="Transfer stock"
      sub="Moves units from one warehouse to another. Available stock does not change, only where it is."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" icon={ArrowRightLeft} loading={busy} disabled={!valid} onClick={save}>
            Transfer{" "}
            {plural(
              lines.reduce((s, l) => s + (Number(l.quantity) || 0), 0),
              "unit",
            )}
          </Button>
        </>
      }
    >
      <div className="stack">
        <div className="form-grid">
          <Field label="From">
            <Select value={from} onChange={(e) => setFrom(e.target.value)} options={warehouseOptions(warehouses)} />
          </Field>
          <Field label="To" error={from && from === to ? "Choose a different warehouse" : null}>
            <Select value={to} onChange={(e) => setTo(e.target.value)} placeholder="Choose" options={warehouseOptions(warehouses)} />
          </Field>
        </div>
        <Field label="Add products">
          <ProductPicker onSelect={addProduct} exclude={lines.map((l) => l.productId)} />
        </Field>
        {lines.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">In {warehouses.find((w) => String(w.id) === from)?.code || "source"}</th>
                  <th className="num">Move</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {lines.map((line) => {
                  const max = onHandIn(line, from);
                  const tooMany = Number(line.quantity) > max;
                  return (
                    <tr key={line.productId}>
                      <td>
                        <div className="cell-title">{line.name}</div>
                        <div className="cell-sub">{line.sku}</div>
                      </td>
                      <td className="num">{formatNumber(max)}</td>
                      <td className="num">
                        <Input
                          className="input-sm count-input"
                          type="number"
                          min="1"
                          max={max}
                          value={line.quantity}
                          aria-invalid={tooMany}
                          aria-label={`Units of ${line.name} to move`}
                          onChange={(e) => setLines(lines.map((l) => (l.productId === line.productId ? { ...l, quantity: e.target.value } : l)))}
                        />
                      </td>
                      <td>
                        <IconButton icon={Trash2} label={`Remove ${line.name}`} onClick={() => setLines(lines.filter((l) => l.productId !== line.productId))} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <Field label="Note (optional)">
          <Textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} rows={2} />
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

function Transfers({ route, navigate }) {
  const warehousesList = useLoader(() => api("/api/seller/warehouses"), []);
  const [f, set] = useFilters(route, navigate, { tab: "warehouses" });
  const page = Number(f.page || 0);
  const query = { warehouseId: f.warehouseId, from: f.from, to: f.to };
  const transfers = useLoader(() => api(`/api/seller/transfers${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const [creating, setCreating] = useState(!!route.query.product);
  const [open, setOpen] = useState(null);
  const warehouses = warehousesList.data || [];
  const data = transfers.data;

  return (
    <Panel flush>
      <Toolbar
        right={
          <Button variant="primary" size="sm" icon={ArrowRightLeft} disabled={warehouses.filter((w) => w.active).length < 2} onClick={() => setCreating(true)}>
            New transfer
          </Button>
        }
      >
        <Select
          className="select-sm"
          value={f.warehouseId || ""}
          placeholder="All warehouses"
          options={warehouseOptions(warehouses, { activeOnly: false })}
          onChange={(e) => set({ warehouseId: e.target.value })}
          aria-label="Warehouse"
        />
        <PeriodPicker allowEmpty from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
      </Toolbar>
      {warehouses.filter((w) => w.active).length < 2 && (
        <div style={{ padding: 14 }}>
          <Notice tone="info" icon={Warehouse}>
            Add a second warehouse to move stock between locations.
          </Notice>
        </div>
      )}
      {transfers.loading && !data ? (
        <Loader text="Loading transfers" />
      ) : transfers.error && !data ? (
        <ErrorState message={transfers.error} onRetry={transfers.reload} />
      ) : (
        <>
          <DataTable
            rows={data.content}
            onRowClick={setOpen}
            empty={<Empty icon={ArrowRightLeft} title="No transfers yet" />}
            columns={[
              { key: "number", header: "Transfer", render: (t) => <span className="cell-title">{t.number}</span> },
              {
                key: "route",
                header: "From and to",
                render: (t) => (
                  <span className="row" style={{ gap: 6 }}>
                    {t.fromWarehouse} <ArrowRight size={14} /> {t.toWarehouse}
                  </span>
                ),
              },
              { key: "lines", header: "Products", align: "right", render: (t) => formatNumber(t.lines.length) },
              { key: "units", header: "Units", align: "right", render: (t) => formatNumber(t.totalUnits) },
              { key: "note", header: "Note", render: (t) => <span className="small soft">{t.note || "-"}</span> },
              { key: "by", header: "By", render: (t) => <span className="small muted">{t.createdBy}</span> },
              { key: "when", header: "When", render: (t) => <span className="nowrap">{formatDate(t.createdAt)}</span> },
            ]}
          />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
        </>
      )}
      {creating && warehouses.length > 0 && (
        <TransferDialog
          warehouses={warehouses}
          initialProductId={route.query.product}
          onClose={() => {
            setCreating(false);
            if (route.query.product) set({ product: "" });
          }}
          onDone={() => {
            setCreating(false);
            if (route.query.product) set({ product: "" });
            transfers.reload();
          }}
        />
      )}
      <Drawer
        open={!!open}
        title={open ? `Transfer ${open.number}` : ""}
        sub={open ? `${open.fromWarehouse} to ${open.toWarehouse}, ${formatDate(open.createdAt)}` : ""}
        onClose={() => setOpen(null)}
      >
        {open && (
          <div className="stack">
            <DataTable
              rowKey="productId"
              rows={open.lines}
              columns={[
                { key: "product", header: "Product", render: (l) => <a href={`#/seller/products/${l.productId}`}>{l.productName}</a> },
                { key: "sku", header: "SKU", render: (l) => l.sku },
                { key: "qty", header: "Units", align: "right", render: (l) => formatNumber(l.quantity) },
              ]}
            />
            {open.note && <p className="small soft">Note: {open.note}</p>}
            <p className="small muted">Recorded by {open.createdBy}.</p>
          </div>
        )}
      </Drawer>
    </Panel>
  );
}

export function WarehousesPage({ route, navigate }) {
  const tab = route.query.tab || "warehouses";
  return (
    <>
      <PageHead title="Warehouses" sub="The places where you keep stock, and every move of stock between them." />
      <Tabs
        value={tab}
        onChange={(id) => navigate(`/seller/warehouses${qs({ tab: id === "warehouses" ? "" : id })}`, { replace: true, keepScroll: true })}
        tabs={[
          { id: "warehouses", label: "Warehouses" },
          { id: "transfers", label: "Transfers" },
        ]}
      />
      <div style={{ marginTop: 14 }}>{tab === "transfers" ? <Transfers route={route} navigate={navigate} /> : <WarehouseCards navigate={navigate} />}</div>
    </>
  );
}
