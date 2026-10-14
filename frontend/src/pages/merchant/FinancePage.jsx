import { useState } from "react";
import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, Copy, CreditCard, Pencil, PiggyBank, Plus, Receipt, Search, Trash2, TrendingDown, TrendingUp, Wallet, XCircle } from "lucide-react";
import { api, qs } from "../../api.js";
import { compactMoney, currentMonth, formatDate, formatDay, formatMoney, formatNumber, formatPercent, label, longMonth, monthLabel, shiftMonth, today } from "../../format.js";
import { useDebounced, useLoader } from "../../hooks.jsx";
import { BarList, ChartCard, ColumnsChart } from "../../components/charts.jsx";
import { Alert, Badge, Empty, Field, Modal, PageHeader, Pagination, Spinner, StatTile, Tabs } from "../../components/ui.jsx";
import { ReportButtons, usePeriod } from "./common.jsx";

export default function MerchantFinance({ toast }) {
  const [tab, setTab] = useState("pl");
  return (
    <div className="page">
      <PageHeader title="Finance" subtitle="Profit and loss, expenses, monthly budgets and the money you received from the marketplace." />
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { id: "pl", label: "Profit and loss" },
          { id: "expenses", label: "Expenses" },
          { id: "budgets", label: "Budgets" },
          { id: "payments", label: "Payments received" },
        ]}
      />
      {tab === "pl" && <ProfitAndLoss toast={toast} />}
      {tab === "expenses" && <Expenses toast={toast} />}
      {tab === "budgets" && <Budgets toast={toast} />}
      {tab === "payments" && <Payments />}
    </div>
  );
}

// ---------------------------------------------------------------- profit and loss
function ProfitAndLoss({ toast }) {
  const period = usePeriod("6m", ["mtd", "90d", "6m", "ytd"]);
  const { from, to } = period.range;
  const pl = useLoader(() => api(`/api/merchant/finance/profit-loss${qs({ from, to })}`), [from, to]);
  const p = pl.data;
  const loss = p && Number(p.netProfit) < 0;

  return (
    <>
      <div className="toolbar" style={{ justifyContent: "space-between" }}>
        {period.picker}
        <ReportButtons path="/api/merchant/reports/profit-loss" range={{ from, to }} toast={toast} compact />
      </div>
      <Alert>{pl.error}</Alert>
      {pl.loading && !p ? (
        <Spinner />
      ) : p ? (
        <div className={`stack ${pl.loading ? "refetching" : ""}`} style={{ gap: 18 }}>
          <div className="stats">
            <StatTile hero icon={loss ? TrendingDown : TrendingUp} label={loss ? "Net loss" : "Net profit"} value={formatMoney(p.netProfit)} hint={`${formatPercent(p.netMarginPercent)} net margin, ${formatDay(p.from)} to ${formatDay(p.to)}`} />
            <StatTile icon={Wallet} label="Revenue" value={formatMoney(p.revenue)} hint={`${formatNumber(p.orders)} order${p.orders === 1 ? "" : "s"} sold`} />
            <StatTile icon={PiggyBank} label="Gross profit" value={formatMoney(p.grossProfit)} hint={`${formatPercent(p.grossMarginPercent)} after cost of goods`} />
            <StatTile icon={Receipt} label="Fees and expenses" value={formatMoney(Number(p.platformFees) + Number(p.operatingExpenses))} hint={`${formatMoney(p.platformFees)} platform fees (${p.feeRatePercent}%)`} />
          </div>
          <div className="two-col">
            <ChartCard
              title="Revenue and net profit by month"
              subtitle="AED. Net profit = revenue - cost of goods - platform fees - expenses"
              empty={p.months.every((m) => Number(m.revenue) === 0 && Number(m.operatingExpenses) === 0) ? "No sales or expenses in this period yet." : null}
              table={{
                columns: [
                  { key: "month", label: "Month", format: monthLabel },
                  { key: "revenue", label: "Revenue", numeric: true, format: (v) => formatMoney(v) },
                  { key: "costOfGoodsSold", label: "Cost of goods", numeric: true, format: (v) => formatMoney(v) },
                  { key: "platformFees", label: "Fees", numeric: true, format: (v) => formatMoney(v) },
                  { key: "operatingExpenses", label: "Expenses", numeric: true, format: (v) => formatMoney(v) },
                  { key: "netProfit", label: "Net profit", numeric: true, format: (v) => formatMoney(v) },
                ],
                rows: p.months,
              }}
            >
              <ColumnsChart
                data={p.months.map((m) => ({ month: m.month, revenue: Number(m.revenue), net: Number(m.netProfit) }))}
                xKey="month"
                labelFormat={monthLabel}
                valueFormat={(v) => formatMoney(v)}
                series={[
                  { key: "revenue", name: "Revenue" },
                  { key: "net", name: "Net profit" },
                ]}
              />
            </ChartCard>
            <section className="card">
              <h3>Statement</h3>
              <table className="table statement">
                <tbody>
                  <tr>
                    <td>Revenue</td>
                    <td className="num">{formatMoney(p.revenue)}</td>
                  </tr>
                  <tr className="indent">
                    <td>Cost of goods sold</td>
                    <td className="num">-{formatMoney(p.costOfGoodsSold)}</td>
                  </tr>
                  <tr className="subtotal">
                    <td>Gross profit</td>
                    <td className="num">{formatMoney(p.grossProfit)}</td>
                  </tr>
                  <tr className="indent">
                    <td>Platform fees ({p.feeRatePercent}%)</td>
                    <td className="num">-{formatMoney(p.platformFees)}</td>
                  </tr>
                  {p.expensesByCategory.map((c) => (
                    <tr className="indent" key={c.category}>
                      <td>{c.label}</td>
                      <td className="num">-{formatMoney(c.amount)}</td>
                    </tr>
                  ))}
                  <tr className="total">
                    <td>{loss ? "Net loss" : "Net profit"}</td>
                    <td className={`num ${loss ? "neg" : ""}`}>{formatMoney(p.netProfit)}</td>
                  </tr>
                </tbody>
              </table>
            </section>
          </div>
          <ChartCard
            title="Where the money goes"
            subtitle="Operating expenses by category in this period"
            empty={p.expensesByCategory.length === 0 ? "No expenses recorded in this period. Add rent, salaries, marketing and other costs under Expenses." : null}
            table={{
              columns: [
                { key: "label", label: "Category" },
                { key: "entries", label: "Entries", numeric: true },
                { key: "amount", label: "Amount", numeric: true, format: (v) => formatMoney(v) },
                { key: "percentOfExpenses", label: "Share", numeric: true, format: formatPercent },
              ],
              rows: p.expensesByCategory,
            }}
          >
            <BarList
              items={p.expensesByCategory.map((c) => ({ key: c.category, label: c.label, value: Number(c.amount), note: `${formatPercent(c.percentOfExpenses)} of expenses` }))}
              valueFormat={compactMoney}
            />
          </ChartCard>
        </div>
      ) : null}
    </>
  );
}

// ---------------------------------------------------------------- expenses
function Expenses({ toast }) {
  const period = usePeriod("mtd", ["mtd", "90d", "6m", "ytd"]);
  const { from, to } = period.range;
  const [category, setCategory] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const categories = useLoader(() => api("/api/merchant/expenses/categories"), []);
  const expenses = useLoader(() => api(`/api/merchant/expenses${qs({ from, to, category, search: debounced, page, size: 20 })}`), [from, to, category, debounced, page]);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const shownTotal = (expenses.data?.content || []).reduce((sum, e) => sum + Number(e.amount), 0);

  const remove = async () => {
    try {
      await api(`/api/merchant/expenses/${deleting.id}`, { method: "DELETE" });
      toast.show("Expense deleted");
      setDeleting(null);
      expenses.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };

  return (
    <>
      <div className="toolbar">
        {period.picker}
        <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }} aria-label="Category">
          <option value="">All categories</option>
          {(categories.data || []).map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
        <div className="search">
          <Search size={16} />
          <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} placeholder="Search description, vendor or invoice" aria-label="Search expenses" />
        </div>
        <button className="btn btn-primary" onClick={() => setEditing({ category: "RENT", amount: "", description: "", vendor: "", reference: "", expenseDate: today() })}>
          <Plus size={16} /> Add expense
        </button>
      </div>
      <p className="muted small">Record running costs here. Stock purchases are tracked in Inventory and counted as cost of goods when the items sell.</p>
      <Alert>{expenses.error}</Alert>
      {expenses.loading && !expenses.data ? (
        <Spinner />
      ) : expenses.data?.content.length === 0 ? (
        <Empty icon={Receipt} title="No expenses in this view">Add rent, salaries, marketing, shipping and other costs to see your real profit.</Empty>
      ) : (
        <div className={`card flush ${expenses.loading ? "refetching" : ""}`}>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Category</th>
                  <th>Description</th>
                  <th>Invoice</th>
                  <th className="num">Amount</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {expenses.data?.content.map((e) => (
                  <tr key={e.id}>
                    <td className="nowrap">{formatDay(e.expenseDate)}</td>
                    <td>{e.categoryLabel}</td>
                    <td>
                      {e.description}
                      {e.vendor && <span className="sub">{e.vendor}</span>}
                    </td>
                    <td className="mono">{e.reference || "-"}</td>
                    <td className="num">{formatMoney(e.amount)}</td>
                    <td className="nowrap right">
                      <button className="icon-btn" onClick={() => setEditing({ ...e, amount: String(e.amount), vendor: e.vendor || "", reference: e.reference || "" })} aria-label="Edit expense">
                        <Pencil size={15} />
                      </button>
                      <button className="icon-btn danger" onClick={() => setDeleting(e)} aria-label="Delete expense">
                        <Trash2 size={15} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={4}>Total shown{expenses.data?.totalPages > 1 ? " on this page" : ""}</td>
                  <td className="num">{formatMoney(shownTotal)}</td>
                  <td />
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      )}
      <Pagination page={expenses.data} onChange={setPage} />
      {editing && (
        <ExpenseForm
          initial={editing}
          categories={categories.data || []}
          onClose={() => setEditing(null)}
          onSaved={(created) => {
            setEditing(null);
            toast.show(created ? "Expense recorded" : "Expense updated");
            expenses.reload();
          }}
        />
      )}
      {deleting && (
        <Modal title="Delete this expense?" onClose={() => setDeleting(null)}>
          <div className="stack">
            <p>
              {deleting.description} - {formatMoney(deleting.amount)} on {formatDay(deleting.expenseDate)}
            </p>
            <div className="row end">
              <button className="btn btn-ghost" onClick={() => setDeleting(null)}>
                Keep it
              </button>
              <button className="btn btn-danger" onClick={remove}>
                Delete expense
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}

function ExpenseForm({ initial, categories, onClose, onSaved }) {
  const creating = !initial.id;
  const [form, setForm] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    const body = { category: form.category, amount: form.amount, description: form.description, vendor: form.vendor || null, reference: form.reference || null, expenseDate: form.expenseDate };
    try {
      if (creating) await api("/api/merchant/expenses", { method: "POST", body });
      else await api(`/api/merchant/expenses/${initial.id}`, { method: "PUT", body });
      onSaved(creating);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
      setBusy(false);
    }
  };

  return (
    <Modal title={creating ? "Add an expense" : "Edit expense"} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <div className="form-grid">
          <Field label="Category" error={fieldErrors.category}>
            <select value={form.category} onChange={set("category")}>
              {categories.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Amount (AED)" error={fieldErrors.amount}>
            <input type="number" min="0.01" step="0.01" value={form.amount} onChange={set("amount")} required autoFocus />
          </Field>
          <Field label="Description" error={fieldErrors.description} className="span-2">
            <input value={form.description} onChange={set("description")} maxLength={200} placeholder="For example: October warehouse rent" required />
          </Field>
          <Field label="Paid to (vendor)" error={fieldErrors.vendor}>
            <input value={form.vendor} onChange={set("vendor")} maxLength={100} />
          </Field>
          <Field label="Invoice or receipt number" error={fieldErrors.reference}>
            <input value={form.reference} onChange={set("reference")} maxLength={60} />
          </Field>
          <Field label="Date" error={fieldErrors.expenseDate}>
            <input type="date" value={form.expenseDate} max={today()} onChange={set("expenseDate")} required />
          </Field>
        </div>
        <Alert onClose={() => setError("")}>{error}</Alert>
        <div className="row end">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Saving..." : creating ? "Add expense" : "Save changes"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------- budgets
const BUDGET_STATUS = {
  ON_TRACK: { text: "On track", className: "good", icon: CheckCircle2, meter: "" },
  WARNING: { text: "Almost used", className: "warning", icon: AlertTriangle, meter: "warning" },
  OVER: { text: "Over budget", className: "over", icon: XCircle, meter: "over" },
};

function Budgets({ toast }) {
  const [month, setMonth] = useState(currentMonth());
  const overview = useLoader(() => api(`/api/merchant/budgets${qs({ month })}`), [month]);
  const categories = useLoader(() => api("/api/merchant/expenses/categories"), []);
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState("");
  const o = overview.data;
  const previous = shiftMonth(month, -1);

  const copy = async () => {
    setError("");
    try {
      await api("/api/merchant/budgets/copy", { method: "POST", body: { fromMonth: previous, toMonth: month } });
      toast.show(`Budgets copied from ${longMonth(previous)}`);
      overview.reload();
    } catch (err) {
      setError(err.message);
    }
  };
  const remove = async (line) => {
    try {
      await api(`/api/merchant/budgets/${line.id}`, { method: "DELETE" });
      toast.show(`${line.categoryLabel} budget removed`);
      overview.reload();
    } catch (err) {
      toast.show(err.message, "error");
    }
  };
  const totalStatus = o && o.totalBudget > 0 ? (Number(o.percentUsed) > 100 ? "OVER" : Number(o.percentUsed) >= 80 ? "WARNING" : "ON_TRACK") : null;

  return (
    <>
      <div className="toolbar" style={{ justifyContent: "space-between" }}>
        <div className="row">
          <button className="icon-btn" onClick={() => setMonth(shiftMonth(month, -1))} aria-label="Previous month">
            <ChevronLeft size={18} />
          </button>
          <strong style={{ minWidth: 130, textAlign: "center" }}>{longMonth(month)}</strong>
          <button className="icon-btn" onClick={() => setMonth(shiftMonth(month, 1))} aria-label="Next month">
            <ChevronRight size={18} />
          </button>
        </div>
        <div className="row">
          <button className="btn" onClick={copy}>
            <Copy size={15} /> Copy from {monthLabel(previous)}
          </button>
          <button className="btn btn-primary" onClick={() => setEditing({ category: "MARKETING", amount: "" })}>
            <Plus size={16} /> Set a budget
          </button>
        </div>
      </div>
      <Alert onClose={() => setError("")}>{error || overview.error}</Alert>
      {overview.loading && !o ? (
        <Spinner />
      ) : o ? (
        <div className={`stack ${overview.loading ? "refetching" : ""}`}>
          <div className="card stack">
            <div className="row between wrap">
              <div>
                <span className="muted small">Spent this month</span>
                <div className="stat-value">
                  {formatMoney(o.totalSpent)} <span className="muted" style={{ fontSize: 15, fontWeight: 600 }}>of {formatMoney(o.totalBudget)} budgeted</span>
                </div>
              </div>
              {totalStatus && <StatusLabel status={totalStatus} />}
            </div>
            {Number(o.totalBudget) > 0 && (
              <div className={`meter ${BUDGET_STATUS[totalStatus].meter}`} role="meter" aria-valuenow={Number(o.percentUsed)} aria-valuemin={0} aria-valuemax={100} aria-label="Budget used">
                <span style={{ width: `${Math.min(100, Number(o.percentUsed))}%` }} />
              </div>
            )}
            {Number(o.unbudgetedSpending) > 0 && <p className="muted small">{formatMoney(o.unbudgetedSpending)} was spent in categories without a budget.</p>}
          </div>
          {o.lines.length === 0 ? (
            <Empty icon={PiggyBank} title={`No budgets for ${longMonth(month)}`}>
              Set a monthly limit per category (for example marketing or shipping). You get a notification at 80% and when a budget is exceeded.
            </Empty>
          ) : (
            <div className="card">
              {o.lines.map((line) => {
                const status = BUDGET_STATUS[line.status];
                return (
                  <div className="budget-line" key={line.category}>
                    <div>
                      <strong>{line.categoryLabel}</strong>
                      <div className="muted small">
                        {line.budget === null ? `${formatMoney(line.spent)} spent, no budget set` : `${formatMoney(line.spent)} of ${formatMoney(line.budget)}`}
                      </div>
                    </div>
                    <div>
                      {line.budget !== null ? (
                        <div className={`meter ${status.meter}`} role="meter" aria-valuenow={Number(line.percentUsed)} aria-valuemin={0} aria-valuemax={100} aria-label={`${line.categoryLabel} budget used`}>
                          <span style={{ width: `${Math.min(100, Number(line.percentUsed))}%` }} />
                        </div>
                      ) : (
                        <span className="muted small">Not budgeted</span>
                      )}
                    </div>
                    <div className="row" style={{ justifyContent: "flex-end" }}>
                      {line.budget !== null ? (
                        <>
                          <StatusLabel status={line.status} percent={line.percentUsed} />
                          <button className="icon-btn" onClick={() => setEditing({ category: line.category, amount: String(line.budget) })} aria-label={`Change ${line.categoryLabel} budget`}>
                            <Pencil size={15} />
                          </button>
                          <button className="icon-btn danger" onClick={() => remove(line)} aria-label={`Remove ${line.categoryLabel} budget`}>
                            <Trash2 size={15} />
                          </button>
                        </>
                      ) : (
                        <button className="btn btn-sm" onClick={() => setEditing({ category: line.category, amount: "" })}>
                          Set budget
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      ) : null}
      {editing && (
        <BudgetForm
          month={month}
          initial={editing}
          categories={categories.data || []}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            toast.show("Budget saved");
            overview.reload();
          }}
        />
      )}
    </>
  );
}

function StatusLabel({ status, percent }) {
  const s = BUDGET_STATUS[status];
  if (!s) return null;
  const Icon = s.icon;
  return (
    <span className={`status-label ${s.className}`}>
      <Icon size={14} /> {s.text}
      {percent !== undefined && percent !== null ? ` (${formatPercent(percent)})` : ""}
    </span>
  );
}

function BudgetForm({ month, initial, categories, onClose, onSaved }) {
  const [form, setForm] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api("/api/merchant/budgets", { method: "PUT", body: { month, category: form.category, amount: form.amount } });
      onSaved();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };
  return (
    <Modal title={`Budget for ${longMonth(month)}`} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <div className="form-grid">
          <Field label="Category">
            <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
              {categories.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Monthly limit (AED)">
            <input type="number" min="0.01" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required autoFocus />
          </Field>
        </div>
        <Alert onClose={() => setError("")}>{error}</Alert>
        <div className="row end">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Saving..." : "Save budget"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------- payments received
const METHOD_NAMES = { CARD: "Card", UPI: "UPI", COD: "Cash on delivery" };

function Payments() {
  const period = usePeriod("30d", ["30d", "90d", "6m", "ytd"]);
  const { from, to } = period.range;
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const summary = useLoader(() => api(`/api/merchant/payments/summary${qs({ from, to })}`), [from, to]);
  const payments = useLoader(() => api(`/api/merchant/payments${qs({ status, page, size: 20 })}`), [status, page]);
  const s = summary.data;

  return (
    <>
      <div className="toolbar">{period.picker}</div>
      <Alert>{summary.error || payments.error}</Alert>
      <div className={`stats ${summary.loading && s ? "refetching" : ""}`}>
        <StatTile icon={Wallet} label="Gross sales" value={s ? formatMoney(s.grossSales) : "-"} hint={s ? `${formatNumber(s.payments)} payments` : null} />
        <StatTile icon={Receipt} label="Platform fees" value={s ? formatMoney(s.platformFees) : "-"} hint={s ? `${s.feeRatePercent}% of every sale` : null} />
        <StatTile hero icon={PiggyBank} label="Your net earnings" value={s ? formatMoney(s.netEarnings) : "-"} hint="After platform fees" />
        <StatTile icon={CreditCard} label="Cash to collect" value={s ? formatMoney(s.awaitingCollection) : "-"} hint={s ? `${formatMoney(s.refunded)} refunded, ${s.failedAttempts} declined attempts` : null} />
      </div>
      <div className="toolbar">
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Payment status">
          <option value="">All payments</option>
          {["SUCCESS", "PENDING", "REFUNDED", "CANCELLED", "FAILED"].map((st) => (
            <option key={st} value={st}>
              {st === "PENDING" ? "Cash on delivery (to collect)" : label(st)}
            </option>
          ))}
        </select>
      </div>
      {payments.loading && !payments.data ? (
        <Spinner />
      ) : payments.data?.content.length === 0 ? (
        <Empty icon={CreditCard} title="No payments yet">Payments for your orders appear here with the platform fee and your net amount.</Empty>
      ) : (
        <div className={`card flush ${payments.loading ? "refetching" : ""}`}>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Order</th>
                  <th>Method</th>
                  <th className="num">Amount</th>
                  <th className="num">Fee</th>
                  <th className="num">You receive</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {payments.data?.content.map((p) => (
                  <tr key={p.id}>
                    <td className="nowrap">{formatDate(p.createdAt)}</td>
                    <td>
                      {p.orderNumber}
                      <span className="sub mono">{p.transactionRef}</span>
                    </td>
                    <td>
                      {METHOD_NAMES[p.method] || p.method}
                      {p.maskedInstrument && <span className="sub">{p.maskedInstrument}</span>}
                    </td>
                    <td className="num">{formatMoney(p.amount)}</td>
                    <td className="num">{formatMoney(p.platformFee)}</td>
                    <td className="num">{formatMoney(p.merchantNet)}</td>
                    <td>
                      <Badge value={p.status} text={p.status === "PENDING" ? "To collect" : undefined} />
                      {(p.failureReason || p.refundReason) && <span className="sub">{p.failureReason || p.refundReason}</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <Pagination page={payments.data} onChange={setPage} />
    </>
  );
}
