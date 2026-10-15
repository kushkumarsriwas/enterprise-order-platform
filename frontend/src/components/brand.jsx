import { useState } from "react";
import { Baby, BookOpen, Car, Dumbbell, HeartPulse, Laptop, Package, Shirt, ShoppingBasket, Smartphone, Sofa, Sparkles, Wrench } from "lucide-react";
import { imageUrl } from "../api.js";
import { formatAmount } from "../format.js";

/** The Nexora mark: an eight-point star (two turned squares) around a bloom dot. */
export function Mark({ size = 30 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" className="mark">
      <g transform="translate(32 32)">
        <rect x="-19" y="-19" width="38" height="38" rx="5" fill="var(--mark-fill, var(--brand))" />
        <rect x="-19" y="-19" width="38" height="38" rx="5" fill="var(--mark-fill, var(--brand))" transform="rotate(45)" />
        <circle r="8.5" fill="var(--mark-center, var(--surface))" />
        <circle r="4" fill="var(--mark-dot, var(--accent))" />
      </g>
    </svg>
  );
}

export function Logo({ suffix, size = 30, href = "#/" }) {
  return (
    <a href={href} className="logo" aria-label="Nexora home">
      <Mark size={size} />
      <span className="logo-word">nexora</span>
      {suffix && <span className="logo-suffix">{suffix}</span>}
    </a>
  );
}

const ICONS = {
  smartphone: Smartphone,
  laptop: Laptop,
  shirt: Shirt,
  sofa: Sofa,
  sparkles: Sparkles,
  "shopping-basket": ShoppingBasket,
  dumbbell: Dumbbell,
  "book-open": BookOpen,
  baby: Baby,
  car: Car,
  "heart-pulse": HeartPulse,
  wrench: Wrench,
};

const CATEGORY_ICON_BY_NAME = {
  Electronics: "smartphone",
  "Computers & Office": "laptop",
  Fashion: "shirt",
  "Home & Kitchen": "sofa",
  "Beauty & Personal Care": "sparkles",
  Grocery: "shopping-basket",
  "Sports & Outdoors": "dumbbell",
  "Books & Stationery": "book-open",
  "Toys & Baby": "baby",
  Automotive: "car",
  Health: "heart-pulse",
  "Tools & Hardware": "wrench",
};

export function CategoryIcon({ icon, name, size = 20 }) {
  const Icon = ICONS[icon] || ICONS[CATEGORY_ICON_BY_NAME[name]] || Package;
  return <Icon size={size} aria-hidden="true" />;
}

/** Six calm tints for products without a photo, picked from the category so a department looks consistent. */
const TINTS = ["#e3f1ef", "#f9e6ee", "#e6eefb", "#fbefdb", "#ece9fb", "#e6f4e6"];
const DARK_TINTS = ["#143532", "#3a1e2a", "#1b2a40", "#3a2d14", "#26223f", "#1b3220"];

export function tintFor(key = "") {
  let hash = 0;
  for (const char of String(key)) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return hash % TINTS.length;
}

/** Product photo, or a designed placeholder (department icon on a tint) when the seller has not uploaded one. */
export function ProductImage({ product, className = "", iconSize = 44 }) {
  const [failed, setFailed] = useState(false);
  const src = imageUrl(product);
  if (src && !failed) {
    return (
      <div className={`product-image ${className}`}>
        <img src={src} alt={product.name} loading="lazy" onError={() => setFailed(true)} />
      </div>
    );
  }
  const tint = tintFor(product?.categoryName || product?.categoryId || product?.name);
  return (
    <div className={`product-image placeholder ${className}`} style={{ "--tint": TINTS[tint], "--tint-dark": DARK_TINTS[tint] }} aria-hidden="true">
      <CategoryIcon name={product?.categoryName} size={iconSize} />
    </div>
  );
}

export function Price({ price, compareAtPrice, currency = "AED", size = "md" }) {
  const discounted = compareAtPrice && Number(compareAtPrice) > Number(price);
  const [whole, fraction] = formatAmount(price).split(".");
  return (
    <span className={`price price-${size}`}>
      <span className="price-now">
        <span className="price-currency">{currency}</span>
        <span className="price-whole">{whole}</span>
        <span className="price-fraction">.{fraction}</span>
      </span>
      {discounted && (
        <s className="price-was" aria-label={`was ${currency} ${formatAmount(compareAtPrice)}`}>
          {formatAmount(compareAtPrice)}
        </s>
      )}
    </span>
  );
}

/** The star lattice that sits behind the home page hero (the one decorative pattern in the product). */
export function StarLattice() {
  return (
    <svg className="lattice" aria-hidden="true" width="100%" height="100%">
      <defs>
        <pattern id="nexora-lattice" width="56" height="56" patternUnits="userSpaceOnUse">
          <g transform="translate(28 28)" fill="none" stroke="currentColor" strokeWidth="1">
            <rect x="-11" y="-11" width="22" height="22" />
            <rect x="-11" y="-11" width="22" height="22" transform="rotate(45)" />
          </g>
          <circle cx="0" cy="0" r="1.6" fill="currentColor" />
          <circle cx="56" cy="0" r="1.6" fill="currentColor" />
          <circle cx="0" cy="56" r="1.6" fill="currentColor" />
          <circle cx="56" cy="56" r="1.6" fill="currentColor" />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#nexora-lattice)" />
    </svg>
  );
}
