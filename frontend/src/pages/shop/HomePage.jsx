import { Banknote, BarChart3, Boxes, FileSpreadsheet, ReceiptText, ShieldCheck, Store, Truck } from "lucide-react";
import { api, qs } from "../../api.js";
import { CategoryIcon, Price, ProductImage } from "../../components/brand.jsx";
import { ProductShelf } from "../../components/commerce.jsx";
import { Avatar, Badge, Button, Empty, ErrorState, Loader } from "../../components/ui.jsx";
import { formatNumber } from "../../format.js";
import { useLoader, useTitle } from "../../hooks.jsx";

/** What every order on Nexora comes with, shown under the hero. */
const PERKS = [
  [Truck, "Free delivery", "Every store ships free across the UAE"],
  [Banknote, "Pay your way", "Card, instant bank transfer or cash on delivery"],
  [ShieldCheck, "Cancel for free", "Until the store ships your order"],
  [ReceiptText, "Tax invoice", "Every order invoiced with 5% VAT"],
];

export function HomePage() {
  useTitle(null);
  const home = useLoader(() => api("/api/products/home", { auth: false }), []);
  const stores = useLoader(() => api(`/api/sellers${qs({ size: 8 })}`, { auth: false }).catch(() => null), []);

  if (home.loading && !home.data) return <Loader text="Opening the marketplace" />;
  if (home.error && !home.data) return <ErrorState message={home.error} onRetry={home.reload} />;

  const { deals = [], bestSellers = [], newArrivals = [], topRated = [], categories = [] } = home.data || {};
  const pick = deals[0] || bestSellers[0] || newArrivals[0];
  const minis = [...bestSellers, ...newArrivals].filter((p) => p.id !== pick?.id).slice(0, 4);
  const empty = !pick;

  return (
    <div className="home">
      <section className="hero">
        <div>
          <h1>Shop the independent stores of the UAE</h1>
          <p className="hero-lead">
            One cart for every store on Nexora. Each store ships your order and invoices you directly, with 5% VAT included in every price.
          </p>
          <div className="hero-chips">
            {[...categories]
              .sort((a, b) => (b.productCount || 0) - (a.productCount || 0))
              .slice(0, 6)
              .map((c) => (
                <a key={c.id} href={`#/search?category=${c.id}`}>
                  <CategoryIcon icon={c.icon} name={c.name} size={16} />
                  {c.name}
                </a>
              ))}
          </div>
        </div>
        <div className="hero-side">
          {pick ? (
            <a className="hero-pick" href={`#/product/${pick.id}`}>
              <ProductImage product={pick} iconSize={48} />
              <div>
                <span className="pick-label">
                  <Badge tone={pick.discountPercent > 0 ? "solid-accent" : "brand"}>
                    {pick.discountPercent > 0 ? `Today's pick: ${pick.discountPercent}% off` : "Today's pick"}
                  </Badge>
                </span>
                <h3 className="clamp-2">{pick.name}</h3>
                <p className="tiny muted" style={{ margin: "4px 0 10px" }}>
                  Sold by {pick.sellerName}
                </p>
                <Price price={pick.price} compareAtPrice={pick.compareAtPrice} />
              </div>
            </a>
          ) : (
            <div className="hero-pick" style={{ gridTemplateColumns: "1fr" }}>
              <div>
                <h3>The first stores are stocking their shelves</h3>
                <p className="small muted" style={{ margin: "6px 0 14px" }}>
                  Products appear here as soon as a store lists them for sale.
                </p>
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
              <a key={c.id} className="dept-tile" style={{ "--tile": `var(--series-${(c.id % 6) + 1})` }} href={`#/search?category=${c.id}`}>
                <span className="icon">
                  <CategoryIcon icon={c.icon} name={c.name} />
                </span>
                <span>
                  <b>{c.name}</b>
                  <br />
                  <small>{c.productCount ? `${formatNumber(c.productCount)} products` : "Coming soon"}</small>
                </span>
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
            <a href="#/stores">All stores</a>
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
              </a>
            ))}
          </div>
        </section>
      )}

      <section className="sell-band">
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
        <Button variant="primary" size="lg" onClick={() => (window.location.hash = "/seller/register")}>
          Open your store
        </Button>
      </section>
    </div>
  );
}
