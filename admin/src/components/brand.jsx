import { imageUrl } from "../api.js";

/** The Nexora mark: a lowercase n on a rounded tile, with an accent dot. */
export function Mark({ size = 26 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" className="mark">
      <rect width="64" height="64" rx="16" fill="var(--mark-fill, var(--brand))" />
      <path
        d="M21 46V25h13a10 10 0 0 1 10 10v11"
        fill="none"
        stroke="var(--mark-ink, #ffffff)"
        strokeWidth="7.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="47" cy="17" r="5" fill="var(--mark-dot, var(--saffron-400))" />
    </svg>
  );
}

/** "nexora | Control Center" for the command bar and the sign-in page. */
export function ConsoleLogo({ href = "#/" }) {
  return (
    <a href={href} className="console-logo" aria-label="Nexora Control Center, overview">
      <Mark />
      <span className="console-logo-word">nexora</span>
      <span className="console-logo-area">Control Center</span>
    </a>
  );
}

/** Small product picture, or the first letter of the product when the store has not uploaded one. */
export function ProductThumb({ product, large }) {
  const src = imageUrl(product);
  return (
    <span className={`thumb ${large ? "thumb-lg" : ""}`} aria-hidden="true">
      {src ? <img src={src} alt="" loading="lazy" /> : <span className="thumb-letter">{(product?.name || "?").charAt(0)}</span>}
    </span>
  );
}
