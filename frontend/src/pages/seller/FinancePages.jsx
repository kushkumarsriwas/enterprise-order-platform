import { useState } from "react";
import {
  Banknote,
  CircleAlert,
  Coins,
  Copy,
  HandCoins,
  Landmark,
  Percent,
  PiggyBank,
  Plus,
  Receipt,
  RotateCcw,
  Scale,
  Trash2,
  TrendingDown,
  TrendingUp,
  Wallet,
} from "lucide-react";
import { api, qs } from "../../api.js";
import { HBarList, TrendChart } from "../../components/charts.jsx";
import {
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
  Meter,
  Modal,
  MoneyInput,
  Notice,
  Pagination,
  Panel,
  Select,
  Stat,
  StatusBadge,
} from "../../components/ui.jsx";
import {
  compactMoney,
  currentMonth,
  formatDay,
  formatMoney,
  formatNumber,
  formatPercent,
  label,
  lastMonths,
  longMonth,
  monthLabel,
  shiftMonth,
  shortDay,
  today,
} from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import { METHOD_LABELS } from "./OrdersPages.jsx";
import { ActiveFilters, DebouncedSearch, MultiSelect, PageHead, PeriodPicker, Toolbar, listOf, useFilters } from "./common.jsx";

const MONEY_SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "amount_desc", label: "Largest amount" },
  { value: "amount_asc", label: "Smallest amount" },
];
const PAYMENT_STATUSES = ["SUCCESS", "PENDING", "FAILED", "REFUNDED", "CANCELLED"];

function AmountRange({ f, set, minKey = "minAmount", maxKey = "maxAmount" }) {
  return (
    <div className="range-pair" style={{ width: 210 }}>
      <Input
        className="input-sm"
        key={`min-${f[minKey] || ""}`}
        type="number"
        min="0"
        placeholder="Min AED"
        defaultValue={f[minKey] || ""}
        onBlur={(e) => e.target.value !== (f[minKey] || "") && set({ [minKey]: e.target.value })}
        aria-label="Minimum amount"
      />
      <Input
        className="input-sm"
        key={`max-${f[maxKey] || ""}`}
        type="number"
        min="0"
        placeholder="Max AED"
        defaultValue={f[maxKey] || ""}
        onBlur={(e) => e.target.value !== (f[maxKey] || "") && set({ [maxKey]: e.target.value })}
        aria-label="Maximum amount"
      />
    </div>
  );
}

// ---------------------------------------------------------------- payments

export function PaymentsPage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const page = Number(f.page || 0);
  const period = f.from || f.to ? { from: f.from, to: f.to } : {};
  const query = {
    status: listOf(f.status),
    method: listOf(f.method),
    from: f.from,
    to: f.to,
    minAmount: f.minAmount,
    maxAmount: f.maxAmount,
    q: f.q,
    sort: f.sort,
  };
  const summary = useLoader(() => api(`/api/seller/payments/summary${qs(period)}`), [f.from, f.to]);
  const payments = useLoader(() => api(`/api/seller/payments${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const s = summary.data;
  const data = payments.data;

  return (
    <>
      <PageHead
        title="Payments"
        sub={
          s
            ? `${formatDay(s.from)} to ${formatDay(s.to)}. Nexora keeps a ${formatPercent(s.feeRatePercent)} fee on each sale; the rest is yours.`
            : "Money from your orders, Nexora fees and what you earn."
        }
        actions={
          <>
            <PeriodPicker allowEmpty from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
            <ExportMenu path="/api/seller/payments/export/{format}" query={qs(query)} />
          </>
        }
      />
      {s && (
        <div className="stack" style={{ marginBottom: 16 }}>
          <div className="stats">
            <Stat label="Sales" icon={TrendingUp} value={formatMoney(s.grossSales)} hint={`${formatNumber(s.payments)} payments`} />
            <Stat label="Collected" icon={Landmark} value={formatMoney(s.collected)} hint="paid by card or bank transfer, or cash collected" />
            <Stat
              label="Cash to collect"
              icon={HandCoins}
              value={formatMoney(s.awaitingCollection)}
              hint="cash on delivery not delivered yet"
              attention={Number(s.awaitingCollection) > 0}
            />
            <Stat label="Nexora fees" icon={Percent} value={formatMoney(s.platformFees)} hint={`${formatPercent(s.feeRatePercent)} of sales`} />
            <Stat label="Net earnings" icon={Wallet} value={formatMoney(s.netEarnings)} hint="after fees and refunds" />
          </div>
          <div className="sc-grid-main">
            <Panel title="Money in per day">
              {s.daily?.length ? (
                <TrendChart
                  data={s.daily.map((d) => ({ ...d, label: shortDay(d.date) }))}
                  type="bar"
                  height={220}
                  series={[
                    { key: "amount", label: "Payments" },
                    { key: "platformFees", label: "Nexora fees" },
                  ]}
                />
              ) : (
                <p className="small muted" style={{ padding: "36px 0", textAlign: "center" }}>
                  No payments in this period.
                </p>
              )}
            </Panel>
            <Panel
              title="By method"
              sub={`${formatPercent(s.successRatePercent, 0)} of payment attempts succeeded; ${formatNumber(s.refunds)} refunds (${formatMoney(s.refunded)})`}
            >
              <HBarList
                items={(s.byMethod || []).map((m) => ({
                  key: m.method,
                  label: METHOD_LABELS[m.method] || label(m.method),
                  value: m.amount,
                  hint: `${formatNumber(m.payments)}`,
                }))}
                format={compactMoney}
              />
            </Panel>
          </div>
        </div>
      )}
      <Panel flush>
        <Toolbar>
          <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Order number, reference or customer" />
          <MultiSelect
            label="Status"
            options={PAYMENT_STATUSES.map((v) => ({ value: v, label: label(v === "SUCCESS" ? "SUCCESSFUL" : v) }))}
            value={listOf(f.status)}
            onChange={(list) => set({ status: list.join(",") })}
          />
          <MultiSelect
            label="Method"
            options={Object.entries(METHOD_LABELS).map(([value, text]) => ({ value, label: text }))}
            value={listOf(f.method)}
            onChange={(list) => set({ method: list.join(",") })}
          />
          <AmountRange f={f} set={set} />
          <Select className="select-sm" value={f.sort} options={MONEY_SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />
        </Toolbar>
        <ActiveFilters
          onClear={() => clear(["sort"])}
          chips={[
            { key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) },
            { key: "dates", label: `${f.from || "start"} to ${f.to || "today"}`, value: f.from || f.to, onRemove: () => set({ from: "", to: "" }) },
            {
              key: "amount",
              label: `AED ${f.minAmount || 0} to ${f.maxAmount || "any"}`,
              value: f.minAmount || f.maxAmount,
              onRemove: () => set({ minAmount: "", maxAmount: "" }),
            },
          ]}
        />
        {payments.loading && !data ? (
          <Loader text="Loading payments" />
        ) : payments.error && !data ? (
          <ErrorState message={payments.error} onRetry={payments.reload} />
        ) : (
          <div style={{ opacity: payments.loading ? 0.6 : 1 }}>
            <DataTable
              rows={data.content}
              onRowClick={(p) => navigate(`/seller/orders/${p.orderId}`)}
              empty={<Empty icon={Wallet} title="No payments match" />}
              columns={[
                {
                  key: "ref",
                  header: "Payment",
                  render: (p) => (
                    <div>
                      <div className="cell-title code">{p.transactionReference}</div>
                      <div className="cell-sub">Order {p.orderNumber}</div>
                    </div>
                  ),
                },
                { key: "customer", header: "Customer", render: (p) => p.customerUsername },
                {
                  key: "method",
                  header: "Method",
                  render: (p) => (
                    <div>
                      <div>{METHOD_LABELS[p.method] || p.method}</div>
                      {p.maskedInstrument && <div className="cell-sub">{p.maskedInstrument}</div>}
                    </div>
                  ),
                },
                {
                  key: "status",
                  header: "Status",
                  render: (p) => (
                    <div>
                      <StatusBadge status={p.status}>{p.status === "PENDING" && p.method === "COD" ? "Cash to collect" : undefined}</StatusBadge>
                      {p.failureReason && <div className="cell-sub">{p.failureReason}</div>}
                      {p.refundReason && <div className="cell-sub">{p.refundReason}</div>}
                    </div>
                  ),
                },
                { key: "amount", header: "Amount", align: "right", render: (p) => <span className="strong">{formatMoney(p.amount)}</span> },
                { key: "fee", header: "Fee", align: "right", render: (p) => formatMoney(p.platformFee) },
                { key: "net", header: "You get", align: "right", render: (p) => formatMoney(p.sellerNet) },
                { key: "when", header: "Date", render: (p) => <span className="nowrap">{formatDay(p.createdAt)}</span> },
              ]}
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
    </>
  );
}

// ---------------------------------------------------------------- profit and loss

export function ProfitLossPage({ route, navigate }) {
  const fallback = lastMonths(6);
  const [f, set] = useFilters(route, navigate, { from: fallback.from, to: fallback.to });
  const pl = useLoader(() => api(`/api/seller/finance/profit-loss${qs({ from: f.from, to: f.to })}`), [f.from, f.to]);
  const p = pl.data;
  const profit = p && Number(p.netProfit) >= 0;

  return (
    <>
      <PageHead
        title="Profit and loss"
        sub="Sales minus cost of goods, Nexora fees and your business expenses."
        actions={
          <>
            <PeriodPicker from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
            <ExportMenu path="/api/seller/reports/profit-loss/{format}" query={qs({ from: f.from, to: f.to })} label="P&L report" />
          </>
        }
      />
      {pl.loading && !p ? (
        <Loader text="Preparing your statement" />
      ) : pl.error && !p ? (
        <ErrorState message={pl.error} onRetry={pl.reload} />
      ) : (
        <div className="stack" style={{ opacity: pl.loading ? 0.6 : 1 }}>
          <div className="stats">
            <Stat label="Revenue" icon={TrendingUp} value={formatMoney(p.revenue)} hint={`${formatNumber(p.orders)} orders`} />
            <Stat label="Gross profit" icon={Coins} value={formatMoney(p.grossProfit)} hint={`${formatPercent(p.grossMarginPercent)} gross margin`} />
            <Stat
              label="Fees and expenses"
              icon={Receipt}
              value={formatMoney(Number(p.platformFees) + Number(p.operatingExpenses))}
              hint={`${formatMoney(p.platformFees)} fees, ${formatMoney(p.operatingExpenses)} expenses`}
            />
            <Stat
              label={profit ? "Net profit" : "Net loss"}
              icon={profit ? Scale : TrendingDown}
              value={formatMoney(p.netProfit)}
              hint={`${formatPercent(p.netMarginPercent)} net margin`}
              attention={!profit}
            />
          </div>

          <div className="sc-grid-main">
            <Panel
              title="Statement"
              sub={`${formatDay(p.from)} to ${formatDay(p.to)}`}
              actions={<span className={`result-pill ${profit ? "profit" : "loss"}`}>{profit ? "Profit" : "Loss"}</span>}
            >
              <table className="statement">
                <tbody>
                  <tr>
                    <td>Sales revenue (VAT included)</td>
                    <td className="num">{formatMoney(p.revenue)}</td>
                  </tr>
                  <tr className="indent">
                    <td>Less cost of goods sold</td>
                    <td className="num">-{formatMoney(p.costOfGoodsSold)}</td>
                  </tr>
                  <tr className="sub">
                    <td>Gross profit</td>
                    <td className="num">{formatMoney(p.grossProfit)}</td>
                  </tr>
                  <tr className="indent">
                    <td>Less Nexora fees ({formatPercent(p.feeRatePercent)})</td>
                    <td className="num">-{formatMoney(p.platformFees)}</td>
                  </tr>
                  <tr className="indent">
                    <td>Less operating expenses</td>
                    <td className="num">-{formatMoney(p.operatingExpenses)}</td>
                  </tr>
                  <tr className="grand">
                    <td>{profit ? "Net profit" : "Net loss"}</td>
                    <td className={`num ${profit ? "good" : "bad"}`}>{formatMoney(p.netProfit)}</td>
                  </tr>
                </tbody>
              </table>
            </Panel>
            <Panel
              title="Expenses by category"
              actions={
                <Button size="sm" variant="ghost" onClick={() => navigate("/seller/finance/expenses")}>
                  Expenses
                </Button>
              }
            >
              <HBarList
                items={(p.expensesByCategory || []).map((c) => ({
                  key: c.category,
                  label: c.label,
                  value: c.amount,
                  hint: formatPercent(c.percentOfExpenses, 0),
                }))}
                format={formatMoney}
                color="var(--series-2)"
                empty="No expenses recorded in this period"
              />
            </Panel>
          </div>

          <Panel title="Month by month">
            {p.months?.length ? (
              <TrendChart
                data={p.months.map((m) => ({ ...m, label: monthLabel(m.month) }))}
                type="line"
                height={260}
                series={[
                  { key: "revenue", label: "Revenue" },
                  { key: "grossProfit", label: "Gross profit" },
                  { key: "netProfit", label: "Net profit" },
                ]}
              />
            ) : (
              <p className="small muted">No months in this period.</p>
            )}
          </Panel>

          <Panel flush>
            <DataTable
              rowKey="month"
              rows={p.months || []}
              columns={[
                { key: "month", header: "Month", render: (m) => <span className="strong">{longMonth(m.month)}</span> },
                { key: "orders", header: "Orders", align: "right", render: (m) => formatNumber(m.orders) },
                { key: "revenue", header: "Revenue", align: "right", render: (m) => formatMoney(m.revenue) },
                { key: "cogs", header: "Cost of goods", align: "right", render: (m) => formatMoney(m.costOfGoodsSold) },
                { key: "gross", header: "Gross profit", align: "right", render: (m) => formatMoney(m.grossProfit) },
                { key: "fees", header: "Fees", align: "right", render: (m) => formatMoney(m.platformFees) },
                { key: "opex", header: "Expenses", align: "right", render: (m) => formatMoney(m.operatingExpenses) },
                {
                  key: "net",
                  header: "Net",
                  align: "right",
                  render: (m) => (
                    <span className={`strong ${Number(m.netProfit) < 0 ? "bad" : Number(m.netProfit) > 0 ? "good" : ""}`}>{formatMoney(m.netProfit)}</span>
                  ),
                },
              ]}
            />
          </Panel>
        </div>
      )}
    </>
  );
}

// ---------------------------------------------------------------- expenses

function ExpenseDialog({ expense, categories, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState({
    category: expense?.category || categories[0]?.value || "OTHER",
    amount: expense ? String(expense.amount) : "",
    expenseDate: expense?.expenseDate || today(),
    description: expense?.description || "",
    vendor: expense?.vendor || "",
    reference: expense?.reference || "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const valid = Number(form.amount) > 0 && form.description.trim() && form.expenseDate;
  const save = async () => {
    setBusy(true);
    setError(null);
    const body = {
      ...form,
      amount: Number(form.amount),
      description: form.description.trim(),
      vendor: form.vendor.trim() || null,
      reference: form.reference.trim() || null,
    };
    try {
      const saved = expense
        ? await api(`/api/seller/expenses/${expense.id}`, { method: "PUT", body })
        : await api("/api/seller/expenses", { method: "POST", body });
      toast(expense ? "Expense updated" : `${saved.categoryLabel} expense of ${formatMoney(saved.amount)} recorded`);
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
      title={expense ? "Edit expense" : "Record an expense"}
      sub="Expenses reduce your net profit and count against this month's budget."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} disabled={!valid} onClick={save}>
            {expense ? "Save" : "Record expense"}
          </Button>
        </>
      }
    >
      <div className="stack">
        <div className="form-grid">
          <Field label="Category">
            <Select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} options={categories} />
          </Field>
          <Field label="Amount">
            <MoneyInput value={form.amount} onChange={(v) => setForm({ ...form, amount: v })} />
          </Field>
          <Field label="Date">
            <Input type="date" value={form.expenseDate} max={today()} onChange={(e) => setForm({ ...form, expenseDate: e.target.value })} />
          </Field>
          <Field label="Paid to (optional)">
            <Input value={form.vendor} onChange={(e) => setForm({ ...form, vendor: e.target.value })} maxLength={100} />
          </Field>
          <Field label="Description" className="span-2">
            <Input
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              maxLength={200}
              placeholder="For example: October warehouse rent"
            />
          </Field>
          <Field label="Reference (optional)" className="span-2" hint="Invoice or receipt number">
            <Input value={form.reference} onChange={(e) => setForm({ ...form, reference: e.target.value })} maxLength={60} />
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

function useExpenseCategories() {
  const categories = useLoader(() => api("/api/seller/expenses/categories"), []);
  return categories.data || [];
}

export function ExpensesPage({ route, navigate }) {
  const toast = useToast();
  const categories = useExpenseCategories();
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const page = Number(f.page || 0);
  const query = { from: f.from, to: f.to, category: listOf(f.category), minAmount: f.minAmount, maxAmount: f.maxAmount, search: f.search, sort: f.sort };
  const expenses = useLoader(() => api(`/api/seller/expenses${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const data = expenses.data;
  const pageTotal = data ? data.content.reduce((s, e) => s + Number(e.amount), 0) : 0;

  return (
    <>
      <PageHead
        title="Expenses"
        sub="Rent, salaries, shipping, marketing and every other cost of running the store."
        actions={
          <>
            <ExportMenu path="/api/seller/expenses/export/{format}" query={qs(query)} />
            <Button variant="primary" icon={Plus} disabled={!categories.length} onClick={() => setEditing({})}>
              Record expense
            </Button>
          </>
        }
      />
      <Panel flush>
        <Toolbar>
          <DebouncedSearch value={f.search} onChange={(search) => set({ search })} placeholder="Description, vendor or reference" />
          <MultiSelect label="Category" options={categories} value={listOf(f.category)} onChange={(list) => set({ category: list.join(",") })} />
          <PeriodPicker allowEmpty from={f.from} to={f.to} onChange={({ from, to }) => set({ from, to })} />
          <AmountRange f={f} set={set} />
          <Select className="select-sm" value={f.sort} options={MONEY_SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />
        </Toolbar>
        <ActiveFilters
          onClear={() => clear(["sort"])}
          chips={[
            { key: "search", label: `"${f.search}"`, value: f.search, onRemove: () => set({ search: "" }) },
            { key: "dates", label: `${f.from || "start"} to ${f.to || "today"}`, value: f.from || f.to, onRemove: () => set({ from: "", to: "" }) },
            {
              key: "amount",
              label: `AED ${f.minAmount || 0} to ${f.maxAmount || "any"}`,
              value: f.minAmount || f.maxAmount,
              onRemove: () => set({ minAmount: "", maxAmount: "" }),
            },
          ]}
        />
        {expenses.loading && !data ? (
          <Loader text="Loading expenses" />
        ) : expenses.error && !data ? (
          <ErrorState message={expenses.error} onRetry={expenses.reload} />
        ) : (
          <div style={{ opacity: expenses.loading ? 0.6 : 1 }}>
            <DataTable
              rows={data.content}
              onRowClick={setEditing}
              empty={
                <Empty icon={Receipt} title="No expenses recorded" action={<Button onClick={() => setEditing({})}>Record an expense</Button>}>
                  Record what the store costs to run, so your profit and loss shows what you really earn.
                </Empty>
              }
              columns={[
                { key: "date", header: "Date", render: (e) => <span className="nowrap">{formatDay(e.expenseDate)}</span> },
                { key: "category", header: "Category", render: (e) => e.categoryLabel },
                {
                  key: "description",
                  header: "Description",
                  render: (e) => (
                    <div>
                      <div className="cell-title">{e.description}</div>
                      <div className="cell-sub">{[e.vendor, e.reference].filter(Boolean).join(" - ")}</div>
                    </div>
                  ),
                },
                { key: "amount", header: "Amount", align: "right", render: (e) => <span className="strong">{formatMoney(e.amount)}</span> },
                { key: "by", header: "Recorded by", render: (e) => <span className="small muted">{e.createdBy}</span> },
                {
                  key: "actions",
                  header: "",
                  width: 48,
                  render: (e) => (
                    <span onClick={(event) => event.stopPropagation()}>
                      <IconButton icon={Trash2} label={`Delete ${e.description}`} onClick={() => setDeleting(e)} />
                    </span>
                  ),
                },
              ]}
              footer={
                data.content.length > 1 && (
                  <tr>
                    <td colSpan={3}>This page</td>
                    <td className="num">{formatMoney(pageTotal)}</td>
                    <td colSpan={2} />
                  </tr>
                )
              }
            />
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </div>
        )}
      </Panel>
      {editing && (
        <ExpenseDialog
          expense={editing.id ? editing : null}
          categories={categories}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            expenses.reload();
          }}
        />
      )}
      <Confirm
        open={!!deleting}
        title="Delete this expense?"
        confirmLabel="Delete"
        danger
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          try {
            await api(`/api/seller/expenses/${deleting.id}`, { method: "DELETE" });
            toast("Expense deleted");
            setDeleting(null);
            expenses.reload();
          } catch (e) {
            toast(e.message, "bad");
          }
        }}
      >
        {deleting && `${deleting.description}, ${formatMoney(deleting.amount)} on ${formatDay(deleting.expenseDate)}.`}
      </Confirm>
    </>
  );
}

// ---------------------------------------------------------------- budgets

function BudgetDialog({ month, categories, line, onClose, onSaved }) {
  const toast = useToast();
  const [category, setCategory] = useState(line?.category || categories[0]?.value || "");
  const [amount, setAmount] = useState(line?.budget && Number(line.budget) > 0 ? String(line.budget) : "");
  const [busy, setBusy] = useState(false);
  return (
    <Modal
      open
      onClose={onClose}
      title={`Budget for ${longMonth(month)}`}
      sub="You get a notification when spending reaches 80% of a budget and when it goes over."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={busy}
            disabled={!(Number(amount) > 0) || !category}
            onClick={async () => {
              setBusy(true);
              try {
                const overview = await api("/api/seller/budgets", { method: "PUT", body: { month, category, amount: Number(amount) } });
                toast("Budget saved");
                onSaved(overview);
              } catch (e) {
                toast(e.message, "bad");
              } finally {
                setBusy(false);
              }
            }}
          >
            Save budget
          </Button>
        </>
      }
    >
      <div className="form-grid">
        <Field label="Category">
          <Select value={category} onChange={(e) => setCategory(e.target.value)} options={categories} disabled={!!line} />
        </Field>
        <Field label="Monthly budget">
          <MoneyInput value={amount} onChange={setAmount} autoFocus />
        </Field>
      </div>
    </Modal>
  );
}

export function BudgetsPage({ route, navigate }) {
  const toast = useToast();
  const categories = useExpenseCategories();
  const [f, set] = useFilters(route, navigate, { month: currentMonth() });
  const month = f.month;
  const budgets = useLoader(() => api(`/api/seller/budgets${qs({ month })}`), [month]);
  const [editing, setEditing] = useState(null);
  const [copying, setCopying] = useState(false);
  const b = budgets.data;
  const months = Array.from({ length: 13 }, (_, i) => shiftMonth(currentMonth(), 1 - i));

  const remove = async (line) => {
    try {
      budgets.setData(await api(`/api/seller/budgets/${line.id}`, { method: "DELETE" }));
      toast(`${line.categoryLabel} budget removed`);
    } catch (e) {
      toast(e.message, "bad");
    }
  };

  return (
    <>
      <PageHead
        title="Budgets"
        sub="Plan what you can spend each month per category and see how much is left."
        actions={
          <>
            <Select
              className="select-sm"
              value={month}
              onChange={(e) => set({ month: e.target.value })}
              options={months.map((m) => ({ value: m, label: longMonth(m) }))}
              aria-label="Month"
            />
            <Button icon={Copy} onClick={() => setCopying(true)}>
              Copy from {longMonth(shiftMonth(month, -1))}
            </Button>
            <Button variant="primary" icon={Plus} disabled={!categories.length} onClick={() => setEditing({})}>
              Set a budget
            </Button>
          </>
        }
      />
      {budgets.loading && !b ? (
        <Loader text="Loading budgets" />
      ) : budgets.error && !b ? (
        <ErrorState message={budgets.error} onRetry={budgets.reload} />
      ) : (
        <div className="stack">
          <div className="stats">
            <Stat label="Budget" icon={PiggyBank} value={formatMoney(b.totalBudget)} />
            <Stat
              label="Spent"
              icon={Banknote}
              value={formatMoney(b.totalSpent)}
              hint={`${formatPercent(b.percentUsed, 0)} of the budget`}
              attention={Number(b.percentUsed) >= 80}
            />
            <Stat label="Left" icon={Wallet} value={formatMoney(b.remaining)} attention={Number(b.remaining) < 0} />
            <Stat label="Spent without a budget" icon={RotateCcw} value={formatMoney(b.unbudgetedSpending)} hint="categories with no budget this month" />
          </div>
          {Number(b.totalBudget) > 0 && (
            <Panel>
              <div className="row-between small" style={{ marginBottom: 8 }}>
                <span>
                  {formatMoney(b.totalSpent)} spent of {formatMoney(b.totalBudget)} in {longMonth(b.month)}
                </span>
                <span className="strong">{formatPercent(b.percentUsed, 0)}</span>
              </div>
              <Meter percent={b.percentUsed} tone={Number(b.percentUsed) >= 100 ? "bad" : Number(b.percentUsed) >= 80 ? "warn" : null} />
            </Panel>
          )}
          <Panel flush>
            <DataTable
              rowKey="category"
              rows={b.lines}
              onRowClick={(line) => (line.id ? setEditing(line) : setEditing({ category: line.category }))}
              empty={
                <Empty icon={PiggyBank} title={`No budgets for ${longMonth(month)}`} action={<Button onClick={() => setEditing({})}>Set a budget</Button>}>
                  Start with the big ones: rent, salaries, shipping and marketing.
                </Empty>
              }
              columns={[
                { key: "category", header: "Category", render: (l) => <span className="cell-title">{l.categoryLabel}</span> },
                {
                  key: "budget",
                  header: "Budget",
                  align: "right",
                  render: (l) => (Number(l.budget) > 0 ? formatMoney(l.budget) : <span className="muted">None</span>),
                },
                { key: "spent", header: "Spent", align: "right", render: (l) => formatMoney(l.spent) },
                {
                  key: "left",
                  header: "Left",
                  align: "right",
                  render: (l) => (Number(l.budget) > 0 ? <span className={Number(l.remaining) < 0 ? "bad strong" : ""}>{formatMoney(l.remaining)}</span> : "-"),
                },
                {
                  key: "used",
                  header: "Used",
                  render: (l) =>
                    Number(l.budget) > 0 ? (
                      <div style={{ minWidth: 140 }}>
                        <Meter percent={l.percentUsed} tone={l.status === "OVER" ? "bad" : l.status === "WARNING" ? "warn" : null} />
                        <div className="cell-sub">{formatPercent(l.percentUsed, 0)}</div>
                      </div>
                    ) : null,
                },
                { key: "status", header: "Status", render: (l) => <StatusBadge status={l.status} /> },
                {
                  key: "actions",
                  header: "",
                  width: 48,
                  render: (l) =>
                    l.id ? (
                      <span onClick={(e) => e.stopPropagation()}>
                        <IconButton icon={Trash2} label={`Remove the ${l.categoryLabel} budget`} onClick={() => remove(l)} />
                      </span>
                    ) : null,
                },
              ]}
            />
          </Panel>
        </div>
      )}
      {editing && (
        <BudgetDialog
          month={month}
          categories={categories}
          line={editing.id ? editing : editing.category ? { category: editing.category } : null}
          onClose={() => setEditing(null)}
          onSaved={(overview) => {
            setEditing(null);
            budgets.setData(overview);
          }}
        />
      )}
      <Confirm
        open={copying}
        title={`Copy budgets from ${longMonth(shiftMonth(month, -1))}?`}
        confirmLabel="Copy budgets"
        onClose={() => setCopying(false)}
        onConfirm={async () => {
          try {
            budgets.setData(await api("/api/seller/budgets/copy", { method: "POST", body: { fromMonth: shiftMonth(month, -1), toMonth: month } }));
            toast("Budgets copied");
          } catch (e) {
            toast(e.message, "bad");
          } finally {
            setCopying(false);
          }
        }}
      >
        Every budget of {longMonth(shiftMonth(month, -1))} is copied to {longMonth(month)}. Budgets you already set for {longMonth(month)} stay as they are.
      </Confirm>
    </>
  );
}
