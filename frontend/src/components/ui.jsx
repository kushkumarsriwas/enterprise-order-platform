import { useEffect } from "react";
import { AlertCircle, CheckCircle2, Loader2, X } from "lucide-react";

export function Spinner({ label = "Loading..." }) {
  return (
    <div className="spinner">
      <Loader2 className="spin" size={18} /> {label}
    </div>
  );
}

export function Alert({ kind = "error", children, onClose }) {
  if (!children) return null;
  const Icon = kind === "error" ? AlertCircle : CheckCircle2;
  return (
    <div className={`alert alert-${kind}`} role="alert">
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

const STATUS_TONE = {
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
  CUSTOMER: "blue",
};

export function Badge({ value, tone }) {
  return <span className={`badge badge-${tone || STATUS_TONE[value] || "gray"}`}>{String(value).replaceAll("_", " ")}</span>;
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

export function Empty({ icon: Icon, title, children }) {
  return (
    <div className="empty">
      {Icon && <Icon size={34} strokeWidth={1.5} />}
      <h3>{title}</h3>
      {children && <p>{children}</p>}
    </div>
  );
}

export function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button className="btn btn-ghost" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        Previous
      </button>
      <span>
        Page {page.page + 1} of {page.totalPages}
      </span>
      <button className="btn btn-ghost" disabled={page.last} onClick={() => onChange(page.page + 1)}>
        Next
      </button>
    </div>
  );
}

export function Field({ label, error, hint, children }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

