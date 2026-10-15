import { useEffect, useRef, useState } from "react";
import { ChevronLeft, Search, SlidersHorizontal } from "lucide-react";
import { api, qs } from "../../api.js";
import { Button, FilterChips, Input, Select } from "../../components/ui.jsx";
import { currentMonth, daysAgo, formatMoney, formatNumber, isoDate, lastDays, lastMonths, monthToDate, shiftMonth, today, yearToDate } from "../../format.js";
import { useClickOutside, useDebounced, useTitle } from "../../hooks.jsx";

export const AREA = "Nexora Seller Central";

/** Page title row: heading, one line of context and the page's actions. Also sets the browser tab title. */
export function PageHead({ title, sub, back, actions }) {
  useTitle(title, AREA);
  return (
    <>
      {back && (
        <a className="sc-back" href={`#${back.to}`}>
          <ChevronLeft /> {back.label}
        </a>
      )}
      <div className="sc-head">
        <div>
          <h1>{title}</h1>
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
 * Returns [values, set(changes), clear()]; changing a filter goes back to the first page.
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

/** A search box that writes to the URL a moment after the user stops typing. */
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
    if ((value || "") !== text.trim()) setText(value || "");
    // follow the URL when filters are cleared elsewhere
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

// ---------------------------------------------------------------- shared lists (warehouses, suppliers, departments)

const cache = new Map();

/** Small in-memory cache for drop-down data that rarely changes; invalidate() after creating or editing. */
function cached(key, loader, ttl = 60000) {
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < ttl) return hit.promise;
  const entry = { at: Date.now(), value: hit?.value };
  entry.promise = loader()
    .then((value) => {
      entry.value = value;
      return value;
    })
    .catch((error) => {
      cache.delete(key);
      throw error;
    });
  cache.set(key, entry);
  return entry.promise;
}

/** Marks a list as stale: the next page that needs it loads it again. */
export const invalidate = (key) => {
  const hit = cache.get(key);
  if (hit) hit.at = 0;
};

function useCachedList(key, path) {
  const [state, setState] = useState({ data: cache.get(key)?.value || [], loading: true });
  useEffect(() => {
    let alive = true;
    cached(key, () => api(path))
      .then((data) => alive && setState({ data, loading: false }))
      .catch(() => alive && setState({ data: [], loading: false }));
    return () => {
      alive = false;
    };
  }, [key, path]);
  return state.data;
}

export const useWarehouses = () => useCachedList("warehouses", "/api/seller/warehouses");
export const useSuppliers = () => useCachedList("suppliers", "/api/seller/suppliers/all");
export const useCategories = () => useCachedList("categories", "/api/categories");
export const useBrands = () => useCachedList("brands", "/api/seller/products/brands");

export const warehouseOptions = (warehouses, { activeOnly = true } = {}) =>
  warehouses
    .filter((w) => !activeOnly || w.active)
    .map((w) => ({ value: String(w.id), label: `${w.code} - ${w.name}${w.defaultWarehouse ? " (default)" : ""}` }));

export const defaultWarehouseId = (warehouses) =>
  String((warehouses.find((w) => w.defaultWarehouse && w.active) || warehouses.find((w) => w.active))?.id || "");

/** Searches the seller's products as you type; used wherever a product is picked (adjustments, purchase orders, transfers). */
export function ProductPicker({ onSelect, placeholder = "Search by name, SKU or barcode", exclude = [], autoFocus }) {
  const [text, setText] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const term = useDebounced(text.trim(), 250);
  const [results, setResults] = useState([]);
  const box = useRef(null);
  useClickOutside(box, () => setOpen(false), open);
  useEffect(() => {
    let alive = true;
    if (!term) {
      setResults([]);
      return undefined;
    }
    api(`/api/seller/products${qs({ search: term, size: 8 })}`)
      .then((page) => alive && setResults(page.content.filter((p) => !exclude.includes(p.id))))
      .catch(() => alive && setResults([]));
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [term]);
  const pick = (product) => {
    onSelect(product);
    setText("");
    setResults([]);
    setOpen(false);
  };
  return (
    <div className="picker" ref={box}>
      <div className="input-affix">
        <Search />
        <input
          className="input"
          value={text}
          placeholder={placeholder}
          aria-label={placeholder}
          autoFocus={autoFocus}
          onFocus={() => setOpen(true)}
          onChange={(event) => {
            setText(event.target.value);
            setOpen(true);
            setActive(0);
          }}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              setActive((i) => Math.min(i + 1, results.length - 1));
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              setActive((i) => Math.max(i - 1, 0));
            } else if (event.key === "Enter" && results[active]) {
              event.preventDefault();
              pick(results[active]);
            }
          }}
        />
      </div>
      {open && term && (
        <div className="picker-list" role="listbox">
          {results.length === 0 ? (
            <div className="small muted" style={{ padding: 10 }}>
              No product matches "{term}"
            </div>
          ) : (
            results.map((product, index) => (
              <button
                key={product.id}
                type="button"
                role="option"
                aria-selected={index === active}
                className={index === active ? "active" : ""}
                onClick={() => pick(product)}
              >
                <span className="grow">
                  <span className="ellipsis" style={{ display: "block" }}>
                    {product.name}
                  </span>
                  <span className="muted">
                    {product.sku} - {formatNumber(product.availableQuantity)} available - cost {formatMoney(product.costPrice)}
                  </span>
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}

/** A drop-down of checkboxes for filters that accept several values (statuses, payment methods, movement types). */
export function MultiSelect({ label: text, options, value = [], onChange }) {
  const [open, setOpen] = useState(false);
  const box = useRef(null);
  useClickOutside(box, () => setOpen(false), open);
  const toggle = (option) => onChange(value.includes(option) ? value.filter((v) => v !== option) : [...value, option]);
  const chosen = options.filter((o) => value.includes(o.value));
  return (
    <div className="menu-anchor" ref={box}>
      <button type="button" className="select select-sm multi-trigger" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="ellipsis">{chosen.length === 0 ? text : chosen.length === 1 ? chosen[0].label : `${text}: ${chosen.length}`}</span>
      </button>
      {open && (
        <div className="menu" style={{ left: 0, right: "auto", minWidth: 230 }} role="group" aria-label={text}>
          {options.map((option) => (
            <label key={option.value} className="menu-item">
              <input type="checkbox" checked={value.includes(option.value)} onChange={() => toggle(option.value)} />
              {option.label}
            </label>
          ))}
          {value.length > 0 && (
            <>
              <div className="menu-sep" />
              <button type="button" className="menu-item" onClick={() => onChange([])}>
                Clear
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
