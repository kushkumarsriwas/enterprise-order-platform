import { useMemo, useState } from "react";
import { CircleAlert, ClipboardList, FileText, PackageCheck, Pencil, Plus, Repeat, Send, Trash2, Truck, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import {
  Badge,
  Button,
  Checkbox,
  Confirm,
  DataTable,
  DownloadButton,
  Drawer,
  Empty,
  ErrorState,
  ExportMenu,
  Field,
  IconButton,
  Input,
  Loader,
  Modal,
  MoneyInput,
  Notice,
  Pagination,
  Panel,
  Select,
  Stat,
  StatusBadge,
  Tabs,
  Textarea,
} from "../../components/ui.jsx";
import { formatDate, formatDay, formatMoney, formatNumber, isoDate, plural, today } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import {
  ActiveFilters,
  DebouncedSearch,
  PageHead,
  PeriodPicker,
  ProductPicker,
  Toolbar,
  defaultWarehouseId,
  useFilters,
  useSuppliers,
  useWarehouses,
  warehouseOptions,
} from "./common.jsx";
import { useSeller } from "./SellerLayout.jsx";

const PO_TABS = [
  { id: "", label: "All" },
  { id: "DRAFT", label: "Drafts" },
  { id: "ORDERED", label: "Ordered" },
  { id: "PARTIALLY_RECEIVED", label: "Partly received" },
  { id: "RECEIVED", label: "Received" },
  { id: "CLOSED", label: "Closed" },
  { id: "CANCELLED", label: "Cancelled" },
];
const PO_SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "expected", label: "Expected delivery" },
  { value: "amount", label: "Largest amount" },
];
const addDays = (days) => isoDate(new Date(Date.now() + days * 86400000));

// ---------------------------------------------------------------- purchase orders list

export function PurchaseOrdersPage({ route, navigate }) {
  const suppliers = useSuppliers();
  const warehouses = useWarehouses();
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const page = Number(f.page || 0);
  const query = {
    status: f.status,
    supplierId: f.supplierId,
    warehouseId: f.warehouseId,
    search: f.search,
    from: f.from,
    to: f.to,
    overdue: f.overdue === "true" ? true : undefined,
    sort: f.sort,
  };
  const orders = useLoader(() => api(`/api/seller/purchase-orders${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const data = orders.data;
  return (
    <>
      <PageHead
        title="Purchase orders"
        sub="What you ordered from suppliers, what arrived and what is still outstanding."
        actions={
          <>
            <ExportMenu path="/api/seller/purchase-orders/export/{format}" query={qs(query)} />
            <Button icon={Repeat} onClick={() => navigate("/seller/replenishment")}>
              Reorder suggestions
            </Button>
            <Button variant="primary" icon={Plus} onClick={() => navigate("/seller/purchase-orders/new")}>
              New purchase order
            </Button>
          </>
        }
      />
      <Tabs tabs={PO_TABS} value={f.status || ""} onChange={(status) => set({ status })} />
      <Panel flush className="tabbed-panel">
        <Toolbar>
          <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="PO number, supplier or SKU" />
          <Select
            className="select-sm"
            value={f.supplierId || ""}
            placeholder="All suppliers"
            options={suppliers.map((s) => ({ value: String(s.id), label: s.name }))}
            onChange={(e) => set({ supplierId: e.target.value })}
            aria-label="Supplier"
          />
          <Select
            className="select-sm"
            value={f.warehouseId || ""}
            placeholder="All warehouses"
            options={warehouseOptions(warehouses, { activeOnly: false })}
            onChange={(e) => set({ warehouseId: e.target.value })}
            aria-label="Warehouse"
          />
          <PeriodPicker allowEmpty label="Order date" from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
          <Checkbox checked={f.overdue === "true"} onChange={(v) => set({ overdue: v ? "true" : "" })}>
            Overdue only
          </Checkbox>
          <Select className="select-sm" value={f.sort} options={PO_SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />
        </Toolbar>
        <ActiveFilters
          onClear={() => clear(["sort", "status"])}
          chips={[
            { key: "search", label: `"${f.search}"`, value: f.search, onRemove: () => set({ search: "" }) },
            {
              key: "supplier",
              label: suppliers.find((s) => String(s.id) === f.supplierId)?.name || "Supplier",
              value: f.supplierId,
              onRemove: () => set({ supplierId: "" }),
            },
            {
              key: "wh",
              label: warehouses.find((w) => String(w.id) === f.warehouseId)?.code || "Warehouse",
              value: f.warehouseId,
              onRemove: () => set({ warehouseId: "" }),
            },
            { key: "dates", label: `Ordered ${f.from || "start"} to ${f.to || "today"}`, value: f.from || f.to, onRemove: () => set({ from: "", to: "" }) },
            { key: "overdue", label: "Overdue", value: f.overdue === "true", onRemove: () => set({ overdue: "" }) },
          ]}
        />
        {orders.loading && !data ? (
          <Loader text="Loading purchase orders" />
        ) : orders.error && !data ? (
          <ErrorState message={orders.error} onRetry={orders.reload} />
        ) : (
          <div style={{ opacity: orders.loading ? 0.6 : 1 }}>
            <DataTable
              rows={data.content}
              onRowClick={(po) => navigate(`/seller/purchase-orders/${po.id}`)}
              empty={
                <Empty
                  icon={ClipboardList}
                  title="No purchase orders"
                  action={<Button onClick={() => navigate("/seller/purchase-orders/new")}>New purchase order</Button>}
                >
                  Order stock from your suppliers; receiving the delivery adds it to your warehouse.
                </Empty>
              }
              columns={[
                { key: "number", header: "PO", render: (po) => <span className="cell-title">{po.number}</span> },
                { key: "supplier", header: "Supplier", render: (po) => po.supplierName },
                { key: "wh", header: "Deliver to", render: (po) => po.warehouseCode },
                { key: "status", header: "Status", render: (po) => <StatusBadge status={po.status} /> },
                { key: "date", header: "Ordered", render: (po) => (po.orderDate ? formatDay(po.orderDate) : <span className="muted">Not sent</span>) },
                {
                  key: "expected",
                  header: "Expected",
                  render: (po) => (
                    <span className="row" style={{ gap: 6 }}>
                      {po.expectedDate ? formatDay(po.expectedDate) : <span className="muted">-</span>}
                      {po.overdue && <Badge tone="red">Overdue</Badge>}
                    </span>
                  ),
                },
                {
                  key: "units",
                  header: "Units",
                  align: "right",
                  render: (po) => (
                    <>
                      {formatNumber(po.totalUnits)}
                      {po.outstandingUnits > 0 && po.status !== "DRAFT" && <div className="cell-sub">{formatNumber(po.outstandingUnits)} to come</div>}
                    </>
                  ),
                },
                { key: "amount", header: "Amount", align: "right", render: (po) => <span className="strong">{formatMoney(po.totalAmount)}</span> },
              ]}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
    </>
  );
}

// ---------------------------------------------------------------- create / edit a draft

export function PurchaseOrderEditor({ id, route, navigate }) {
  const existing = useLoader(() => (id ? api(`/api/seller/purchase-orders/${id}`) : Promise.resolve(null)), [id]);
  if (id && existing.loading && !existing.data) return <Loader text="Loading the purchase order" />;
  if (id && existing.error && !existing.data) return <ErrorState message={existing.error} onRetry={existing.reload} />;
  if (existing.data && existing.data.status !== "DRAFT") {
    return (
      <Notice tone="info" icon={CircleAlert}>
        Purchase order {existing.data.number} has been sent and can no longer be edited. <a href={`#/seller/purchase-orders/${id}`}>Open it</a>
      </Notice>
    );
  }
  return <PurchaseOrderForm id={id} po={existing.data} supplierId={route.query.supplier} navigate={navigate} />;
}

function PurchaseOrderForm({ id, po, supplierId, navigate }) {
  const toast = useToast();
  const suppliers = useSuppliers();
  const warehouses = useWarehouses();
  const [form, setForm] = useState(() =>
    po
      ? { supplierId: String(po.supplierId), warehouseId: String(po.warehouseId), expectedDate: po.expectedDate || "", notes: po.notes || "" }
      : { supplierId: supplierId || "", warehouseId: "", expectedDate: "", notes: "" },
  );
  const [lines, setLines] = useState(() =>
    po
      ? po.lines.map((l) => ({ productId: l.productId, name: l.productName, sku: l.sku, quantity: String(l.quantityOrdered), unitCost: String(l.unitCost) }))
      : [],
  );
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);
  const supplier = suppliers.find((s) => String(s.id) === form.supplierId);
  const warehouseId = form.warehouseId || defaultWarehouseId(warehouses);
  // until the user picks a date, expect the delivery after the supplier's lead time
  const expectedDate = form.expectedDate || (supplier ? addDays(supplier.leadTimeDays || 7) : "");

  const total = lines.reduce((sum, l) => sum + (Number(l.quantity) || 0) * (Number(l.unitCost) || 0), 0);
  const units = lines.reduce((sum, l) => sum + (Number(l.quantity) || 0), 0);
  const valid =
    form.supplierId &&
    lines.length > 0 &&
    lines.every((l) => Number.isInteger(Number(l.quantity)) && Number(l.quantity) > 0 && l.unitCost !== "" && Number(l.unitCost) >= 0);
  const update = (productId, changes) => setLines(lines.map((l) => (l.productId === productId ? { ...l, ...changes } : l)));

  const save = async (andSend) => {
    setBusy(andSend ? "send" : "save");
    setError(null);
    const body = {
      supplierId: Number(form.supplierId),
      warehouseId: warehouseId ? Number(warehouseId) : null,
      expectedDate: expectedDate || null,
      notes: form.notes.trim() || null,
      lines: lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity), unitCost: Number(l.unitCost) })),
    };
    try {
      let po = id
        ? await api(`/api/seller/purchase-orders/${id}`, { method: "PUT", body })
        : await api("/api/seller/purchase-orders", { method: "POST", body });
      if (andSend) po = await api(`/api/seller/purchase-orders/${po.id}/submit`, { method: "POST" });
      toast(andSend ? `${po.number} sent to ${po.supplierName}` : `Draft ${po.number} saved`);
      navigate(`/seller/purchase-orders/${po.id}`, { replace: true });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <PageHead
        title={id ? `Edit ${po?.number}` : "New purchase order"}
        sub="Drafts can be changed until you send them to the supplier."
        back={{ to: "/seller/purchase-orders", label: "Purchase orders" }}
        actions={
          <>
            <Button loading={busy === "save"} disabled={!valid || !!busy} onClick={() => save(false)}>
              Save draft
            </Button>
            <Button variant="primary" icon={Send} loading={busy === "send"} disabled={!valid || !!busy} onClick={() => save(true)}>
              Save and send
            </Button>
          </>
        }
      />
      <div className="sc-grid-main">
        <Panel title="Products">
          <div className="stack">
            <ProductPicker
              exclude={lines.map((l) => l.productId)}
              onSelect={(p) =>
                setLines([...lines, { productId: p.id, name: p.name, sku: p.sku, quantity: String(p.reorderQuantity || 1), unitCost: String(p.costPrice) }])
              }
            />
            {lines.length === 0 ? (
              <p className="small muted" style={{ margin: 0 }}>
                Search for the products you want to order. The unit cost starts at each product's current cost.
              </p>
            ) : (
              <div className="line-editor">
                <div className="line-row head">
                  <span>Product</span>
                  <span className="num">Quantity</span>
                  <span className="num">Unit cost</span>
                  <span className="num">Total</span>
                  <span />
                </div>
                {lines.map((l) => (
                  <div key={l.productId} className="line-row">
                    <span>
                      <span className="cell-title" style={{ display: "block" }}>
                        {l.name}
                      </span>
                      <span className="cell-sub">{l.sku}</span>
                    </span>
                    <Input
                      className="input-sm num"
                      type="number"
                      min="1"
                      value={l.quantity}
                      onChange={(e) => update(l.productId, { quantity: e.target.value })}
                      aria-label={`Quantity of ${l.name}`}
                    />
                    <MoneyInput value={l.unitCost} onChange={(v) => update(l.productId, { unitCost: v })} aria-label={`Unit cost of ${l.name}`} />
                    <span className="num strong">{formatMoney((Number(l.quantity) || 0) * (Number(l.unitCost) || 0))}</span>
                    <IconButton icon={Trash2} label={`Remove ${l.name}`} onClick={() => setLines(lines.filter((x) => x.productId !== l.productId))} />
                  </div>
                ))}
                <div className="line-row" style={{ borderTop: "1px solid var(--line)", paddingTop: 10 }}>
                  <span className="strong">Total</span>
                  <span className="num strong">{formatNumber(units)}</span>
                  <span />
                  <span className="num strong">{formatMoney(total)}</span>
                  <span />
                </div>
              </div>
            )}
          </div>
        </Panel>
        <Panel title="Order details">
          <div className="stack">
            <Field label="Supplier">
              <Select
                value={form.supplierId}
                onChange={(e) => setForm({ ...form, supplierId: e.target.value })}
                placeholder="Choose a supplier"
                options={suppliers.filter((s) => s.active).map((s) => ({ value: String(s.id), label: s.name }))}
              />
            </Field>
            {suppliers.length === 0 && (
              <Notice tone="info" icon={CircleAlert}>
                Add a supplier first. <a href="#/seller/suppliers">Go to suppliers</a>
              </Notice>
            )}
            <Field label="Deliver to">
              <Select value={warehouseId} onChange={(e) => setForm({ ...form, warehouseId: e.target.value })} options={warehouseOptions(warehouses)} />
            </Field>
            <Field label="Expected delivery" hint="Starts from the supplier's lead time; orders past this date show as overdue">
              <Input type="date" value={expectedDate} min={today()} onChange={(e) => setForm({ ...form, expectedDate: e.target.value })} />
            </Field>
            <Field label="Notes for the supplier (optional)">
              <Textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} maxLength={500} rows={3} />
            </Field>
            {error && (
              <Notice tone="bad" icon={CircleAlert}>
                {error}
              </Notice>
            )}
          </div>
        </Panel>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- one purchase order

function ReceiveDialog({ po, onClose, onDone }) {
  const toast = useToast();
  const open = po.lines.filter((l) => l.quantityOutstanding > 0);
  const [quantities, setQuantities] = useState(Object.fromEntries(open.map((l) => [l.id, String(l.quantityOutstanding)])));
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const chosen = open.filter((l) => Number(quantities[l.id]) > 0);
  const over = open.filter((l) => Number(quantities[l.id]) > l.quantityOutstanding);
  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      title={`Receive delivery for ${po.number}`}
      sub={`Into ${po.warehouseCode} - ${po.warehouseName}. Change the quantities if the supplier delivered only part of the order.`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            icon={PackageCheck}
            loading={busy}
            disabled={chosen.length === 0 || over.length > 0}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                const receipt = await api(`/api/seller/purchase-orders/${po.id}/receive`, {
                  method: "POST",
                  body: {
                    lines: chosen.map((l) => ({ lineId: l.id, quantity: Number(quantities[l.id]) })),
                    supplierReference: reference.trim() || null,
                    note: note.trim() || null,
                  },
                });
                toast(`Goods receipt ${receipt.number}: ${plural(receipt.totalUnits, "unit")} added to stock`);
                onDone();
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            Receive{" "}
            {plural(
              chosen.reduce((s, l) => s + Number(quantities[l.id]), 0),
              "unit",
            )}
          </Button>
        </>
      }
    >
      <div className="stack">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Product</th>
                <th className="num">Outstanding</th>
                <th className="num">Received now</th>
              </tr>
            </thead>
            <tbody>
              {open.map((l) => (
                <tr key={l.id}>
                  <td>
                    <div className="cell-title">{l.productName}</div>
                    <div className="cell-sub">{l.sku}</div>
                  </td>
                  <td className="num">{formatNumber(l.quantityOutstanding)}</td>
                  <td className="num">
                    <Input
                      className="input-sm count-input"
                      type="number"
                      min="0"
                      max={l.quantityOutstanding}
                      value={quantities[l.id]}
                      aria-invalid={Number(quantities[l.id]) > l.quantityOutstanding}
                      aria-label={`Units of ${l.productName} received`}
                      onChange={(e) => setQuantities({ ...quantities, [l.id]: e.target.value })}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="form-grid">
          <Field label="Supplier's delivery note number (optional)">
            <Input value={reference} onChange={(e) => setReference(e.target.value)} maxLength={60} />
          </Field>
          <Field label="Note (optional)">
            <Input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} />
          </Field>
        </div>
        {over.length > 0 && (
          <Notice tone="warn" icon={CircleAlert}>
            You can't receive more than is outstanding. Create a new purchase order for extra units.
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

export function PurchaseOrderPage({ id, navigate }) {
  const toast = useToast();
  const { reloadCounts } = useSeller();
  const po = useLoader(() => api(`/api/seller/purchase-orders/${id}`), [id]);
  const [receiving, setReceiving] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const [busy, setBusy] = useState(null);

  if (po.loading && !po.data) return <Loader text="Loading the purchase order" />;
  if (po.error && !po.data) return <ErrorState message={po.error} onRetry={po.reload} />;
  const o = po.data;
  const received = o.lines.reduce((s, l) => s + l.quantityReceived, 0);

  const act = async (action, message) => {
    setBusy(action);
    try {
      po.setData(await api(`/api/seller/purchase-orders/${id}/${action}`, { method: "POST" }));
      toast(message);
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(null);
      setConfirm(null);
    }
  };

  return (
    <>
      <PageHead
        title={`Purchase order ${o.number}`}
        sub={`${o.supplierName}, delivering to ${o.warehouseCode} - ${o.warehouseName}. Created ${formatDate(o.createdAt)} by ${o.createdBy}.`}
        back={{ to: "/seller/purchase-orders", label: "Purchase orders" }}
        actions={
          <>
            <DownloadButton path={`/api/seller/purchase-orders/${id}/document`} icon={FileText}>
              PDF for the supplier
            </DownloadButton>
            {o.status === "DRAFT" && (
              <>
                <Button variant="ghost" icon={XCircle} onClick={() => setConfirm("cancel")}>
                  Cancel
                </Button>
                <Button icon={Pencil} onClick={() => navigate(`/seller/purchase-orders/${id}/edit`)}>
                  Edit
                </Button>
                <Button variant="primary" icon={Send} loading={busy === "submit"} onClick={() => act("submit", `${o.number} sent to ${o.supplierName}`)}>
                  Send to supplier
                </Button>
              </>
            )}
            {o.status === "ORDERED" && (
              <Button variant="ghost" icon={XCircle} onClick={() => setConfirm("cancel")}>
                Cancel
              </Button>
            )}
            {o.status === "PARTIALLY_RECEIVED" && (
              <Button variant="ghost" onClick={() => setConfirm("close")}>
                Close the rest
              </Button>
            )}
            {(o.status === "ORDERED" || o.status === "PARTIALLY_RECEIVED") && (
              <Button variant="primary" icon={Truck} onClick={() => setReceiving(true)}>
                Receive delivery
              </Button>
            )}
          </>
        }
      />
      <div className="stack">
        <div className="stats">
          <Stat
            label="Status"
            value={<StatusBadge status={o.status} />}
            hint={o.overdue ? "Past the expected date" : o.receivedAt ? `Completed ${formatDate(o.receivedAt)}` : null}
            attention={o.overdue}
          />
          <Stat label="Amount" value={formatMoney(o.totalAmount)} hint={`${formatNumber(o.totalUnits)} units`} />
          <Stat
            label="Received"
            value={`${formatNumber(received)} / ${formatNumber(o.totalUnits)}`}
            hint={o.outstandingUnits > 0 ? `${formatNumber(o.outstandingUnits)} outstanding` : "Nothing outstanding"}
          />
          <Stat
            label="Expected"
            value={o.expectedDate ? formatDay(o.expectedDate) : "-"}
            hint={o.orderDate ? `Ordered ${formatDay(o.orderDate)}` : "Not sent yet"}
          />
        </div>
        <div className="sc-grid-main">
          <Panel title="Lines" flush>
            <DataTable
              rows={o.lines}
              columns={[
                {
                  key: "product",
                  header: "Product",
                  render: (l) => (
                    <div>
                      <a className="cell-title" href={`#/seller/products/${l.productId}`}>
                        {l.productName}
                      </a>
                      <div className="cell-sub">{l.sku}</div>
                    </div>
                  ),
                },
                { key: "ordered", header: "Ordered", align: "right", render: (l) => formatNumber(l.quantityOrdered) },
                { key: "received", header: "Received", align: "right", render: (l) => formatNumber(l.quantityReceived) },
                {
                  key: "outstanding",
                  header: "Outstanding",
                  align: "right",
                  render: (l) =>
                    l.quantityOutstanding ? <span className="strong">{formatNumber(l.quantityOutstanding)}</span> : <span className="muted">0</span>,
                },
                { key: "cost", header: "Unit cost", align: "right", render: (l) => formatMoney(l.unitCost) },
                { key: "total", header: "Total", align: "right", render: (l) => formatMoney(l.lineTotal) },
              ]}
              footer={
                <tr>
                  <td>Total</td>
                  <td className="num">{formatNumber(o.totalUnits)}</td>
                  <td className="num">{formatNumber(received)}</td>
                  <td className="num">{formatNumber(o.outstandingUnits)}</td>
                  <td />
                  <td className="num">{formatMoney(o.totalAmount)}</td>
                </tr>
              }
            />
          </Panel>
          <div className="stack">
            <Panel title="Supplier">
              <dl className="dl">
                <dt>Company</dt>
                <dd>{o.supplierName}</dd>
                <dt>Email</dt>
                <dd>{o.supplierEmail ? <a href={`mailto:${o.supplierEmail}`}>{o.supplierEmail}</a> : "-"}</dd>
                {o.notes && (
                  <>
                    <dt>Notes</dt>
                    <dd>{o.notes}</dd>
                  </>
                )}
              </dl>
            </Panel>
            <Panel title="Deliveries">
              {o.receipts.length === 0 ? (
                <p className="small muted" style={{ margin: 0 }}>
                  Nothing received yet.
                </p>
              ) : (
                <ul className="timeline">
                  {o.receipts.map((r) => (
                    <li key={r.id} className="done">
                      <div className="t-title">
                        {r.number}: {plural(r.totalUnits, "unit")}, {formatMoney(r.totalAmount)}
                      </div>
                      <div className="t-meta">
                        {formatDate(r.receivedAt)} by {r.receivedBy}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          </div>
        </div>
      </div>
      {receiving && (
        <ReceiveDialog
          po={o}
          onClose={() => setReceiving(false)}
          onDone={() => {
            setReceiving(false);
            po.reload();
            reloadCounts();
          }}
        />
      )}
      <Confirm
        open={confirm === "cancel"}
        title={`Cancel ${o.number}?`}
        confirmLabel="Cancel the order"
        danger
        busy={busy === "cancel"}
        onConfirm={() => act("cancel", `${o.number} cancelled`)}
        onClose={() => setConfirm(null)}
      >
        Let {o.supplierName} know that you no longer need these products.
      </Confirm>
      <Confirm
        open={confirm === "close"}
        title={`Close ${o.number}?`}
        confirmLabel="Close the order"
        busy={busy === "close"}
        onConfirm={() => act("close", `${o.number} closed`)}
        onClose={() => setConfirm(null)}
      >
        The {plural(o.outstandingUnits, "unit")} still outstanding will not be delivered and no longer count as incoming stock.
      </Confirm>
    </>
  );
}

// ---------------------------------------------------------------- goods receipts

function ReceiveWithoutOrder({ onClose, onDone }) {
  const toast = useToast();
  const warehouses = useWarehouses();
  const suppliers = useSuppliers();
  const [product, setProduct] = useState(null);
  const [form, setForm] = useState({ warehouseId: "", quantity: "", unitCost: "", supplierId: "", reference: "", note: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const warehouseId = form.warehouseId || defaultWarehouseId(warehouses);
  const valid =
    product && warehouseId && Number(form.quantity) > 0 && Number.isInteger(Number(form.quantity)) && form.unitCost !== "" && Number(form.unitCost) >= 0;
  return (
    <Modal
      open
      onClose={onClose}
      title="Receive stock without a purchase order"
      sub="For opening stock, a delivery you did not order through Nexora, or returns from a market stall. The average cost is updated."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            icon={PackageCheck}
            loading={busy}
            disabled={!valid}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                const receipt = await api("/api/seller/goods-receipts", {
                  method: "POST",
                  body: {
                    productId: product.id,
                    warehouseId: Number(warehouseId),
                    quantity: Number(form.quantity),
                    unitCost: Number(form.unitCost),
                    supplierId: form.supplierId ? Number(form.supplierId) : null,
                    reference: form.reference.trim() || null,
                    note: form.note.trim() || null,
                  },
                });
                toast(`Goods receipt ${receipt.number} saved`);
                onDone();
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            Receive
          </Button>
        </>
      }
    >
      <div className="stack">
        {product ? (
          <div className="row-between">
            <div>
              <div className="strong">{product.name}</div>
              <div className="tiny muted">
                {product.sku} - current cost {formatMoney(product.costPrice)}
              </div>
            </div>
            <Button size="sm" variant="ghost" onClick={() => setProduct(null)}>
              Change
            </Button>
          </div>
        ) : (
          <Field label="Product">
            <ProductPicker
              autoFocus
              onSelect={(p) => {
                setProduct(p);
                setForm((f) => ({ ...f, unitCost: String(p.costPrice), supplierId: p.preferredSupplierId ? String(p.preferredSupplierId) : f.supplierId }));
              }}
            />
          </Field>
        )}
        <div className="form-grid">
          <Field label="Into warehouse">
            <Select value={warehouseId} onChange={(e) => setForm({ ...form, warehouseId: e.target.value })} options={warehouseOptions(warehouses)} />
          </Field>
          <Field label="Units">
            <Input type="number" min="1" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} />
          </Field>
          <Field label="Cost per unit">
            <MoneyInput value={form.unitCost} onChange={(v) => setForm({ ...form, unitCost: v })} />
          </Field>
          <Field label="Supplier (optional)">
            <Select
              value={form.supplierId}
              onChange={(e) => setForm({ ...form, supplierId: e.target.value })}
              placeholder="None"
              options={suppliers.map((s) => ({ value: String(s.id), label: s.name }))}
            />
          </Field>
          <Field label="Reference (optional)" hint="Invoice or delivery note number">
            <Input value={form.reference} onChange={(e) => setForm({ ...form, reference: e.target.value })} maxLength={60} />
          </Field>
          <Field label="Note (optional)">
            <Input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} maxLength={255} />
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

export function GoodsReceiptsPage({ route, navigate }) {
  const suppliers = useSuppliers();
  const warehouses = useWarehouses();
  const { reloadCounts } = useSeller();
  const [f, set, clear] = useFilters(route, navigate);
  const page = Number(f.page || 0);
  const query = { search: f.search, supplierId: f.supplierId, warehouseId: f.warehouseId, from: f.from, to: f.to };
  const receipts = useLoader(() => api(`/api/seller/goods-receipts${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const [creating, setCreating] = useState(false);
  const [open, setOpen] = useState(null);
  const data = receipts.data;
  return (
    <>
      <PageHead
        title="Goods receipts"
        sub="Every delivery booked into a warehouse (GRN), with or without a purchase order. Also your purchases report."
        actions={
          <>
            <ExportMenu path="/api/seller/goods-receipts/export/{format}" query={qs(query)} label="Purchases report" />
            <Button variant="primary" icon={PackageCheck} onClick={() => setCreating(true)}>
              Receive stock
            </Button>
          </>
        }
      />
      <Panel flush>
        <Toolbar>
          <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="GRN, supplier or delivery note" />
          <Select
            className="select-sm"
            value={f.supplierId || ""}
            placeholder="All suppliers"
            options={suppliers.map((s) => ({ value: String(s.id), label: s.name }))}
            onChange={(e) => set({ supplierId: e.target.value })}
            aria-label="Supplier"
          />
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
        <ActiveFilters
          onClear={() => clear()}
          chips={[
            { key: "search", label: `"${f.search}"`, value: f.search, onRemove: () => set({ search: "" }) },
            { key: "dates", label: `${f.from || "start"} to ${f.to || "today"}`, value: f.from || f.to, onRemove: () => set({ from: "", to: "" }) },
          ]}
        />
        {receipts.loading && !data ? (
          <Loader text="Loading goods receipts" />
        ) : receipts.error && !data ? (
          <ErrorState message={receipts.error} onRetry={receipts.reload} />
        ) : (
          <>
            <DataTable
              rows={data.content}
              onRowClick={setOpen}
              empty={<Empty icon={PackageCheck} title="No goods received yet" />}
              columns={[
                { key: "number", header: "GRN", render: (r) => <span className="cell-title">{r.number}</span> },
                {
                  key: "po",
                  header: "Purchase order",
                  render: (r) =>
                    r.purchaseOrderId ? (
                      <a href={`#/seller/purchase-orders/${r.purchaseOrderId}`} onClick={(e) => e.stopPropagation()}>
                        {r.purchaseOrderNumber}
                      </a>
                    ) : (
                      <span className="muted">Without PO</span>
                    ),
                },
                { key: "supplier", header: "Supplier", render: (r) => r.supplierName || <span className="muted">-</span> },
                { key: "wh", header: "Warehouse", render: (r) => r.warehouseCode },
                { key: "ref", header: "Delivery note", render: (r) => r.supplierReference || <span className="muted">-</span> },
                { key: "units", header: "Units", align: "right", render: (r) => formatNumber(r.totalUnits) },
                { key: "amount", header: "Amount", align: "right", render: (r) => <span className="strong">{formatMoney(r.totalAmount)}</span> },
                {
                  key: "when",
                  header: "Received",
                  render: (r) => (
                    <div>
                      <div className="nowrap">{formatDate(r.receivedAt)}</div>
                      <div className="cell-sub">{r.receivedBy}</div>
                    </div>
                  ),
                },
              ]}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </>
        )}
      </Panel>
      {creating && (
        <ReceiveWithoutOrder
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            receipts.reload();
            reloadCounts();
          }}
        />
      )}
      <Drawer
        open={!!open}
        title={open ? `Goods receipt ${open.number}` : ""}
        sub={open ? `${open.warehouseCode}, ${formatDate(open.receivedAt)} by ${open.receivedBy}` : ""}
        onClose={() => setOpen(null)}
      >
        {open && (
          <div className="stack">
            <DataTable
              rowKey="productId"
              rows={open.lines}
              columns={[
                { key: "product", header: "Product", render: (l) => <a href={`#/seller/products/${l.productId}`}>{l.productName}</a> },
                { key: "qty", header: "Units", align: "right", render: (l) => formatNumber(l.quantity) },
                { key: "cost", header: "Unit cost", align: "right", render: (l) => formatMoney(l.unitCost) },
                { key: "total", header: "Total", align: "right", render: (l) => formatMoney(l.lineTotal) },
              ]}
            />
            <div className="summary-row total">
              <span>Total</span>
              <span className="num">{formatMoney(open.totalAmount)}</span>
            </div>
            {open.note && <p className="small soft">Note: {open.note}</p>}
          </div>
        )}
      </Drawer>
    </>
  );
}

// ---------------------------------------------------------------- replenishment

export function ReplenishmentPage({ route, navigate }) {
  const toast = useToast();
  const suppliers = useSuppliers();
  const warehouses = useWarehouses();
  const [f, set] = useFilters(route, navigate, { onlyNeeded: "true" });
  const suggestions = useLoader(
    () => api(`/api/seller/replenishment${qs({ supplierId: f.supplierId, onlyNeeded: f.onlyNeeded === "true" })}`),
    [f.supplierId, f.onlyNeeded],
  );
  const [picked, setSelected] = useState(null);
  const [quantities, setQuantities] = useState({});
  const [supplierFor, setSupplierFor] = useState({});
  const [creating, setCreating] = useState(false);
  const [warehouseId, setWarehouseId] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const rows = useMemo(() => suggestions.data || [], [suggestions.data]);
  // everything with a suggested quantity starts selected, until the user changes the selection
  const selected = picked ?? rows.filter((r) => r.suggestedQuantity > 0).map((r) => r.productId);

  const qtyOf = (r) => quantities[r.productId] ?? String(r.suggestedQuantity);
  const supplierOf = (r) => supplierFor[r.productId] ?? (r.supplierId ? String(r.supplierId) : "");
  const chosen = rows.filter((r) => selected.includes(r.productId));
  const missingSupplier = chosen.filter((r) => !supplierOf(r));
  const estimated = chosen.reduce((s, r) => s + (Number(qtyOf(r)) || 0) * Number(r.unitCost || 0), 0);
  const supplierCount = new Set(chosen.map(supplierOf).filter(Boolean)).size;

  const create = async () => {
    setBusy(true);
    try {
      const orders = await api("/api/seller/replenishment/purchase-orders", {
        method: "POST",
        body: {
          items: chosen.map((r) => ({ productId: r.productId, quantity: Number(qtyOf(r)), supplierId: Number(supplierOf(r)) })),
          warehouseId: Number(warehouseId || defaultWarehouseId(warehouses)),
          notes: notes.trim() || null,
        },
      });
      toast(`${plural(orders.length, "draft purchase order")} created`);
      navigate("/seller/purchase-orders?status=DRAFT");
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHead
        title="Reorder suggestions"
        sub="Based on each product's reorder point, recent daily sales, the supplier's lead time and stock already on order."
        actions={<ExportMenu path="/api/seller/replenishment/export/{format}" query={qs({ supplierId: f.supplierId })} label="Reorder report" />}
      />
      <Panel flush>
        <Toolbar
          right={
            <Button variant="primary" size="sm" icon={ClipboardList} disabled={chosen.length === 0} onClick={() => setCreating(true)}>
              Create purchase orders ({formatNumber(chosen.length)})
            </Button>
          }
        >
          <Select
            className="select-sm"
            value={f.supplierId || ""}
            placeholder="All suppliers"
            options={suppliers.map((s) => ({ value: String(s.id), label: s.name }))}
            onChange={(e) => {
              setSelected(null);
              set({ supplierId: e.target.value });
            }}
            aria-label="Supplier"
          />
          <Checkbox
            checked={f.onlyNeeded === "true"}
            onChange={(v) => {
              setSelected(null);
              set({ onlyNeeded: v ? "true" : "false" });
            }}
          >
            Only products that need reordering
          </Checkbox>
        </Toolbar>
        {suggestions.loading && !suggestions.data ? (
          <Loader text="Working out what to reorder" />
        ) : suggestions.error && !suggestions.data ? (
          <ErrorState message={suggestions.error} onRetry={suggestions.reload} />
        ) : (
          <DataTable
            rowKey="productId"
            rows={rows}
            selectable
            selected={selected}
            onSelect={setSelected}
            empty={
              <Empty icon={Repeat} title="Nothing to reorder">
                Every product is above its reorder point, counting stock already on order.
              </Empty>
            }
            columns={[
              {
                key: "product",
                header: "Product",
                render: (r) => (
                  <div style={{ minWidth: 200 }}>
                    <a className="cell-title" href={`#/seller/products/${r.productId}`}>
                      {r.productName}
                    </a>
                    <div className="cell-sub">
                      {r.sku}
                      {r.categoryName ? ` - ${r.categoryName}` : ""}
                    </div>
                  </div>
                ),
              },
              { key: "available", header: "Available", align: "right", render: (r) => formatNumber(r.availableQuantity) },
              {
                key: "incoming",
                header: "On order",
                align: "right",
                render: (r) => (r.incomingQuantity ? formatNumber(r.incomingQuantity) : <span className="muted">-</span>),
              },
              { key: "rop", header: "Reorder at", align: "right", render: (r) => formatNumber(r.reorderPoint) },
              { key: "daily", header: "Sold per day", align: "right", render: (r) => Number(r.averageDailySales || 0).toFixed(1) },
              {
                key: "cover",
                header: "Days left",
                align: "right",
                render: (r) =>
                  r.daysOfCover === null || r.daysOfCover === undefined ? (
                    <span className="muted">-</span>
                  ) : (
                    <span className={r.daysOfCover <= r.leadTimeDays ? "bad strong" : ""}>{formatNumber(r.daysOfCover)}</span>
                  ),
              },
              {
                key: "supplier",
                header: "Supplier",
                render: (r) => (
                  <Select
                    className="select-sm"
                    value={supplierOf(r)}
                    placeholder="Choose"
                    options={suppliers.filter((s) => s.active).map((s) => ({ value: String(s.id), label: s.name }))}
                    onChange={(e) => setSupplierFor({ ...supplierFor, [r.productId]: e.target.value })}
                    aria-label={`Supplier for ${r.productName}`}
                  />
                ),
              },
              {
                key: "qty",
                header: "Order",
                align: "right",
                render: (r) => (
                  <Input
                    className="input-sm count-input"
                    type="number"
                    min="1"
                    value={qtyOf(r)}
                    onChange={(e) => setQuantities({ ...quantities, [r.productId]: e.target.value })}
                    aria-label={`Units of ${r.productName} to order`}
                  />
                ),
              },
              { key: "cost", header: "Est. cost", align: "right", render: (r) => formatMoney((Number(qtyOf(r)) || 0) * Number(r.unitCost || 0)) },
              {
                key: "why",
                header: "Why",
                render: (r) => (
                  <span className="small soft" style={{ display: "block", minWidth: 180 }}>
                    {r.reason}
                  </span>
                ),
              },
            ]}
          />
        )}
      </Panel>
      <Modal
        open={creating}
        onClose={() => setCreating(false)}
        title="Create draft purchase orders"
        sub={`${plural(chosen.length, "product")} from ${plural(supplierCount, "supplier")}: one draft per supplier, estimated ${formatMoney(estimated)}. You can review the drafts before sending them.`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCreating(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={busy} disabled={missingSupplier.length > 0} onClick={create}>
              Create {plural(supplierCount, "draft")}
            </Button>
          </>
        }
      >
        <div className="stack">
          {missingSupplier.length > 0 && (
            <Notice tone="warn" icon={CircleAlert}>
              Choose a supplier for {missingSupplier.map((r) => r.productName).join(", ")}.
            </Notice>
          )}
          <Field label="Deliver to">
            <Select
              value={warehouseId || defaultWarehouseId(warehouses)}
              onChange={(e) => setWarehouseId(e.target.value)}
              options={warehouseOptions(warehouses)}
            />
          </Field>
          <Field label="Notes (optional)">
            <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} rows={2} />
          </Field>
        </div>
      </Modal>
    </>
  );
}
