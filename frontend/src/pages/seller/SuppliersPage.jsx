import { useState } from "react";
import { CircleAlert, ClipboardList, Factory, MoreHorizontal, Pencil, Plus, Power } from "lucide-react";
import { api, qs } from "../../api.js";
import {
  Badge,
  Button,
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
  Textarea,
} from "../../components/ui.jsx";
import { formatMoney, formatNumber, formatPercent } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import { fieldErrorsOf } from "../account/ProfilePage.jsx";
import { DebouncedSearch, PageHead, Toolbar, invalidate, useFilters } from "./common.jsx";

const EMPTY = {
  name: "",
  contactName: "",
  email: "",
  phone: "",
  address: "",
  city: "",
  country: "United Arab Emirates",
  leadTimeDays: "7",
  paymentTermsDays: "30",
  notes: "",
};

export function SupplierDialog({ supplier, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState(
    supplier ? Object.fromEntries(Object.keys(EMPTY).map((k) => [k, supplier[k] === null || supplier[k] === undefined ? "" : String(supplier[k])])) : EMPTY,
  );
  const [errors, setErrors] = useState({});
  const [problem, setProblem] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const save = async () => {
    const found = {};
    if (!form.name.trim()) found.name = "Enter the supplier's name";
    if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) found.email = "Enter a valid email address";
    if (form.phone.trim() && !/^[+0-9 ()-]{7,30}$/.test(form.phone.trim())) found.phone = "Enter a valid phone number";
    ["leadTimeDays", "paymentTermsDays"].forEach((key) => {
      const n = Number(form[key]);
      if (form[key] !== "" && !(Number.isInteger(n) && n >= 0 && n <= 365)) found[key] = "0 to 365 days";
    });
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    setProblem(null);
    const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, typeof v === "string" ? v.trim() || null : v]));
    body.leadTimeDays = form.leadTimeDays === "" ? null : Number(form.leadTimeDays);
    body.paymentTermsDays = form.paymentTermsDays === "" ? null : Number(form.paymentTermsDays);
    try {
      const saved = supplier
        ? await api(`/api/seller/suppliers/${supplier.id}`, { method: "PUT", body })
        : await api("/api/seller/suppliers", { method: "POST", body });
      invalidate("suppliers");
      toast(supplier ? "Supplier updated" : `${saved.name} added`);
      onSaved(saved);
    } catch (e) {
      setErrors(fieldErrorsOf(e));
      setProblem(e.fieldErrors ? "Some fields need your attention." : e.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      title={supplier ? `Edit ${supplier.name}` : "Add a supplier"}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} onClick={save}>
            {supplier ? "Save" : "Add supplier"}
          </Button>
        </>
      }
    >
      <div className="stack">
        <div className="form-grid">
          <Field label="Company name" className="span-2" error={errors.name}>
            <Input value={form.name} onChange={set("name")} maxLength={120} />
          </Field>
          <Field label="Contact person" error={errors.contactName}>
            <Input value={form.contactName} onChange={set("contactName")} maxLength={120} />
          </Field>
          <Field label="Email" error={errors.email} hint="Purchase orders are addressed to this email">
            <Input type="email" value={form.email} onChange={set("email")} maxLength={120} />
          </Field>
          <Field label="Phone" error={errors.phone}>
            <Input value={form.phone} onChange={set("phone")} maxLength={30} />
          </Field>
          <Field label="City" error={errors.city}>
            <Input value={form.city} onChange={set("city")} maxLength={60} />
          </Field>
          <Field label="Address" error={errors.address}>
            <Input value={form.address} onChange={set("address")} maxLength={255} />
          </Field>
          <Field label="Country" error={errors.country}>
            <Input value={form.country} onChange={set("country")} maxLength={60} />
          </Field>
          <Field label="Lead time (days)" error={errors.leadTimeDays} hint="From ordering to delivery; used for reorder suggestions">
            <Input type="number" min="0" max="365" value={form.leadTimeDays} onChange={set("leadTimeDays")} />
          </Field>
          <Field label="Payment terms (days)" error={errors.paymentTermsDays} hint="For example 30 for net 30">
            <Input type="number" min="0" max="365" value={form.paymentTermsDays} onChange={set("paymentTermsDays")} />
          </Field>
          <Field label="Notes" className="span-2" error={errors.notes}>
            <Textarea value={form.notes} onChange={set("notes")} maxLength={500} rows={3} />
          </Field>
        </div>
        {problem && (
          <Notice tone="bad" icon={CircleAlert}>
            {problem}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

export function SuppliersPage({ route, navigate }) {
  const toast = useToast();
  const [f, set] = useFilters(route, navigate, { sort: "name" });
  const page = Number(f.page || 0);
  const query = { search: f.search, active: f.active, sort: f.sort };
  const suppliers = useLoader(() => api(`/api/seller/suppliers${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const [editing, setEditing] = useState(null);
  const data = suppliers.data;

  const toggle = async (supplier) => {
    try {
      await api(`/api/seller/suppliers/${supplier.id}/active${qs({ active: !supplier.active })}`, { method: "PATCH" });
      invalidate("suppliers");
      toast(supplier.active ? `${supplier.name} deactivated` : `${supplier.name} reactivated`);
      suppliers.reload();
    } catch (e) {
      toast(e.message, "bad");
    }
  };

  return (
    <>
      <PageHead
        title="Suppliers"
        sub="Who you buy from, how fast they deliver and how much you spend with them."
        actions={
          <>
            <ExportMenu path="/api/seller/suppliers/export/{format}" />
            <Button variant="primary" icon={Plus} onClick={() => setEditing({})}>
              Add supplier
            </Button>
          </>
        }
      />
      <Panel flush>
        <Toolbar>
          <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="Name, contact, email or city" />
          <Select
            className="select-sm"
            value={f.active || ""}
            placeholder="Active and inactive"
            options={[
              { value: "true", label: "Active" },
              { value: "false", label: "Inactive" },
            ]}
            onChange={(e) => set({ active: e.target.value })}
            aria-label="Status"
          />
          <Select
            className="select-sm"
            value={f.sort}
            options={[
              { value: "name", label: "Name (A to Z)" },
              { value: "newest", label: "Newest first" },
              { value: "leadTime", label: "Fastest delivery" },
            ]}
            onChange={(e) => set({ sort: e.target.value })}
            aria-label="Sort"
          />
        </Toolbar>
        {suppliers.loading && !data ? (
          <Loader text="Loading suppliers" />
        ) : suppliers.error && !data ? (
          <ErrorState message={suppliers.error} onRetry={suppliers.reload} />
        ) : (
          <>
            <DataTable
              rows={data.content}
              onRowClick={setEditing}
              empty={
                <Empty icon={Factory} title="No suppliers yet" action={<Button onClick={() => setEditing({})}>Add supplier</Button>}>
                  Add the companies you buy from to raise purchase orders and get reorder suggestions.
                </Empty>
              }
              columns={[
                {
                  key: "name",
                  header: "Supplier",
                  render: (s) => (
                    <div>
                      <div className="cell-title">
                        {s.name} {!s.active && <Badge>Inactive</Badge>}
                      </div>
                      <div className="cell-sub">{[s.contactName, s.email, s.phone].filter(Boolean).join(" - ") || "No contact details"}</div>
                    </div>
                  ),
                },
                { key: "city", header: "City", render: (s) => s.city || <span className="muted">-</span> },
                { key: "lead", header: "Lead time", align: "right", render: (s) => `${formatNumber(s.leadTimeDays)} days` },
                { key: "terms", header: "Terms", align: "right", render: (s) => (s.paymentTermsDays ? `Net ${s.paymentTermsDays}` : "Prepaid") },
                { key: "products", header: "Products", align: "right", render: (s) => formatNumber(s.productsSupplied) },
                {
                  key: "orders",
                  header: "Purchase orders",
                  align: "right",
                  render: (s) => (
                    <>
                      {formatNumber(s.purchaseOrders)}
                      {s.openPurchaseOrders > 0 && <div className="cell-sub">{formatNumber(s.openPurchaseOrders)} open</div>}
                    </>
                  ),
                },
                { key: "spend", header: "Ordered", align: "right", render: (s) => formatMoney(s.orderedAmount) },
                {
                  key: "ontime",
                  header: "On time",
                  align: "right",
                  render: (s) =>
                    s.onTimeDeliveryPercent === null || s.onTimeDeliveryPercent === undefined ? (
                      <span className="muted">-</span>
                    ) : (
                      <span className={s.onTimeDeliveryPercent < 80 ? "warn strong" : "good strong"}>{formatPercent(s.onTimeDeliveryPercent, 0)}</span>
                    ),
                },
                {
                  key: "actions",
                  header: "",
                  width: 48,
                  render: (s) => (
                    <span onClick={(e) => e.stopPropagation()}>
                      <Menu trigger={({ toggle: open }) => <IconButton icon={MoreHorizontal} label={`Actions for ${s.name}`} onClick={open} />}>
                        <button type="button" className="menu-item" onClick={() => setEditing(s)}>
                          <Pencil /> Edit
                        </button>
                        <a className="menu-item" href={`#/seller/purchase-orders/new?supplier=${s.id}`}>
                          <ClipboardList /> New purchase order
                        </a>
                        <a className="menu-item" href={`#/seller/purchase-orders?supplierId=${s.id}`}>
                          <ClipboardList /> Purchase orders
                        </a>
                        <div className="menu-sep" />
                        <button type="button" className="menu-item" onClick={() => toggle(s)}>
                          <Power /> {s.active ? "Deactivate" : "Reactivate"}
                        </button>
                      </Menu>
                    </span>
                  ),
                },
              ]}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </>
        )}
      </Panel>
      {editing && (
        <SupplierDialog
          supplier={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            suppliers.reload();
          }}
        />
      )}
    </>
  );
}
