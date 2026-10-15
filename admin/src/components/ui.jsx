import { useEffect, useId, useRef, useState } from "react";
import {
  ArrowDownRight,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Download,
  FileSpreadsheet,
  FileText,
  Inbox,
  LoaderCircle,
  RefreshCw,
  Search,
  Star,
  X,
} from "lucide-react";
import { download } from "../api.js";
import { useClickOutside, useToast } from "../hooks.jsx";
import { formatNumber, initials, label } from "../format.js";

export function Button({ variant = "secondary", size, block, icon: Icon, loading, children, className = "", ...props }) {
  const classes = ["btn", `btn-${variant}`, size && `btn-${size}`, block && "btn-block", className].filter(Boolean).join(" ");
  return (
    <button type="button" className={classes} disabled={loading || props.disabled} {...props}>
      {loading ? <LoaderCircle className="spin" /> : Icon ? <Icon /> : null}
      {children}
    </button>
  );
}

export function IconButton({ icon: Icon, label: text, count, className = "", ...props }) {
  return (
    <button type="button" className={`icon-btn ${className}`} aria-label={text} title={text} {...props}>
      <Icon />
      {count > 0 && <span className="dot-count">{count > 99 ? "99+" : count}</span>}
    </button>
  );
}

export function Field({ label: text, hint, error, children, className = "" }) {
  return (
    <label className={`field ${className}`}>
      {text && <span className="field-label">{text}</span>}
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

export const Input = ({ className = "", ...props }) => <input className={`input ${className}`} {...props} />;
export const Textarea = ({ className = "", ...props }) => <textarea className={`textarea ${className}`} {...props} />;

export function Select({ options = [], placeholder, className = "", ...props }) {
  return (
    <select className={`select ${className}`} {...props}>
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options.map((option) =>
        typeof option === "string" ? (
          <option key={option} value={option}>
            {label(option)}
          </option>
        ) : (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ),
      )}
    </select>
  );
}

export function SearchInput({ value, onChange, placeholder = "Search", className = "", autoFocus }) {
  return (
    <div className={`input-affix ${className}`}>
      <Search />
      <input
        className="input"
        type="search"
        value={value}
        placeholder={placeholder}
        aria-label={placeholder}
        autoFocus={autoFocus}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

export function MoneyInput({ value, onChange, ...props }) {
  return (
    <div className="input-affix has-text">
      <span className="affix">AED</span>
      <input
        className="input num"
        type="number"
        min="0"
        step="0.01"
        inputMode="decimal"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        {...props}
      />
    </div>
  );
}

export function Checkbox({ checked, onChange, children, ...props }) {
  return (
    <label className="check">
      <input type="checkbox" checked={!!checked} onChange={(event) => onChange(event.target.checked)} {...props} />
      {children}
    </label>
  );
}

export function Switch({ checked, onChange, children, disabled }) {
  return (
    <label className="switch">
      <input type="checkbox" role="switch" checked={!!checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />
      {children}
    </label>
  );
}

export function Badge({ tone = "neutral", dot, icon: Icon, children }) {
  return (
    <span className={`badge badge-${tone} ${dot ? "badge-dot" : ""}`}>
      {Icon && <Icon />}
      {children}
    </span>
  );
}

const STATUS_TONES = {
  // orders
  PENDING: ["amber", "Awaiting payment"],
  CONFIRMED: ["blue", "To ship"],
  SHIPPED: ["violet", "Shipped"],
  DELIVERED: ["green", "Delivered"],
  CANCELLED: ["neutral", "Cancelled"],
  // payment states and payments
  UNPAID: ["amber", "Unpaid"],
  PAID: ["green", "Paid"],
  PAY_ON_DELIVERY: ["blue", "Cash on delivery"],
  REFUNDED: ["neutral", "Refunded"],
  VOIDED: ["neutral", "Voided"],
  SUCCESS: ["green", "Successful"],
  FAILED: ["red", "Failed"],
  // stock
  IN_STOCK: ["green", "In stock"],
  LOW_STOCK: ["amber", "Low stock"],
  OUT_OF_STOCK: ["red", "Out of stock"],
  // purchasing and counts
  DRAFT: ["neutral", "Draft"],
  ORDERED: ["blue", "Ordered"],
  PARTIALLY_RECEIVED: ["amber", "Partly received"],
  RECEIVED: ["green", "Received"],
  CLOSED: ["neutral", "Closed"],
  IN_PROGRESS: ["blue", "In progress"],
  COMPLETED: ["green", "Completed"],
  // budgets
  ON_TRACK: ["green", "On track"],
  WARNING: ["amber", "Almost used"],
  OVER: ["red", "Over budget"],
  NO_BUDGET: ["neutral", "No budget"],
  // customers
  NEW: ["teal", "New"],
  REPEAT: ["blue", "Repeat"],
  VIP: ["bloom", "VIP"],
  AT_RISK: ["amber", "At risk"],
  // accounts
  ACTIVE: ["green", "Active"],
  SUSPENDED: ["red", "Suspended"],
  // e-mail outbox
  QUEUED: ["blue", "Queued"],
  SENT: ["green", "Sent"],
  // announcements
  LIVE: ["green", "Live"],
  SCHEDULED: ["blue", "Scheduled"],
  ENDED: ["neutral", "Ended"],
  OFF: ["neutral", "Switched off"],
  // conversations
  REPORTED: ["amber", "Reported"],
};

/** Status with a consistent colour and a readable label everywhere in the product. */
export function StatusBadge({ status, children }) {
  const [tone, text] = STATUS_TONES[status] || ["neutral", label(status)];
  return (
    <Badge tone={tone} dot>
      {children || text}
    </Badge>
  );
}

export const statusLabel = (status) => (STATUS_TONES[status] || [null, label(status)])[1];

export function Stars({ value = 0, size, showValue, count }) {
  const rounded = Math.round(Number(value) * 2) / 2;
  return (
    <span className="row" style={{ gap: 6 }}>
      <span className="stars" role="img" aria-label={`${Number(value).toFixed(1)} out of 5 stars`} style={size ? { "--s": size } : undefined}>
        {[1, 2, 3, 4, 5].map((n) => (
          <Star key={n} className={n <= rounded ? "" : "empty-star"} fill={n <= rounded ? "currentColor" : "none"} strokeWidth={n <= rounded ? 0 : 1.8} />
        ))}
      </span>
      {showValue && <span className="small strong">{Number(value).toFixed(1)}</span>}
      {count !== undefined && <span className="small muted">({formatNumber(count)})</span>}
    </span>
  );
}

export function Avatar({ name, large }) {
  return <span className={`avatar ${large ? "avatar-lg" : ""}`}>{initials(name)}</span>;
}

export function Stat({ label: text, value, icon: Icon, delta, deltaLabel = "vs previous period", hint, attention, upIsGood = true }) {
  const hasDelta = delta !== null && delta !== undefined;
  const up = hasDelta && Number(delta) >= 0;
  const good = up === upIsGood;
  return (
    <div className={`stat ${attention ? "stat-attention" : ""}`}>
      <div className="stat-label">
        {Icon && <Icon />}
        {text}
      </div>
      <div className="stat-value" title={typeof value === "string" ? value : undefined}>
        {typeof value === "string" && /^-?AED /.test(value) ? (
          <>
            {value.startsWith("-") ? "-" : ""}
            <span className="cur">AED</span>
            {value.replace(/^-?AED /, "")}
          </>
        ) : (
          value
        )}
      </div>
      {(hasDelta || hint) && (
        <div className="stat-foot">
          {hasDelta && (
            <span className={`delta ${good ? "delta-up" : "delta-down"}`}>
              {up ? <ArrowUpRight /> : <ArrowDownRight />}
              {Math.abs(Number(delta)).toFixed(1)}%
            </span>
          )}
          <span>{hasDelta ? deltaLabel : hint}</span>
        </div>
      )}
    </div>
  );
}

export function Panel({ title, sub, actions, children, flush, className = "", id }) {
  return (
    <section className={`panel ${className}`} id={id}>
      {(title || actions) && (
        <div className="panel-head">
          <div>
            {title && <h2>{title}</h2>}
            {sub && <div className="sub">{sub}</div>}
          </div>
          {actions && <div className="row row-wrap">{actions}</div>}
        </div>
      )}
      <div className={flush ? "panel-flush" : "panel-body"}>{children}</div>
    </section>
  );
}

export function Tabs({ tabs, value, onChange, className = "" }) {
  return (
    <div className={`tabs ${className}`} role="tablist">
      {tabs.map((tab) => (
        <button key={tab.id} type="button" role="tab" className="tab" aria-selected={value === tab.id} onClick={() => onChange(tab.id)}>
          {tab.icon && <tab.icon size={16} />}
          {tab.label}
          {tab.count !== undefined && tab.count !== null && <span className="count">{formatNumber(tab.count)}</span>}
        </button>
      ))}
    </div>
  );
}

export function PillToggle({ options, value, onChange, label: text }) {
  return (
    <div className="pill-toggle" role="group" aria-label={text}>
      {options.map((option) => (
        <button key={option.value} type="button" aria-pressed={value === option.value} onClick={() => onChange(option.value)}>
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, size, onPage }) {
  if (!totalPages || totalPages <= 1) {
    return totalElements ? <div className="pager">{formatNumber(totalElements)} in total</div> : null;
  }
  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <div className="pager">
      <span>
        {formatNumber(from)}-{formatNumber(to)} of {formatNumber(totalElements)}
      </span>
      <div className="pager-buttons">
        <Button size="sm" icon={ChevronLeft} disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
          <ChevronRight />
        </Button>
      </div>
    </div>
  );
}

export function Empty({ icon: Icon = Inbox, title, children, action }) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <Icon />
      </div>
      {title && <h3>{title}</h3>}
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function Loader({ text = "Loading" }) {
  return (
    <div className="loader" role="status">
      <LoaderCircle className="spin" />
      <span>{text}</span>
    </div>
  );
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="empty" role="alert">
      <div className="empty-icon" style={{ background: "var(--bad-soft)", color: "var(--bad)" }}>
        <CircleAlert />
      </div>
      <h3>This did not load</h3>
      <p>{message}</p>
      {onRetry && (
        <Button icon={RefreshCw} onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function Notice({ tone = "info", icon: Icon = CircleAlert, children }) {
  return (
    <div className={`notice notice-${tone}`}>
      <Icon />
      <div>{children}</div>
    </div>
  );
}

function useDialog(open, onClose) {
  const ref = useRef(null);
  useEffect(() => {
    if (!open) return undefined;
    const previous = document.activeElement;
    const onKey = (event) => event.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    const focusable = ref.current?.querySelector("input, select, textarea, button:not(.close-x)");
    (focusable || ref.current)?.focus?.();
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      previous?.focus?.();
    };
  }, [open, onClose]);
  return ref;
}

export function Modal({ open, title, sub, onClose, children, footer, size }) {
  const ref = useDialog(open, onClose);
  const titleId = useId();
  if (!open) return null;
  return (
    <div className="overlay" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div ref={ref} className={`modal ${size ? `modal-${size}` : ""}`} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <div className="modal-head">
          <div>
            <h2 id={titleId}>{title}</h2>
            {sub && <div className="sub">{sub}</div>}
          </div>
          <IconButton icon={X} label="Close" className="close-x" onClick={onClose} />
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Drawer({ open, title, sub, onClose, children, footer, wide }) {
  const ref = useDialog(open, onClose);
  const titleId = useId();
  if (!open) return null;
  return (
    <div className="overlay overlay-drawer" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <aside ref={ref} className={`drawer ${wide ? "drawer-wide" : ""}`} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <div className="modal-head">
          <div>
            <h2 id={titleId}>{title}</h2>
            {sub && <div className="sub">{sub}</div>}
          </div>
          <IconButton icon={X} label="Close" className="close-x" onClick={onClose} />
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </aside>
    </div>
  );
}

/** Confirmation for actions that can't be undone or affect money/stock. */
export function Confirm({ open, title, children, confirmLabel = "Confirm", danger, onConfirm, onClose, busy }) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Keep it
          </Button>
          <Button variant={danger ? "danger" : "primary"} loading={busy} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="soft">{children}</div>
    </Modal>
  );
}

export function Menu({ trigger, children, align = "right" }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useClickOutside(ref, () => setOpen(false), open);
  return (
    <div className="menu-anchor" ref={ref}>
      {trigger({ open, toggle: () => setOpen((value) => !value) })}
      {open && (
        <div className="menu" style={align === "left" ? { left: 0, right: "auto" } : undefined} onClick={() => setOpen(false)}>
          {children}
        </div>
      )}
    </div>
  );
}

/** "Export" menu: the same filtered list as Excel or PDF, downloaded with the user's token. */
export function ExportMenu({ path, query = "", label: text = "Export", size }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const run = async (format) => {
    setBusy(true);
    try {
      const name = await download(`${path.replace("{format}", format)}${query}`);
      toast(`Downloaded ${name}`);
    } catch (error) {
      toast(error.message, "bad");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Menu
      trigger={({ toggle }) => (
        <Button icon={Download} size={size} loading={busy} onClick={toggle}>
          {text}
        </Button>
      )}
    >
      <button type="button" className="menu-item" onClick={() => run("xlsx")}>
        <FileSpreadsheet /> Excel workbook (.xlsx)
      </button>
      <button type="button" className="menu-item" onClick={() => run("pdf")}>
        <FileText /> PDF document
      </button>
    </Menu>
  );
}

/** A one-click document download (invoice, packing slip, purchase order). */
export function DownloadButton({ path, children, icon = Download, ...props }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  return (
    <Button
      icon={icon}
      loading={busy}
      {...props}
      onClick={async () => {
        setBusy(true);
        try {
          const name = await download(path);
          toast(`Downloaded ${name}`);
        } catch (error) {
          toast(error.message, "bad");
        } finally {
          setBusy(false);
        }
      }}
    >
      {children}
    </Button>
  );
}

/** Data table driven by column definitions. */
export function DataTable({ columns, rows, rowKey = "id", onRowClick, empty, selectable, selected = [], onSelect, footer, sort, onSort }) {
  const allSelected = selectable && rows.length > 0 && rows.every((row) => selected.includes(row[rowKey]));
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {selectable && (
              <th className="check-cell">
                <input
                  type="checkbox"
                  aria-label="Select all"
                  checked={allSelected}
                  onChange={(event) =>
                    onSelect(
                      event.target.checked
                        ? [...new Set([...selected, ...rows.map((row) => row[rowKey])])]
                        : selected.filter((id) => !rows.some((row) => row[rowKey] === id)),
                    )
                  }
                />
              </th>
            )}
            {columns.map((column) => (
              <th key={column.key} className={column.align === "right" ? "num" : ""} style={{ width: column.width }}>
                {column.sortKey && onSort ? (
                  <button type="button" onClick={() => onSort(column.sortKey)}>
                    {column.header}
                    {sort === column.sortKey ? " ↓" : ""}
                  </button>
                ) : (
                  column.header
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const key = row[rowKey] ?? index;
            const isSelected = selectable && selected.includes(key);
            return (
              <tr
                key={key}
                className={`${onRowClick ? "clickable" : ""} ${isSelected ? "selected" : ""}`}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={onRowClick ? (event) => event.key === "Enter" && onRowClick(row) : undefined}
                tabIndex={onRowClick ? 0 : undefined}
              >
                {selectable && (
                  <td className="check-cell" onClick={(event) => event.stopPropagation()}>
                    <input
                      type="checkbox"
                      aria-label="Select row"
                      checked={isSelected}
                      onChange={(event) => onSelect(event.target.checked ? [...selected, key] : selected.filter((id) => id !== key))}
                    />
                  </td>
                )}
                {columns.map((column) => (
                  <td key={column.key} className={column.align === "right" ? "num" : ""}>
                    {column.render ? column.render(row) : row[column.key]}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
        {footer && <tfoot>{footer}</tfoot>}
      </table>
      {rows.length === 0 && (empty || <Empty title="Nothing here yet" />)}
    </div>
  );
}

/** Active filters shown as removable chips, with "Clear all". */
export function FilterChips({ chips, onClear }) {
  const visible = chips.filter(
    (chip) =>
      chip.value !== undefined && chip.value !== null && chip.value !== "" && chip.value !== false && !(Array.isArray(chip.value) && chip.value.length === 0),
  );
  if (visible.length === 0) return null;
  return (
    <div className="chips" aria-label="Active filters">
      {visible.map((chip) => (
        <span key={chip.key} className="chip">
          {chip.label}
          <button type="button" aria-label={`Remove ${chip.label}`} onClick={chip.onRemove}>
            <X />
          </button>
        </span>
      ))}
      {visible.length > 1 && (
        <Button size="sm" variant="ghost" onClick={onClear}>
          Clear all
        </Button>
      )}
    </div>
  );
}

export function Meter({ percent, tone }) {
  const value = Math.max(0, Math.min(100, Number(percent) || 0));
  return (
    <div className={`meter ${tone ? `meter-${tone}` : ""}`} role="meter" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}>
      <span style={{ width: `${value}%` }} />
    </div>
  );
}
