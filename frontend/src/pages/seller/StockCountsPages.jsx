import { useMemo, useState } from "react";
import { CircleAlert, ClipboardCheck, Eye, EyeOff, Printer, Save, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import {
  Button,
  Confirm,
  DataTable,
  DownloadButton,
  Empty,
  ErrorState,
  ExportMenu,
  Field,
  Input,
  Loader,
  Meter,
  Modal,
  Notice,
  Pagination,
  Panel,
  SearchInput,
  Select,
  Stat,
  StatusBadge,
} from "../../components/ui.jsx";
import { formatDate, formatMoney, formatNumber, plural } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import { PageHead, Toolbar, defaultWarehouseId, useCategories, useFilters, useWarehouses, warehouseOptions } from "./common.jsx";
import { useSeller } from "./SellerLayout.jsx";

function StartCountDialog({ onClose, onStarted }) {
  const toast = useToast();
  const warehouses = useWarehouses();
  const categories = useCategories();
  const [warehouseId, setWarehouseId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const chosen = warehouseId || defaultWarehouseId(warehouses);
  return (
    <Modal
      open
      onClose={onClose}
      title="Start a stock count"
      sub="The expected quantities are frozen now. Count the shelves, enter what you find, then complete the count to correct the stock."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={busy}
            disabled={!chosen}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                const count = await api("/api/seller/stock-counts", {
                  method: "POST",
                  body: { warehouseId: Number(chosen), categoryId: categoryId ? Number(categoryId) : null, note: note.trim() || null },
                });
                toast(`Count ${count.number} started with ${plural(count.lines, "product")}`);
                onStarted(count);
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            Start counting
          </Button>
        </>
      }
    >
      <div className="stack">
        <div className="form-grid">
          <Field label="Warehouse">
            <Select value={chosen} onChange={(e) => setWarehouseId(e.target.value)} options={warehouseOptions(warehouses)} />
          </Field>
          <Field label="Department (optional)" hint="Count one department at a time in a big warehouse">
            <Select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              placeholder="Everything"
              options={categories.map((c) => ({ value: String(c.id), label: c.name }))}
            />
          </Field>
          <Field label="Note (optional)" className="span-2">
            <Input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} placeholder="For example: year-end count" />
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

export function StockCountsPage({ route, navigate }) {
  const warehouses = useWarehouses();
  const [f, set] = useFilters(route, navigate);
  const page = Number(f.page || 0);
  const counts = useLoader(
    () => api(`/api/seller/stock-counts${qs({ status: f.status, warehouseId: f.warehouseId, page, size: 20 })}`),
    [f.status, f.warehouseId, page],
  );
  const [starting, setStarting] = useState(false);
  const data = counts.data;
  return (
    <>
      <PageHead
        title="Stock counts"
        sub="Physical counts (stock takes) of a warehouse. Differences are posted to the stock ledger when you complete a count."
        actions={
          <Button variant="primary" icon={ClipboardCheck} onClick={() => setStarting(true)}>
            Start a count
          </Button>
        }
      />
      <Panel flush>
        <Toolbar>
          <Select
            className="select-sm"
            value={f.status || ""}
            placeholder="Any status"
            options={[
              { value: "IN_PROGRESS", label: "In progress" },
              { value: "COMPLETED", label: "Completed" },
              { value: "CANCELLED", label: "Cancelled" },
            ]}
            onChange={(e) => set({ status: e.target.value })}
            aria-label="Status"
          />
          <Select
            className="select-sm"
            value={f.warehouseId || ""}
            placeholder="All warehouses"
            options={warehouseOptions(warehouses, { activeOnly: false })}
            onChange={(e) => set({ warehouseId: e.target.value })}
            aria-label="Warehouse"
          />
        </Toolbar>
        {counts.loading && !data ? (
          <Loader text="Loading counts" />
        ) : counts.error && !data ? (
          <ErrorState message={counts.error} onRetry={counts.reload} />
        ) : (
          <>
            <DataTable
              rows={data.content}
              onRowClick={(c) => navigate(`/seller/stock-counts/${c.id}`)}
              empty={
                <Empty icon={ClipboardCheck} title="No stock counts yet" action={<Button onClick={() => setStarting(true)}>Start your first count</Button>}>
                  Count regularly to catch damage, loss and mistakes before customers do.
                </Empty>
              }
              columns={[
                {
                  key: "number",
                  header: "Count",
                  render: (c) => (
                    <div>
                      <div className="cell-title">{c.number}</div>
                      {c.note && <div className="cell-sub">{c.note}</div>}
                    </div>
                  ),
                },
                { key: "wh", header: "Warehouse", render: (c) => `${c.warehouseCode} - ${c.warehouseName}` },
                { key: "cat", header: "Department", render: (c) => c.categoryName || <span className="muted">Everything</span> },
                { key: "status", header: "Status", render: (c) => <StatusBadge status={c.status} /> },
                {
                  key: "progress",
                  header: "Counted",
                  render: (c) => (
                    <div style={{ minWidth: 120 }}>
                      <div className="tiny muted">
                        {formatNumber(c.countedLines)} of {formatNumber(c.lines)}
                      </div>
                      <Meter percent={c.lines ? (c.countedLines / c.lines) * 100 : 0} />
                    </div>
                  ),
                },
                { key: "diff", header: "Differences", align: "right", render: (c) => formatNumber(c.linesWithDifference) },
                {
                  key: "value",
                  header: "Variance value",
                  align: "right",
                  render: (c) => (
                    <span className={Number(c.varianceValue) < 0 ? "variance-neg" : Number(c.varianceValue) > 0 ? "variance-pos" : ""}>
                      {formatMoney(c.varianceValue)}
                    </span>
                  ),
                },
                {
                  key: "when",
                  header: "Started",
                  render: (c) => (
                    <div>
                      <div className="nowrap">{formatDate(c.createdAt)}</div>
                      <div className="cell-sub">{c.createdBy}</div>
                    </div>
                  ),
                },
              ]}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </>
        )}
      </Panel>
      {starting && <StartCountDialog onClose={() => setStarting(false)} onStarted={(count) => navigate(`/seller/stock-counts/${count.id}`)} />}
    </>
  );
}

export function StockCountPage({ id }) {
  const toast = useToast();
  const { reloadCounts } = useSeller();
  const count = useLoader(() => api(`/api/seller/stock-counts/${id}`), [id]);
  const [entered, setEntered] = useState({});
  const [showExpected, setShowExpected] = useState(false);
  const [search, setSearch] = useState("");
  const [only, setOnly] = useState("");
  const [saving, setSaving] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const [busy, setBusy] = useState(false);

  const c = count.data;
  const lines = useMemo(() => {
    if (!c) return [];
    const term = search.trim().toLowerCase();
    return c.items.filter((line) => {
      if (term && !`${line.sku} ${line.productName} ${line.binLocation || ""}`.toLowerCase().includes(term)) return false;
      const counted = entered[line.id] !== undefined ? entered[line.id] !== "" : line.countedQuantity !== null;
      if (only === "uncounted") return !counted;
      if (only === "differences") return line.countedQuantity !== null && line.variance !== 0;
      return true;
    });
  }, [c, search, only, entered]);

  if (count.loading && !c) return <Loader text="Loading the count" />;
  if (count.error && !c) return <ErrorState message={count.error} onRetry={count.reload} />;

  const open = c.status === "IN_PROGRESS";
  const dirty = Object.entries(entered).filter(([lineId, value]) => {
    const line = c.items.find((l) => String(l.id) === lineId);
    return value !== "" && Number(value) !== line?.countedQuantity;
  });
  const reveal = !open || showExpected;

  const saveCounts = async () => {
    setSaving(true);
    try {
      const updated = await api(`/api/seller/stock-counts/${id}/lines`, {
        method: "PUT",
        body: { lines: dirty.map(([lineId, value]) => ({ lineId: Number(lineId), countedQuantity: Number(value) })) },
      });
      count.setData(updated);
      setEntered({});
      toast(`${plural(dirty.length, "count")} saved`);
      return updated;
    } catch (e) {
      toast(e.message, "bad");
      return null;
    } finally {
      setSaving(false);
    }
  };

  const finish = async (action) => {
    setBusy(true);
    try {
      if (action === "complete" && dirty.length && !(await saveCounts())) return;
      const updated = await api(`/api/seller/stock-counts/${id}/${action}`, { method: "POST" });
      count.setData(updated);
      toast(action === "complete" ? `Count ${updated.number} completed; stock corrected` : `Count ${updated.number} cancelled`);
      reloadCounts();
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(false);
      setConfirm(null);
    }
  };

  return (
    <>
      <PageHead
        title={`Count ${c.number}`}
        sub={`${c.warehouseCode} - ${c.warehouseName}${c.categoryName ? `, ${c.categoryName}` : ""}. Started ${formatDate(c.createdAt)} by ${c.createdBy}.`}
        back={{ to: "/seller/stock-counts", label: "Stock counts" }}
        actions={
          <>
            <DownloadButton path={`/api/seller/stock-counts/${id}/sheet`} icon={Printer}>
              Count sheet
            </DownloadButton>
            <ExportMenu path={`/api/seller/stock-counts/${id}/export/{format}`} label="Variance report" />
            {open && (
              <>
                <Button variant="ghost" icon={XCircle} onClick={() => setConfirm("cancel")}>
                  Cancel count
                </Button>
                <Button icon={Save} loading={saving} disabled={dirty.length === 0} onClick={saveCounts}>
                  Save {dirty.length ? plural(dirty.length, "count") : "counts"}
                </Button>
                <Button variant="primary" icon={ClipboardCheck} onClick={() => setConfirm("complete")}>
                  Complete
                </Button>
              </>
            )}
          </>
        }
      />

      <div className="stack">
        <div className="stats">
          <Stat label="Status" value={<StatusBadge status={c.status} />} hint={c.completedAt ? `${formatDate(c.completedAt)} by ${c.completedBy}` : null} />
          <Stat
            label="Counted"
            value={`${formatNumber(c.countedLines)} / ${formatNumber(c.lines)}`}
            hint={`${Math.round(c.lines ? (c.countedLines / c.lines) * 100 : 0)}% of products`}
          />
          <Stat
            label="Differences"
            value={formatNumber(c.linesWithDifference)}
            hint={`${formatNumber(c.varianceUnits)} units in total`}
            attention={c.linesWithDifference > 0}
          />
          <Stat label="Variance value" value={formatMoney(c.varianceValue)} hint="At unit cost; negative is a loss" />
        </div>

        <Panel flush>
          <Toolbar
            right={
              open && (
                <Button size="sm" variant="ghost" icon={showExpected ? EyeOff : Eye} onClick={() => setShowExpected((v) => !v)}>
                  {showExpected ? "Hide expected quantities" : "Show expected quantities"}
                </Button>
              )
            }
          >
            <SearchInput value={search} onChange={setSearch} placeholder="SKU, product or bin" />
            <Select
              className="select-sm"
              value={only}
              onChange={(e) => setOnly(e.target.value)}
              aria-label="Show"
              options={[
                { value: "", label: "All products" },
                { value: "uncounted", label: "Not counted yet" },
                { value: "differences", label: "With a difference" },
              ]}
            />
          </Toolbar>
          {open && !showExpected && (
            <div style={{ padding: "12px 14px 0" }}>
              <Notice tone="info" icon={EyeOff}>
                Blind count: expected quantities are hidden so the count is not influenced. Enter exactly what is on the shelf.
              </Notice>
            </div>
          )}
          <DataTable
            rows={lines}
            empty={<Empty title="No product matches" />}
            columns={[
              {
                key: "product",
                header: "Product",
                render: (l) => (
                  <div>
                    <div className="cell-title">{l.productName}</div>
                    <div className="cell-sub">{l.sku}</div>
                  </div>
                ),
              },
              { key: "bin", header: "Bin", render: (l) => l.binLocation || <span className="muted">-</span> },
              ...(reveal ? [{ key: "expected", header: "Expected", align: "right", render: (l) => formatNumber(l.expectedQuantity) }] : []),
              {
                key: "counted",
                header: "Counted",
                align: "right",
                render: (l) =>
                  open ? (
                    <Input
                      className="input-sm count-input"
                      type="number"
                      min="0"
                      inputMode="numeric"
                      aria-label={`Counted quantity of ${l.productName}`}
                      value={entered[l.id] ?? l.countedQuantity ?? ""}
                      onChange={(e) => setEntered({ ...entered, [l.id]: e.target.value })}
                    />
                  ) : l.countedQuantity === null ? (
                    <span className="muted">Not counted</span>
                  ) : (
                    formatNumber(l.countedQuantity)
                  ),
              },
              ...(reveal
                ? [
                    {
                      key: "variance",
                      header: "Difference",
                      align: "right",
                      render: (l) =>
                        l.countedQuantity === null ? (
                          <span className="muted">-</span>
                        ) : (
                          <span className={l.variance < 0 ? "variance-neg" : l.variance > 0 ? "variance-pos" : "muted"}>
                            {l.variance > 0 ? "+" : ""}
                            {formatNumber(l.variance)}
                          </span>
                        ),
                    },
                    {
                      key: "value",
                      header: "Value",
                      align: "right",
                      render: (l) => (l.countedQuantity === null || !l.variance ? "-" : formatMoney(l.varianceValue)),
                    },
                  ]
                : []),
            ]}
          />
        </Panel>
      </div>

      <Confirm
        open={confirm === "complete"}
        title={`Complete count ${c.number}?`}
        confirmLabel="Complete and correct stock"
        busy={busy}
        onConfirm={() => finish("complete")}
        onClose={() => setConfirm(null)}
      >
        <p>
          Differences are posted to the stock ledger and the stock of {c.warehouseCode} is corrected. Products you did not count keep their current quantity.
        </p>
        {c.lines - c.countedLines - dirty.length > 0 && (
          <Notice tone="warn" icon={CircleAlert}>
            {plural(c.lines - c.countedLines - dirty.length, "product")} not counted yet.
          </Notice>
        )}
        {dirty.length > 0 && <p className="small">Your {plural(dirty.length, "unsaved count")} will be saved first.</p>}
      </Confirm>
      <Confirm
        open={confirm === "cancel"}
        title={`Cancel count ${c.number}?`}
        confirmLabel="Cancel the count"
        danger
        busy={busy}
        onConfirm={() => finish("cancel")}
        onClose={() => setConfirm(null)}
      >
        Nothing is posted to the stock ledger. You can start a new count at any time.
      </Confirm>
    </>
  );
}
