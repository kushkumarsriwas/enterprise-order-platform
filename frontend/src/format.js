const money = new Intl.NumberFormat("en-AE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const integer = new Intl.NumberFormat("en-AE");
const compactFormat = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

export const formatMoney = (amount, currency = "AED") => `${currency} ${money.format(Number(amount || 0))}`;
export const formatAmount = (amount) => money.format(Number(amount || 0));
export const formatNumber = (value) => integer.format(Number(value || 0));
export const compact = (value) => compactFormat.format(Number(value || 0));
export const compactMoney = (value) => `AED ${compact(value)}`;
export const formatPercent = (value) => `${Number(value || 0).toFixed(1)}%`;

export const formatDate = (iso) =>
  iso ? new Date(iso).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" }) : "-";
export const formatDay = (iso) =>
  iso ? new Date(iso.length === 10 ? `${iso}T00:00:00Z` : iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: iso.length === 10 ? "UTC" : undefined }) : "-";
export const shortDay = (isoDate) =>
  new Date(`${isoDate}T00:00:00Z`).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });
export const monthLabel = (yearMonth) =>
  new Date(`${yearMonth}-01T00:00:00Z`).toLocaleDateString("en-GB", { month: "short", year: "numeric", timeZone: "UTC" });
export const longMonth = (yearMonth) =>
  new Date(`${yearMonth}-01T00:00:00Z`).toLocaleDateString("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });

export const timeAgo = (iso) => {
  const seconds = Math.max(1, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h ago`;
  if (seconds < 86400 * 7) return `${Math.round(seconds / 86400)} d ago`;
  return formatDay(iso);
};

// Dates are exchanged with the API as UTC calendar days (yyyy-mm-dd).
export const isoDate = (date) => date.toISOString().slice(0, 10);
export const today = () => isoDate(new Date());
export const currentMonth = () => today().slice(0, 7);
export const shiftMonth = (yearMonth, delta) => {
  const [year, month] = yearMonth.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1 + delta, 1));
  return date.toISOString().slice(0, 7);
};
export const lastDays = (days) => ({ from: isoDate(new Date(Date.now() - (days - 1) * 86400000)), to: today() });
export const monthToDate = () => ({ from: `${currentMonth()}-01`, to: today() });
export const lastMonths = (months) => ({ from: `${shiftMonth(currentMonth(), -(months - 1))}-01`, to: today() });
export const yearToDate = () => ({ from: `${today().slice(0, 4)}-01-01`, to: today() });

/** Every calendar day from..to, so a chart shows days without sales as zero instead of skipping them. */
export function eachDay(from, to) {
  const days = [];
  for (let d = new Date(`${from}T00:00:00Z`); d <= new Date(`${to}T00:00:00Z`); d = new Date(d.getTime() + 86400000)) {
    days.push(isoDate(d));
  }
  return days;
}

export const label = (value) => {
  if (!value) return "";
  const text = String(value).replaceAll("_", " ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
};

export const initials = (name = "") =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "?";
