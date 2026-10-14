import { useEffect, useState } from "react";
import { AlertCircle, AlertTriangle, CheckCircle2, Info, Loader2, Star, X } from "lucide-react";
import { initials, label } from "../format.js";

export function Spinner({ label: text = "Loading..." }) {
  return (
    <div className="spinner" role="status">
      <Loader2 className="spin" size={18} /> {text}
    </div>
  );
}

const ALERT_ICONS = { error: AlertCircle, success: CheckCircle2, info: Info, warning: AlertTriangle };

export function Alert({ kind = "error", children, onClose }) {
  if (!children) return null;
  const Icon = ALERT_ICONS[kind] || Info;
  return (
    <div className={`alert alert-${kind}`} role={kind === "error" ? "alert" : "status"}>
      <Icon size={16} />
      <span>{children}</span>
      {onClose && (
        <button className="icon-btn" onClick={onClose} aria-label="Dismiss">
          <X size={14} />
        </button>
      )}
    </div>
  );
}

const TONES = {
  PENDING: "amber",
  CONFIRMED: "blue",
  SHIPPED: "violet",
  DELIVERED: "green",
  CANCELLED: "red",
  SUCCESS: "green",
  FAILED: "red",
  REFUNDED: "gray",
  PAID: "green",
  UNPAID: "amber",
  PAY_ON_DELIVERY: "blue",
  VOIDED: "gray",
  ADMIN: "violet",
  MERCHANT: "blue",
  CUSTOMER: "gray",
  LISTED: "green",
  NOT_LISTED: "gray",
  LOW_STOCK: "amber",
  OUT_OF_STOCK: "red",
  ACTIVE: "green",
  SUSPENDED: "red",
};

const FRIENDLY = {
  PENDING: "Awaiting payment",
  CONFIRMED: "Confirmed",
  PAY_ON_DELIVERY: "Pay on delivery",
  UNPAID: "Unpaid",
};

/** Status pill. `friendly` turns order statuses into customer wording ("Awaiting payment"). */
export function Badge({ value, tone, text, friendly = false }) {
  if (value === null || value === undefined) return null;
  return <span className={`badge badge-${tone || TONES[value] || "gray"}`}>{text || (friendly && FRIENDLY[value]) || label(value)}</span>;
}

export function Modal({ title, onClose, children, wide }) {
  useEffect(() => {
    const onKey = (event) => event.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className={`modal ${wide ? "modal-wide" : ""}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

export function Empty({ icon: Icon, title, children, action }) {
  return (
    <div className="empty">
      {Icon && <Icon size={36} strokeWidth={1.5} />}
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button className="btn btn-ghost btn-sm" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        Previous
      </button>
      <span className="muted small">
        Page {page.page + 1} of {page.totalPages}
      </span>
      <button className="btn btn-ghost btn-sm" disabled={page.last} onClick={() => onChange(page.page + 1)}>
        Next
      </button>
    </div>
  );
}

export function Field({ label: text, error, hint, children, className = "" }) {
  return (
    <label className={`field ${className}`}>
      <span className="field-label">{text}</span>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

export function PageHeader({ title, subtitle, actions, back }) {
  return (
    <div className="stack tight">
      {back}
      <div className="page-head">
        <div>
          <h1>{title}</h1>
          {subtitle && <p className="muted">{subtitle}</p>}
        </div>
        {actions && <div className="row wrap">{actions}</div>}
      </div>
    </div>
  );
}

export function Tabs({ tabs, value, onChange }) {
  return (
    <div className="tabbar" role="tablist">
      {tabs.map((tab) => (
        <button key={tab.id} role="tab" aria-selected={value === tab.id} className={value === tab.id ? "active" : ""} onClick={() => onChange(tab.id)}>
          {tab.label}
          {tab.count !== undefined && tab.count !== null && <em>{tab.count}</em>}
        </button>
      ))}
    </div>
  );
}

export function Segmented({ options, value, onChange, ariaLabel }) {
  return (
    <div className="segmented" role="group" aria-label={ariaLabel}>
      {options.map((option) => (
        <button key={option.id} className={value === option.id ? "active" : ""} aria-pressed={value === option.id} onClick={() => onChange(option.id)}>
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function Switch({ checked, onChange, label: text, disabled }) {
  return (
    <label className="switch">
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />
      <span className="track" aria-hidden="true" />
      {text && <span>{text}</span>}
    </label>
  );
}

export function Stars({ value = 0, size = 15 }) {
  const rounded = Math.round(Number(value || 0));
  return (
    <span className="stars" aria-label={`${Number(value || 0).toFixed(1)} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Star key={n} size={size} className={n <= rounded ? "" : "off"} fill="currentColor" strokeWidth={0} />
      ))}
    </span>
  );
}

export function StarInput({ value, onChange }) {
  const [hover, setHover] = useState(0);
  return (
    <div className="star-input" onMouseLeave={() => setHover(0)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          type="button"
          key={n}
          className={n <= (hover || value) ? "on" : ""}
          onMouseEnter={() => setHover(n)}
          onClick={() => onChange(n)}
          aria-label={`${n} star${n > 1 ? "s" : ""}`}
        >
          <Star size={26} fill="currentColor" strokeWidth={0} />
        </button>
      ))}
    </div>
  );
}

export function StatTile({ label: text, value, hint, icon: Icon, hero }) {
  return (
    <div className={`stat ${hero ? "hero" : ""}`}>
      <span className="stat-label">
        {Icon && <Icon size={15} />} {text}
      </span>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  );
}

/** Product picture: the merchant's image URL, or a calm tile with the product's initials. */
export function ProductArt({ product, size = "", name, imageUrl, categoryId }) {
  const [failed, setFailed] = useState(false);
  const title = name || product?.name || product?.productName || "";
  const url = imageUrl ?? product?.imageUrl;
  const tint = Math.abs(Number(categoryId ?? product?.categoryId ?? title.length)) % 6;
  return (
    <div className={`art ${size} tint-${tint}`} aria-hidden="true">
      {url && !failed ? <img src={url} alt="" loading="lazy" onError={() => setFailed(true)} /> : <span>{initials(title)}</span>}
    </div>
  );
}

export function Avatar({ name, large }) {
  return <span className={`avatar ${large ? "lg" : ""}`}>{initials(name)}</span>;
}

export function StockNote({ available, low }) {
  if (available <= 0) return <span className="stock out">Out of stock</span>;
  if (low) return <span className="stock low">Only {available} left</span>;
  return <span className="stock">In stock</span>;
}

export function Qty({ value, onChange, max = 100, small }) {
  return (
    <div className={`qty ${small ? "sm" : ""}`}>
      <button type="button" onClick={() => onChange(value - 1)} disabled={value <= 1} aria-label="Decrease quantity">
        -
      </button>
      <span>{value}</span>
      <button type="button" onClick={() => onChange(value + 1)} disabled={value >= max} aria-label="Increase quantity">
        +
      </button>
    </div>
  );
}
