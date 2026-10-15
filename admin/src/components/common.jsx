import { useEffect, useRef, useState } from "react";
import { ChevronLeft, Search, SlidersHorizontal } from "lucide-react";
import { api, qs } from "../api.js";
import { Button, FilterChips, Input, Select } from "./ui.jsx";
import { currentMonth, daysAgo, isoDate, lastDays, lastMonths, monthToDate, shiftMonth, today, yearToDate } from "../format.js";
import { useDebounced, useLoader, useTitle } from "../hooks.jsx";

/** Page title row: heading, one line of context and the page's actions. Also sets the browser tab title. */
export function PageHead({ title, sub, back, actions, badges }) {
  useTitle(typeof title === "string" ? title : null);
  return (
    <>
      {back && (
        <a className="cc-back" href={`#${back.to}`}>
          <ChevronLeft /> {back.label}
        </a>
      )}
      <div className="cc-head">
        <div className="cc-head-text">
          <div className="row row-wrap" style={{ gap: 10 }}>
            <h1>{title}</h1>
            {badges}
          </div>
          {sub && <p>{sub}</p>}
        </div>
        {actions && <div className="actions">{actions}</div>}
      </div>
    </>
  );
}

/** "A,B" in the URL <-> ["A", "B"] for multi-value filters. */
export const listOf = (value) => (value ? String(value).split(",").filter(Boolean) : []);

/**
 * Filters kept in the URL, so a filtered list can be bookmarked, shared and survives a reload.
 * Returns [values, set(changes), clear(keep)]; changing a filter goes back to the first page.
 */
export function useFilters(route, navigate, defaults = {}) {
  const values = { ...defaults, ...route.query };
  const go = (query) => {
    const next = { ...query };
    Object.keys(next).forEach((key) => {
      const value = next[key];
      if (
        value === "" ||
        value === undefined ||
        value === null ||
        value === false ||
        String(value) === String(defaults[key] ?? "") ||
        (key === "page" && Number(value) === 0)
      ) {
        delete next[key];
      }
    });
    navigate(`${route.path}${qs(next)}`, { replace: true, keepScroll: true });
  };
  const set = (changes) => {
    const next = { ...route.query, ...changes };
    if (!("page" in changes)) delete next.page;
    go(next);
  };
  const clear = (keep = []) => go(Object.fromEntries(keep.filter((key) => route.query[key] !== undefined).map((key) => [key, route.query[key]])));
  return [values, set, clear];
}

/** A search box that writes to the URL a moment after the administrator stops typing. */
export function DebouncedSearch({ value, onChange, placeholder }) {
  const [text, setText] = useState(value || "");
  const debounced = useDebounced(text, 350);
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    if (debounced.trim() !== (value || "")) onChange(debounced.trim());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);
  useEffect(() => {
    // follow the URL when filters are cleared elsewhere
    if ((value || "") !== text.trim()) setText(value || "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);
  return (
    <div className="input-affix">
      <Search />
      <input
        className="input"
        type="search"
        value={text}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(event) => setText(event.target.value)}
      />
    </div>
  );
}

/** Toolbar above a table: search and the main filters on one row, the rest behind "More filters". */
export function Toolbar({ children, more, moreCount = 0, right }) {
  const [open, setOpen] = useState(moreCount > 0);
  return (
    <>
      <div className="toolbar">
        {children}
        {more && (
          <Button size="sm" variant={open ? "soft" : "ghost"} icon={SlidersHorizontal} onClick={() => setOpen((v) => !v)} aria-expanded={open}>
            More filters{moreCount > 0 ? ` (${moreCount})` : ""}
          </Button>
        )}
        <span className="spacer" />
        {right && <div className="toolbar-right">{right}</div>}
      </div>
      {more && open && <div className="more-filters">{more}</div>}
    </>
  );
}

export function ActiveFilters({ chips, onClear }) {
  const any = chips.some((chip) => chip.value !== undefined && chip.value !== null && chip.value !== "" && chip.value !== false);
  if (!any) return null;
  return (
    <div className="toolbar-chips">
      <FilterChips chips={chips} onClear={onClear} />
    </div>
  );
}

export const PERIODS = [
  { value: "7d", label: "Last 7 days", range: () => lastDays(7) },
  { value: "30d", label: "Last 30 days", range: () => lastDays(30) },
  { value: "90d", label: "Last 90 days", range: () => lastDays(90) },
  { value: "mtd", label: "This month", range: monthToDate },
  {
    value: "lastMonth",
    label: "Last month",
    range: () => {
      const month = shiftMonth(currentMonth(), -1);
      const end = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0));
      return { from: `${month}-01`, to: isoDate(end) };
    },
  },
  { value: "6m", label: "Last 6 months", range: () => lastMonths(6) },
  { value: "12m", label: "Last 12 months", range: () => ({ from: daysAgo(364), to: today() }) },
  { value: "ytd", label: "This year", range: yearToDate },
];

/** Which preset (if any) a from/to pair corresponds to. */
export const presetOf = (from, to) =>
  PERIODS.find((p) => {
    const range = p.range();
    return range.from === from && range.to === to;
  })?.value || "custom";

/** Name of a period for sentences ("last 30 days", "1 Sept to 15 Oct 2026"). */
export function periodName(from, to) {
  if (!from && !to) return "all time";
  const preset = PERIODS.find((p) => p.value === presetOf(from, to));
  if (preset) return preset.label.charAt(0).toLowerCase() + preset.label.slice(1);
  const day = (iso) => new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
  return `${day(from)} to ${day(to)}`;
}

/** Period filter: presets plus custom dates. Empty from/to means "any date" when allowEmpty is set. */
export function PeriodPicker({ from, to, onChange, allowEmpty, label = "Period" }) {
  const preset = !from && !to ? "" : presetOf(from, to);
  return (
    <div className="period" role="group" aria-label={label}>
      <Select
        className="select-auto"
        value={preset}
        aria-label={label}
        onChange={(event) => {
          const value = event.target.value;
          if (value === "") onChange({ from: "", to: "" });
          else if (value !== "custom") onChange(PERIODS.find((p) => p.value === value).range());
          else onChange({ from: from || daysAgo(29), to: to || today() });
        }}
        options={[
          ...(allowEmpty ? [{ value: "", label: "Any date" }] : []),
          ...PERIODS.map((p) => ({ value: p.value, label: p.label })),
          { value: "custom", label: "Custom dates" },
        ]}
      />
      {preset === "custom" && (
        <>
          <Input type="date" value={from || ""} max={to || today()} aria-label="From" onChange={(event) => onChange({ from: event.target.value, to })} />
          <span className="muted small">to</span>
          <Input
            type="date"
            value={to || ""}
            min={from || undefined}
            max={today()}
            aria-label="To"
            onChange={(event) => onChange({ from, to: event.target.value })}
          />
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- lists for filter drop-downs

const cache = new Map();

function cached(key, loader, ttl = 60000) {
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < ttl) return hit.promise;
  const promise = loader().catch((error) => {
    cache.delete(key);
    throw error;
  });
  cache.set(key, { at: Date.now(), promise });
  return promise;
}

/** Every store (seller account) on the marketplace, for "Store" filters. */
export function useStores() {
  return useLoader(() => cached("stores", () => api(`/api/admin/users${qs({ role: "SELLER", sort: "name", size: 100 })}`).then((page) => page.content)), []);
}

/** Marketplace departments. */
export function useDepartments() {
  return useLoader(() => cached("departments", () => api("/api/categories")), []);
}

export const storeName = (user) => user?.store?.storeName || user?.fullName || user?.username;

export const storeOptions = (stores, placeholder = "All stores") => [
  { value: "", label: placeholder },
  ...(stores || []).map((s) => ({ value: String(s.id), label: storeName(s) })),
];

/** A labelled value in a detail panel ("Customer since: 4 May 2026"). */
export function KeyValues({ items }) {
  return (
    <dl className="kv-list">
      {items
        .filter((item) => item && item.value !== undefined)
        .map((item) => (
          <div key={item.label} className={item.total ? "total" : ""}>
            <dt>{item.label}</dt>
            <dd>{item.value ?? "-"}</dd>
          </div>
        ))}
    </dl>
  );
}

/** Link to the marketplace (separate web application), e.g. a store's public page. */
export const MARKETPLACE_URL = (import.meta.env.VITE_MARKETPLACE_URL || "http://localhost:5173").replace(/\/$/, "");
