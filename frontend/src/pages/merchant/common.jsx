import { useState } from "react";
import { Download, FileText } from "lucide-react";
import { download, qs } from "../../api.js";
import { lastDays, lastMonths, monthToDate, yearToDate } from "../../format.js";
import { Segmented } from "../../components/ui.jsx";

const PERIODS = [
  { id: "7d", label: "7 days", range: () => lastDays(7) },
  { id: "30d", label: "30 days", range: () => lastDays(30) },
  { id: "90d", label: "90 days", range: () => lastDays(90) },
  { id: "mtd", label: "This month", range: monthToDate },
  { id: "6m", label: "6 months", range: () => lastMonths(6) },
  { id: "ytd", label: "This year", range: yearToDate },
];

export function usePeriod(initial = "30d", allowed) {
  const options = allowed ? allowed.map((id) => PERIODS.find((p) => p.id === id)) : PERIODS;
  const [id, setId] = useState(initial);
  const range = (options.find((p) => p.id === id) || options[0]).range();
  const picker = <Segmented options={options} value={id} onChange={setId} ariaLabel="Period" />;
  return { id, range, picker, setId };
}

/** PDF / Excel buttons for a report endpoint ("/api/merchant/reports/sales"). */
export function ReportButtons({ path, range, toast, compact }) {
  const [busy, setBusy] = useState("");
  const get = async (format) => {
    setBusy(format);
    try {
      const name = await download(`${path}/${format}${range ? qs(range) : ""}`);
      toast.show(`Downloaded ${name}`);
    } catch (err) {
      toast.show(err.message, "error");
    } finally {
      setBusy("");
    }
  };
  return (
    <div className="row">
      <button className={`btn ${compact ? "btn-sm" : ""}`} onClick={() => get("pdf")} disabled={Boolean(busy)}>
        <FileText size={15} /> {busy === "pdf" ? "Preparing..." : "PDF"}
      </button>
      <button className={`btn ${compact ? "btn-sm" : ""}`} onClick={() => get("xlsx")} disabled={Boolean(busy)}>
        <Download size={15} /> {busy === "xlsx" ? "Preparing..." : "Excel"}
      </button>
    </div>
  );
}
