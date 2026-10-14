import { PackageSearch, Store } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatNumber } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import ProductCard from "../../components/ProductCard.jsx";
import { Alert, Avatar, Empty, Pagination, Spinner } from "../../components/ui.jsx";

const SORTS = [
  ["createdAt,desc", "Newest"],
  ["price,asc", "Price: low to high"],
  ["price,desc", "Price: high to low"],
  ["name,asc", "Name: A to Z"],
];

/** The marketplace: every listed product of every active store. Filters live in the URL so results can be shared. */
export default function ShopPage({ user, route, navigate, cart, toast }) {
  const q = route.query;
  const page = Number(q.page || 0);
  const canBuy = !user || user.role === "CUSTOMER";

  const go = (changes) => navigate(`/shop${qs({ ...q, page: 0, ...changes })}`, { keepScroll: true });

  const categories = useLoader(() => api("/api/categories", { auth: false }), []);
  const stores = useLoader(() => api("/api/merchants?size=8", { auth: false }), []);
  const products = useLoader(
    () =>
      api(
        `/api/products${qs({ search: q.q, categoryId: q.category, sort: q.sort || "createdAt,desc", inStockOnly: q.stock === "1", minPrice: q.min, maxPrice: q.max, page, size: 20 })}`,
        { auth: false }
      ),
    [q.q, q.category, q.sort, q.stock, q.min, q.max, page]
  );
  const filtered = Boolean(q.q || q.category || q.stock || q.min || q.max);
  const emptyMarketplace = !filtered && products.data?.totalElements === 0;
  const visibleCategories = (categories.data || []).filter((c) => c.productCount > 0 || String(c.id) === q.category);
  const storeCount = stores.data?.totalElements;

  return (
    <div className="stack" style={{ gap: 20 }}>
      <div className="shop-intro">
        <div>
          <h1>{q.q ? `Results for "${q.q}"` : "Shop the marketplace"}</h1>
          <p className="muted">
            {products.data && storeCount !== undefined && !emptyMarketplace
              ? `${formatNumber(products.data.totalElements)} product${products.data.totalElements === 1 ? "" : "s"}${filtered ? " found" : ""} from ${formatNumber(storeCount)} store${storeCount === 1 ? "" : "s"} across the UAE`
              : "Products from independent stores across the UAE"}
          </p>
        </div>
        <div className="toolbar" hidden={emptyMarketplace}>
          <select value={q.sort || "createdAt,desc"} onChange={(event) => go({ sort: event.target.value })} aria-label="Sort products">
            {SORTS.map(([value, text]) => (
              <option key={value} value={value}>
                {text}
              </option>
            ))}
          </select>
        </div>
      </div>

      {visibleCategories.length > 0 && (
        <div className="chips" role="list" aria-label="Categories">
          <button className={`chip ${!q.category ? "active" : ""}`} onClick={() => go({ category: "" })}>
            All categories
          </button>
          {visibleCategories.map((c) => (
            <button key={c.id} className={`chip ${String(c.id) === q.category ? "active" : ""}`} onClick={() => go({ category: c.id })}>
              {c.name} <em>{c.productCount}</em>
            </button>
          ))}
        </div>
      )}

      <div className="toolbar" hidden={emptyMarketplace}>
        <label className="check">
          <input type="checkbox" checked={q.stock === "1"} onChange={(event) => go({ stock: event.target.checked ? "1" : "" })} />
          In stock only
        </label>
        <form
          key={`${q.min || ""}-${q.max || ""}`}
          className="row"
          onSubmit={(event) => {
            event.preventDefault();
            const data = new FormData(event.currentTarget);
            go({ min: data.get("min") || "", max: data.get("max") || "" });
          }}
        >
          <span className="muted small">Price (AED)</span>
          <input className="price-input" name="min" type="number" min="0" placeholder="Min" defaultValue={q.min || ""} aria-label="Minimum price" />
          <input className="price-input" name="max" type="number" min="0" placeholder="Max" defaultValue={q.max || ""} aria-label="Maximum price" />
          <button className="btn btn-sm">Apply</button>
        </form>
        {filtered && (
          <button className="link" onClick={() => navigate("/shop")}>
            Clear filters
          </button>
        )}
      </div>

      <Alert>{products.error}</Alert>
      {products.loading && !products.data ? (
        <Spinner label="Loading products..." />
      ) : products.data?.content.length === 0 ? (
        filtered ? (
          <Empty icon={PackageSearch} title="No products match your search" action={<button className="btn" onClick={() => navigate("/shop")}>Clear filters</button>}>
            Try another word, category or price range.
          </Empty>
        ) : (
          <Empty
            icon={Store}
            title="No products are listed yet"
            action={
              !user ? (
                <a className="btn btn-primary" href="#/register?type=merchant">
                  Open a store
                </a>
              ) : null
            }
          >
            The marketplace shows real products listed by merchants. As soon as a store lists its first product, it appears here.
          </Empty>
        )
      ) : (
        <div className={`product-grid ${products.loading ? "refetching" : ""}`}>
          {products.data?.content.map((product) => (
            <ProductCard key={product.id} product={product} cart={cart} canBuy={canBuy} onAdded={(p) => toast.show(`${p.name} added to your cart`)} />
          ))}
        </div>
      )}
      <Pagination page={products.data} onChange={(p) => navigate(`/shop${qs({ ...q, page: p })}`)} />

      {!filtered && stores.data?.content.length > 0 && (
        <section className="stack" style={{ gap: 12 }}>
          <h2>Stores on Nexora</h2>
          <div className="stores-row">
            {stores.data.content.map((s) => (
              <a key={s.merchantId} className="store-chip" href={`#/store/${s.merchantId}`}>
                <Avatar name={s.storeName} />
                <div style={{ minWidth: 0 }}>
                  <strong className="clamp" style={{ WebkitLineClamp: 1 }}>
                    {s.storeName}
                  </strong>
                  <div className="muted small">{s.city}</div>
                </div>
              </a>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
