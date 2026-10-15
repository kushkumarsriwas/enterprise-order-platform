import { useState } from "react";
import {
  Archive,
  BarChart3,
  Boxes,
  ClipboardList,
  Factory,
  FileSpreadsheet,
  FileText,
  History,
  Layers,
  PackageCheck,
  Receipt,
  Repeat,
  Scale,
  ShoppingBag,
  TrendingUp,
  Users,
  Wallet,
  Warehouse,
} from "lucide-react";
import { download, qs } from "../../api.js";
import { Button, Select } from "../../components/ui.jsx";
import { formatDay, lastDays } from "../../format.js";
import { useToast } from "../../hooks.jsx";
import { PageHead, PeriodPicker } from "./common.jsx";

const GROUPS = [
  {
    title: "Sales and money",
    reports: [
      {
        icon: TrendingUp,
        title: "Sales report",
        text: "Revenue, profit, best sellers, departments, emirates and the trend.",
        path: "/api/seller/reports/sales/{format}",
        dated: true,
      },
      {
        icon: Scale,
        title: "Profit and loss",
        text: "Statement with cost of goods, Nexora fees, every expense and a monthly breakdown.",
        path: "/api/seller/reports/profit-loss/{format}",
        dated: true,
      },
      {
        icon: ShoppingBag,
        title: "Orders",
        text: "Every order with customer, emirate, payment, total and profit.",
        path: "/api/seller/orders/export/{format}",
        dated: true,
      },
      {
        icon: Wallet,
        title: "Payments",
        text: "Each payment with method, status, Nexora fee and what you receive.",
        path: "/api/seller/payments/export/{format}",
        dated: true,
      },
      { icon: Receipt, title: "Expenses", text: "Your recorded expenses by date and category.", path: "/api/seller/expenses/export/{format}", dated: true },
      { icon: Users, title: "Customers", text: "Customers with orders, spend, last order and segment.", path: "/api/seller/customers/export/{format}" },
    ],
  },
  {
    title: "Inventory",
    reports: [
      {
        icon: Boxes,
        title: "Inventory valuation",
        text: "Stock on hand at cost and at selling price, by department and warehouse.",
        path: "/api/seller/reports/inventory/valuation/{format}",
        dated: true,
      },
      {
        icon: Layers,
        title: "ABC analysis",
        text: "Which products make 80%, 15% and 5% of your revenue.",
        path: "/api/seller/reports/inventory/abc/{format}",
        dated: true,
      },
      {
        icon: Archive,
        title: "Dead stock",
        text: "Products on hand that have not sold for a while.",
        path: "/api/seller/reports/inventory/dead-stock/{format}",
        deadStock: true,
      },
      {
        icon: BarChart3,
        title: "Adjustments by reason",
        text: "Damaged, expired, lost and found stock at cost.",
        path: "/api/seller/reports/inventory/shrinkage/{format}",
        dated: true,
      },
      {
        icon: History,
        title: "Stock ledger",
        text: "Every movement of stock: received, sold, adjusted, transferred.",
        path: "/api/seller/inventory/movements/export/{format}",
        dated: true,
      },
      {
        icon: Warehouse,
        title: "Stock levels",
        text: "Available, reserved, on hand and incoming for every product today.",
        path: "/api/seller/inventory/export/{format}",
      },
      {
        icon: FileSpreadsheet,
        title: "Product catalogue",
        text: "All products with prices, cost, margin, stock and listing.",
        path: "/api/seller/products/export/{format}",
      },
    ],
  },
  {
    title: "Purchasing",
    reports: [
      {
        icon: ClipboardList,
        title: "Purchase orders",
        text: "Orders to suppliers with status, amounts and what is outstanding.",
        path: "/api/seller/purchase-orders/export/{format}",
        dated: true,
      },
      {
        icon: PackageCheck,
        title: "Purchases (goods received)",
        text: "Deliveries booked into your warehouses and what they cost.",
        path: "/api/seller/goods-receipts/export/{format}",
        dated: true,
      },
      {
        icon: Repeat,
        title: "Reorder report",
        text: "What to order now, how many and from which supplier.",
        path: "/api/seller/replenishment/export/{format}",
      },
      { icon: Factory, title: "Suppliers", text: "Suppliers with lead time, spend and on-time delivery.", path: "/api/seller/suppliers/export/{format}" },
    ],
  },
];

function ReportCard({ report, period, deadStockDays }) {
  const toast = useToast();
  const [busy, setBusy] = useState(null);
  const run = async (format) => {
    setBusy(format);
    try {
      const query = report.dated ? qs(period) : report.deadStock ? qs({ deadStockDays }) : "";
      const name = await download(`${report.path.replace("{format}", format)}${query}`);
      toast(`Downloaded ${name}`);
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setBusy(null);
    }
  };
  return (
    <article className="info-card">
      <div className="row" style={{ gap: 12, alignItems: "flex-start" }}>
        <span className="report-icon">
          <report.icon />
        </span>
        <div className="grow">
          <h3>{report.title}</h3>
          <p>{report.text}</p>
        </div>
      </div>
      <p className="tiny muted" style={{ margin: 0 }}>
        {report.dated ? `${formatDay(period.from)} to ${formatDay(period.to)}` : report.deadStock ? `Not sold for ${deadStockDays} days` : "As of today"}
      </p>
      <div className="foot">
        <Button size="sm" icon={FileSpreadsheet} loading={busy === "xlsx"} onClick={() => run("xlsx")}>
          Excel
        </Button>
        <Button size="sm" icon={FileText} loading={busy === "pdf"} onClick={() => run("pdf")}>
          PDF
        </Button>
      </div>
    </article>
  );
}

/** Every report of Seller Central in one place, as Excel (to work with) or PDF (to share and print). */
export function ReportsPage() {
  const [period, setPeriod] = useState(lastDays(30));
  const [deadStockDays, setDeadStockDays] = useState("90");
  return (
    <>
      <PageHead
        title="Reports"
        sub="Download any report as an Excel workbook to work with, or as a PDF to share and print. Lists elsewhere export with their filters."
        actions={
          <>
            <PeriodPicker from={period.from} to={period.to} onChange={setPeriod} label="Period for dated reports" />
            <Select
              className="select-sm"
              value={deadStockDays}
              onChange={(e) => setDeadStockDays(e.target.value)}
              aria-label="Dead stock after"
              options={["30", "60", "90", "180", "365"].map((d) => ({ value: d, label: `Dead after ${d} days` }))}
            />
          </>
        }
      />
      <div className="stack-lg">
        {GROUPS.map((group) => (
          <section key={group.title} className="stack">
            <h2 style={{ fontSize: "var(--text-lg)" }}>{group.title}</h2>
            <div className="card-grid">
              {group.reports.map((report) => (
                <ReportCard key={report.title} report={report} period={period} deadStockDays={deadStockDays} />
              ))}
            </div>
          </section>
        ))}
      </div>
    </>
  );
}
