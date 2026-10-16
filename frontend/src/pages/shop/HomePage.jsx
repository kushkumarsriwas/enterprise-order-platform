import {
  ArrowUpRight,
  BadgePercent,
  Banknote,
  BarChart3,
  Boxes,
  ChevronRight,
  FileSpreadsheet,
  ReceiptText,
  ShieldCheck,
  Star,
  Store,
  Truck,
} from "lucide-react";
import { api, qs } from "../../api.js";
import { CategoryIcon, Price, ProductImage } from "../../components/brand.jsx";
import { ProductShelf } from "../../components/commerce.jsx";
import { Avatar, Badge, Button, Empty, ErrorState, Loader } from "../../components/ui.jsx";
import { formatNumber } from "../../format.js";
import { useCountUp, useLoader, useTilt, useTitle } from "../../hooks.jsx";

/** What every order on Nexora comes with, shown under the hero. */
const PERKS = [
  [Truck, "Free delivery", "Every store ships free across the UAE"],
  [Banknote, "Pay your way", "Card, instant bank transfer or cash on delivery"],
  [ShieldCheck, "Cancel for free", "Until the store ships your order"],
  [ReceiptText, "Tax invoice", "Every order invoiced with 5% VAT"],
];

/** Moving coloured light behind the first-look panels (hero, sell band, sign-in). Purely decorative. */
export function Aurora() {
  return (
    <div className="aurora" aria-hidden="true">
      <span />
      <span />
      <span />
    </div>
  );
}

/** A hero figure that counts up to its value when it appears. */
function Fact({ value, label }) {
  const shown = useCountUp(value == null ? "" : formatNumber(value), 1400);
  if (value == null) return null;
  return (
    <div className="hero-fact">
      <b>{shown}</b>
      <span>{label}</span>
    </div>
  );
}

export function HomePage() {
  useTitle(null);
  const tilt = useTilt();
  const home = useLoader(() => api("/api/products/home", { auth: false }), []);
  const stores = useLoader(() => api(`/api/sellers${qs({ size: 8 })}`, { auth: false }).catch(() => null), []);

  if (home.loading && !home.data) return <Loader text="Opening the marketplace" />;
  if (home.error && !home.data) return <ErrorState message={home.error} onRetry={home.reload} />;

  const { deals = [], bestSellers = [], newArrivals = [], topRated = [], categories = [] } = home.data || {};
  const pick = deals[0] || bestSellers[0] || newArrivals[0];
  const minis = [...bestSellers, ...newArrivals].filter((p) => p.id !== pick?.id).slice(0, 4);
  const productCount = categories.reduce((sum, c) => sum + (c.productCount || 0), 0);
  const empty = !pick;

  return (
    <div className="home">
      <section className="hero" ref={tilt}>
        <Aurora />
        <div className="hero-copy">
          <h1>Shop the independent stores of the UAE</h1>
          <p className="hero-lead">
            One cart for every store on Nexora. Each store ships your order and invoices you directly, with 5% VAT included in every price.
          </p>
          <div className="hero-cta">
            <a className="btn btn-lg btn-shine btn-hero" href={deals.length ? "#/search?deals=true" : "#/search"}>
              <BadgePercent /> {deals.length ? "Shop today's deals" : "Start shopping"}
            </a>
            <a className="btn btn-lg btn-hero-alt" href="#/seller/register">
              <Store /> Open your store
            </a>
          </div>
          <div className="hero-facts">
            <Fact value={stores.data?.totalElements} label={stores.data?.totalElements === 1 ? "independent store" : "independent stores"} />
            {productCount > 0 && <Fact value={productCount} label={productCount === 1 ? "product on sale" : "products on sale"} />}
            <Fact value={7} label="emirates, free delivery" />
          </div>
        </div>

        <div className="hero-stage">
          {pick ? (
            <a className="hero-pick" href={`#/product/${pick.id}`}>
              <ProductImage product={pick} iconSize={64} />
              <div className="hero-pick-info">
                <Badge tone={pick.discountPercent > 0 ? "solid-accent" : "brand"}>
                  {pick.discountPercent > 0 ? `Today's pick: ${pick.discountPercent}% off` : "Today's pick"}
                </Badge>
                <h3 className="clamp-2">{pick.name}</h3>
                <p className="tiny muted">Sold by {pick.sellerName}</p>
                <Price price={pick.price} compareAtPrice={pick.compareAtPrice} />
              </div>
              {pick.ratingCount > 0 && (
                <span className="hero-chip chip-rating">
                  <Star /> Rated {Number(pick.ratingAverage).toFixed(1)} by {formatNumber(pick.ratingCount)} {pick.ratingCount === 1 ? "customer" : "customers"}
                </span>
              )}
              <span className="hero-chip chip-delivery">
                <Truck /> Free delivery
              </span>
            </a>
          ) : (
            <div className="hero-pick hero-pick-empty">
              <div>
                <h3>The first stores are stocking their shelves</h3>
                <p className="small muted">Products appear here as soon as a store lists them for sale.</p>
                <Button variant="primary" onClick={() => (window.location.hash = "/seller/register")}>
                  Open your store
                </Button>
              </div>
            </div>
          )}
          {minis.length > 0 && (
            <div className="hero-mini">
              {minis.map((p) => (
                <a key={p.id} href={`#/product/${p.id}`}>
                  <ProductImage product={p} iconSize={22} />
                  <span className="grow">
                    <span className="name clamp-2">{p.name}</span>
                    <span className="p">AED {Number(p.price).toFixed(2)}</span>
                  </span>
                </a>
              ))}
            </div>
          )}
        </div>
      </section>

      <ul className="perks" aria-label="Why shop on Nexora">
        {PERKS.map(([Icon, title, text]) => (
          <li key={title}>
            <span className="perk-icon">
              <Icon />
            </span>
            <span>
              <b>{title}</b>
              <small>{text}</small>
            </span>
          </li>
        ))}
      </ul>

      {empty && (
        <div className="panel">
          <Empty icon={Store} title="Nothing on sale yet">
            Sellers add products in Seller Central and choose which ones to list on the marketplace.
          </Empty>
        </div>
      )}

      <ProductShelf title="Today's deals" sub="Price drops set by the stores themselves" products={deals} link="#/search?deals=true" />
      <ProductShelf title="Best sellers" sub="What customers across the Emirates buy most" products={bestSellers} link="#/search?sort=bestselling" />

      {categories.length > 0 && (
        <section className="shelf" aria-label="Departments">
          <div className="shelf-head">
            <h2>Shop by department</h2>
          </div>
          <div className="dept-grid">
            {categories.map((c) => (
              <a key={c.id} className="dept-tile" style={{ "--tile": `var(--series-${(c.id % 5) + 1})` }} href={`#/search?category=${c.id}`}>
                <span className="icon">
                  <CategoryIcon icon={c.icon} name={c.name} />
                </span>
                <span>
                  <b>{c.name}</b>
                  <br />
                  <small>{c.productCount ? `${formatNumber(c.productCount)} products` : "Coming soon"}</small>
                </span>
                <ArrowUpRight className="go" aria-hidden="true" />
              </a>
            ))}
          </div>
        </section>
      )}

      <ProductShelf title="New arrivals" products={newArrivals} link="#/search?sort=newest" />
      <ProductShelf title="Top rated" sub="Rated by customers after delivery" products={topRated} link="#/search?sort=rating" />

      {stores.data?.content?.length > 0 && (
        <section className="shelf" aria-label="Stores">
          <div className="shelf-head">
            <div>
              <h2>Stores to discover</h2>
              <p>{formatNumber(stores.data.totalElements)} independent businesses sell on Nexora</p>
            </div>
            <a className="see-all" href="#/stores">
              All stores <ChevronRight />
            </a>
          </div>
          <div className="store-tiles">
            {stores.data.content.map((s) => (
              <a key={s.sellerId} className="store-tile" href={`#/store/${s.sellerId}`}>
                <Avatar name={s.storeName} large />
                <div className="grow">
                  <b>{s.storeName}</b>
                  <p className="clamp-2">{s.description || `Selling from ${s.city || "the UAE"}`}</p>
                  <p>{s.city ? `${s.city}, ${s.country}` : s.country}</p>
                </div>
                <ArrowUpRight className="go" aria-hidden="true" />
              </a>
            ))}
          </div>
        </section>
      )}

      <section className="sell-band">
        <Aurora />
        <div>
          <h2>Run your store on Nexora</h2>
          <p>
            Seller Central keeps your whole inventory, including products you don't sell online. List what you want on the marketplace, ship orders and see your
            profit after costs, fees and expenses.
          </p>
          <ul>
            <li>
              <Boxes /> Stock across several warehouses
            </li>
            <li>
              <Truck /> Purchase orders and goods receipts
            </li>
            <li>
              <BarChart3 /> Sales, margin and profit &amp; loss
            </li>
            <li>
              <FileSpreadsheet /> Excel and PDF for every report
            </li>
          </ul>
        </div>
        <a className="btn btn-lg btn-shine btn-hero" href="#/seller/register">
          <Store /> Open your store
        </a>
      </section>
    </div>
  );
}
