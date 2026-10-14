import { Boxes, FileBarChart, Scale } from "lucide-react";
import { formatDay } from "../../format.js";
import { PageHeader } from "../../components/ui.jsx";
import { ReportButtons, usePeriod } from "./common.jsx";

/** Downloadable reports for the accountant, the bank or the owner: PDF to share, Excel to analyse. */
export default function MerchantReports({ toast }) {
  const sales = usePeriod("30d", ["7d", "30d", "90d", "mtd", "ytd"]);
  const pl = usePeriod("6m", ["mtd", "90d", "6m", "ytd"]);
  return (
    <div className="page" style={{ maxWidth: 980 }}>
      <PageHeader title="Reports" subtitle="Download PDF reports to share, or Excel workbooks to analyse further. Every report uses live data." />
      <ReportCard
        icon={FileBarChart}
        title="Sales report"
        text="Every order in the period with its cost and profit, best sellers and daily sales."
        picker={sales.picker}
        range={sales.range}
        path="/api/merchant/reports/sales"
        toast={toast}
      />
      <ReportCard
        icon={Scale}
        title="Profit and loss"
        text="Revenue, cost of goods, platform fees and expenses by category, with a monthly breakdown and the list of expenses."
        picker={pl.picker}
        range={pl.range}
        path="/api/merchant/reports/profit-loss"
        toast={toast}
      />
      <ReportCard
        icon={Boxes}
        title="Inventory valuation"
        text="Current stock of every product with average cost, value at cost and at selling price, and low-stock items."
        path="/api/merchant/reports/inventory"
        toast={toast}
      />
    </div>
  );
}

function ReportCard({ icon: Icon, title, text, picker, range, path, toast }) {
  return (
    <section className="card row wrap" style={{ gap: 16, alignItems: "flex-start" }}>
      <span className="notif-icon" style={{ width: 44, height: 44 }}>
        <Icon size={20} />
      </span>
      <div className="grow stack tight" style={{ minWidth: 240 }}>
        <h2>{title}</h2>
        <p className="muted">{text}</p>
        {picker && (
          <div className="row wrap" style={{ marginTop: 6 }}>
            {picker}
            <span className="muted small">
              {formatDay(range.from)} to {formatDay(range.to)}
            </span>
          </div>
        )}
      </div>
      <ReportButtons path={path} range={range} toast={toast} />
    </section>
  );
}
