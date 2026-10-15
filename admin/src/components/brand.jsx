import { imageUrl } from "../api.js";

/** The Nexora mark: an eight-point star (two turned squares). In the console its centre dot is saffron. */
export function Mark({ size = 26 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" className="mark">
      <g transform="translate(32 32)">
        <rect x="-19" y="-19" width="38" height="38" rx="5" fill="var(--mark-fill, var(--brand))" />
        <rect x="-19" y="-19" width="38" height="38" rx="5" fill="var(--mark-fill, var(--brand))" transform="rotate(45)" />
        <circle r="8.5" fill="var(--mark-center, var(--surface))" />
        <circle r="4" fill="var(--mark-dot, var(--saffron-400))" />
      </g>
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
