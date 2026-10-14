import { useEffect, useState } from "react";
import { PackageSearch, Plus, Search } from "lucide-react";
import { api, qs } from "../api.js";
import { formatMoney } from "../format.js";
import { Alert, Empty, Pagination, Spinner } from "../components/ui.jsx";
import { useLoader } from "../hooks.jsx";

export default function CatalogPage({ cart, toast }) {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [inStockOnly, setInStockOnly] = useState(false);
  const [sort, setSort] = useState("name,asc");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(search);
      setPage(0);
    }, 350);
    return () => clearTimeout(timer);
  }, [search]);

  const categories = useLoader(() => api("/api/categories", { auth: false }), []);
  const products = useLoader(
    () => api(`/api/products${qs({ search: query, categoryId, inStockOnly, sort, page, size: 12 })}`, { auth: false }),
    [query, categoryId, inStockOnly, sort, page]
  );

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Catalogue</h1>
          <p className="muted">Prices in AED. Stock is reserved the moment you place an order.</p>
        </div>
      </div>

      <div className="toolbar">
        <div className="search">
          <Search size={16} />
          <input placeholder="Search name, SKU or description" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }}>
          <option value="">All categories</option>
          {(categories.data || []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.productCount})
            </option>
          ))}
        </select>
        <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(0); }}>
          <option value="name,asc">Name A-Z</option>
          <option value="price,asc">Price: low to high</option>
          <option value="price,desc">Price: high to low</option>
          <option value="createdAt,desc">Newest</option>
        </select>
        <label className="check">
          <input type="checkbox" checked={inStockOnly} onChange={(e) => { setInStockOnly(e.target.checked); setPage(0); }} />
          In stock only
        </label>
      </div>

      <Alert>{products.error}</Alert>
      {products.loading && !products.data ? (
        <Spinner label="Loading products..." />
      ) : products.data?.content.length === 0 ? (
        <Empty icon={PackageSearch} title="No products found">Try a different search or category.</Empty>
      ) : (
        <div className="product-grid">
          {products.data?.content.map((product) => {
            const inCart = cart.items.find((item) => item.productId === product.id)?.quantity || 0;
            const canAdd = product.availableQuantity > inCart;
            return (
              <article key={product.id} className="product-card">
                <div className="product-thumb">{product.name.slice(0, 1)}</div>
                <div className="product-meta">
                  <span className="muted small">{product.categoryName || "General"} · {product.sku}</span>
                  <h3>{product.name}</h3>
                  <p className="muted small clamp">{product.description}</p>
                </div>
                <div className="product-foot">
                  <div>
                    <strong>{formatMoney(product.price, product.currency)}</strong>
                    <div className={`stock ${product.availableQuantity === 0 ? "out" : product.lowStock ? "low" : ""}`}>
                      {product.availableQuantity === 0
                        ? "Out of stock"
                        : product.lowStock
                          ? `Only ${product.availableQuantity} left`
                          : `${product.availableQuantity} in stock`}
                    </div>
                  </div>
                  <button
                    className="btn btn-primary btn-sm"
                    disabled={!canAdd}
                    onClick={() => {
                      cart.add(product);
                      toast.show(`${product.name} added to cart`);
                    }}
                  >
                    <Plus size={14} /> {inCart ? `Add (${inCart})` : "Add"}
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
      <Pagination page={products.data} onChange={setPage} />
    </div>
  );
}
