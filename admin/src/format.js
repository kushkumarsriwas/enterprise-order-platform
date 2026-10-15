const money = new Intl.NumberFormat("en-AE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const integer = new Intl.NumberFormat("en-AE");
const compactFormat = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

/** "AED 1,234.50"; a negative amount is written "-AED 280.90", the way statements show it. */
export const formatMoney = (amount, currency = "AED") => {
  const value = Number(amount || 0);
  return `${value < 0 ? "-" : ""}${currency} ${money.format(Math.abs(value))}`;
};
export const formatAmount = (amount) => money.format(Number(amount || 0));
export const formatNumber = (value) => integer.format(Number(value || 0));
export const compact = (value) => compactFormat.format(Number(value || 0));
export const compactMoney = (value) => `${Number(value) < 0 ? "-" : ""}AED ${compact(Math.abs(Number(value || 0)))}`;
export const formatPercent = (value, digits = 1) => (value === null || value === undefined ? "-" : `${Number(value).toFixed(digits)}%`);

export const formatDate = (iso) => (iso ? new Date(iso).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" }) : "-");
export const formatDay = (iso) => {
  if (!iso) return "-";
  const dateOnly = iso.length === 10;
  return new Date(dateOnly ? `${iso}T00:00:00Z` : iso).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: dateOnly ? "UTC" : undefined,
  });
};
export const shortDay = (isoDate) => new Date(`${isoDate}T00:00:00Z`).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });
export const monthLabel = (yearMonth) =>
  new Date(`${yearMonth}-01T00:00:00Z`).toLocaleDateString("en-GB", { month: "short", year: "numeric", timeZone: "UTC" });
export const longMonth = (yearMonth) => new Date(`${yearMonth}-01T00:00:00Z`).toLocaleDateString("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });

export const timeAgo = (iso) => {
  if (!iso) return "-";
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
  return new Date(Date.UTC(year, month - 1 + delta, 1)).toISOString().slice(0, 7);
};
export const daysAgo = (days) => isoDate(new Date(Date.now() - days * 86400000));
export const lastDays = (days) => ({ from: daysAgo(days - 1), to: today() });
export const monthToDate = () => ({ from: `${currentMonth()}-01`, to: today() });
export const lastMonths = (months) => ({ from: `${shiftMonth(currentMonth(), -(months - 1))}-01`, to: today() });
export const yearToDate = () => ({ from: `${today().slice(0, 4)}-01-01`, to: today() });

/** "SHIPPED" -> "Shipped", "PAY_ON_DELIVERY" -> "Pay on delivery". */
export const label = (value) => {
  if (!value) return "";
  const text = String(value).replaceAll("_", " ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
};

export const initials = (name = "") =>
  name
    .split(/[\s._-]+/)
    .filter((part) => /^[\p{L}\p{N}]/u.test(part))
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "?";

/**
 * Splits the delivery address stored on an order ("recipient, line 1, line 2, city, emirate, country (phone)")
 * into the address and the phone number, without repeating the recipient or a city that is also the emirate.
 */
export function deliveryAddress(order) {
  let text = order?.shippingAddress || "";
  const phone = text.match(/\(([^()]*)\)\s*$/)?.[1] || "";
  text = text.replace(/\s*\([^()]*\)\s*$/, "");
  const name = order?.recipientName;
  if (name && text.startsWith(`${name}, `)) text = text.slice(name.length + 2);
  const parts = text.split(", ").filter((part, index, all) => part && part !== all[index - 1]);
  return { address: parts.join(", "), phone };
}

export const plural = (count, word, many = `${word}s`) => `${formatNumber(count)} ${count === 1 ? word : many}`;

/** How a product is sold, from the unit symbol the API sends ("pc", "kg"...) or the unit name ("PIECE"). */
const UNIT_NAMES = {
  pc: "Single item",
  PIECE: "Single item",
  pack: "Pack",
  PACK: "Pack",
  box: "Box",
  BOX: "Box",
  ctn: "Carton",
  CARTON: "Carton",
  pair: "Pair",
  PAIR: "Pair",
  set: "Set",
  SET: "Set",
  kg: "Per kilogram",
  KG: "Per kilogram",
  g: "Per gram",
  GRAM: "Per gram",
  L: "Per litre",
  LITRE: "Per litre",
  m: "Per metre",
  METRE: "Per metre",
};
export const unitName = (unit) => UNIT_NAMES[unit] || label(unit);

/** Emirates of the UAE, used for delivery addresses and sales by region. */
export const EMIRATES = ["Abu Dhabi", "Dubai", "Sharjah", "Ajman", "Umm Al Quwain", "Ras Al Khaimah", "Fujairah"];
