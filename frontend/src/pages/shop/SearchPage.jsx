import { useState } from "react";
import { SearchX, SlidersHorizontal } from "lucide-react";
import { api, qs } from "../../api.js";
import { ProductCard } from "../../components/commerce.jsx";
import { Button, Checkbox, Drawer, Empty, ErrorState, FilterChips, Loader, Pagination, Select, Stars } from "../../components/ui.jsx";
import { formatNumber, plural } from "../../format.js";
import { useLoader, useTitle } from "../../hooks.jsx";

const SORTS = [
  { value: "relevance", label: "Best match" },
  { value: "bestselling", label: "Best selling" },
  { value: "price_asc", label: "Price: low to high" },
  { value: "price_desc", label: "Price: high to low" },
  { value: "rating", label: "Customer rating" },
  { value: "newest", label: "Newest first" },
  { value: "discount", label: "Biggest discount" },
];
const PAGE_SIZE = 24;

const list = (value) => (value ? String(value).split(",").filter(Boolean) : []);

/** Search results and department pages: the only place with filters (they make no sense on the home page). */
export function SearchPage({ route, navigate }) {
  const q = route.query;
  const filters = {
    q: q.q || "",
    categoryId: q.category || "",
    brand: list(q.brand),
    sellerId: list(q.seller),
    minPrice: q.min || "",
    maxPrice: q.max || "",
    minRating: q.rating || "",
    inStock: q.inStock === "true",
    deals: q.deals === "true",
  };
  const sort = q.sort || (filters.q ? "relevance" : "bestselling");
  const page = Number(q.page || 0);
  const apiQuery = { ...filters, sort, page, size: PAGE_SIZE };
  const key = JSON.stringify(apiQuery);
  const results = useLoader(() => api(`/api/products${qs(apiQuery)}`, { auth: false }), [key]);
  const facets = useLoader(() => api(`/api/products/facets${qs(filters)}`, { auth: false }), [JSON.stringify(filters)]);
  const categories = useLoader(() => api("/api/categories", { auth: false }), []);
  const [sheet, setSheet] = useState(false);

  /** Changes one URL parameter (and goes back to page 1). */
  const update = (changes) => {
    const next = { ...q, ...changes, page: undefined };
    Object.keys(next).forEach((k) => (next[k] === "" || next[k] === undefined || next[k] === null || next[k] === false) && delete next[k]);
    navigate(`/search${qs(next)}`, { keepScroll: true });
  };
  const toggleIn = (param, values, value) => {
    const set = new Set(values);
    if (set.has(value)) set.delete(value);
    else set.add(value);
    update({ [param]: [...set].join(",") });
  };

  const category = (categories.data || []).find((c) => String(c.id) === String(filters.categoryId));
  const title = filters.q ? `Results for "${filters.q}"` : category ? category.name : filters.deals ? "Today's deals" : "All products";
  useTitle(title);
  const sellerNames = Object.fromEntries((facets.data?.sellers || []).map((s) => [s.value, s.label]));

  const chips = [
    { key: "category", label: category?.name, value: filters.q ? category?.name : null, onRemove: () => update({ category: "" }) },
    ...filters.brand.map((b) => ({ key: `brand-${b}`, label: b, value: b, onRemove: () => toggleIn("brand", filters.brand, b) })),
    ...filters.sellerId.map((s) => ({
      key: `seller-${s}`,
      label: sellerNames[s] || `Store ${s}`,
      value: s,
      onRemove: () => toggleIn("seller", filters.sellerId, s),
    })),
    {
      key: "price",
      label: `AED ${filters.minPrice || 0} to ${filters.maxPrice || "any"}`,
      value: filters.minPrice || filters.maxPrice,
      onRemove: () => update({ min: "", max: "" }),
    },
    { key: "rating", label: `${filters.minRating} stars and up`, value: filters.minRating, onRemove: () => update({ rating: "" }) },
    { key: "inStock", label: "In stock", value: filters.inStock, onRemove: () => update({ inStock: "" }) },
    { key: "deals", label: "Deals", value: filters.deals && !!filters.q, onRemove: () => update({ deals: "" }) },
  ];

  const panel = (
    <FilterPanel
      filters={filters}
      facets={facets.data}
      categories={categories.data || []}
      onCategory={(id) => update({ category: id })}
      onToggleBrand={(b) => toggleIn("brand", filters.brand, b)}
      onToggleSeller={(s) => toggleIn("seller", filters.sellerId, s)}
      onPrice={(min, max) => update({ min, max })}
      onRating={(r) => update({ rating: r })}
      onFlag={(name, value) => update({ [name]: value ? "true" : "" })}
    />
  );

  return (
    <div className="search-layout">
      <aside className="filters" aria-label="Filters">
        {panel}
      </aside>
      <section>
        <div className="results-head">
          <div>
            <h1>{title}</h1>
            <p>
              {results.data ? plural(results.data.totalElements, "product") : "Searching"}
              {category && filters.q ? ` in ${category.name}` : ""}
            </p>
          </div>
          <div className="row">
            <Button className="mobile-filter-btn" icon={SlidersHorizontal} onClick={() => setSheet(true)}>
              Filters
            </Button>
            <label className="row small soft">
              <span className="nowrap">Sort by</span>
              <Select className="select-auto" value={sort} onChange={(event) => update({ sort: event.target.value })} options={SORTS} aria-label="Sort by" />
            </label>
          </div>
        </div>
        <div style={{ marginBottom: 16 }}>
          <FilterChips chips={chips} onClear={() => navigate(`/search${qs({ q: filters.q, category: filters.q ? "" : filters.categoryId })}`)} />
        </div>
        {results.loading && !results.data ? (
          <Loader text="Finding products" />
        ) : results.error && !results.data ? (
          <ErrorState message={results.error} onRetry={results.reload} />
        ) : results.data.content.length === 0 ? (
          <div className="panel">
            <Empty icon={SearchX} title="No products match" action={<Button onClick={() => navigate(`/search${qs({ q: filters.q })}`)}>Clear filters</Button>}>
              Try fewer filters, another spelling or a broader word.
            </Empty>
          </div>
        ) : (
          <>
            <div className="product-grid" style={{ opacity: results.loading ? 0.6 : 1 }}>
              {results.data.content.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
            <div className="panel" style={{ marginTop: 20 }}>
              <Pagination
                page={page}
                totalPages={results.data.totalPages}
                totalElements={results.data.totalElements}
                size={PAGE_SIZE}
                onPage={(p) => navigate(`/search${qs({ ...q, page: p })}`)}
              />
            </div>
          </>
        )}
      </section>
      <Drawer
        open={sheet}
        title="Filters"
        onClose={() => setSheet(false)}
        footer={
          <Button variant="primary" onClick={() => setSheet(false)}>
            Show {formatNumber(results.data?.totalElements || 0)} products
          </Button>
        }
      >
        <div className="filters-sheet">{panel}</div>
      </Drawer>
    </div>
  );
}

/** Min / max price inputs; remounted (key) whenever the price filter in the URL changes. */
function PriceRange({ minPrice, maxPrice, facets, onPrice }) {
  const [min, setMin] = useState(minPrice);
  const [max, setMax] = useState(maxPrice);
  return (
    <form
      className="price-range"
      onSubmit={(event) => {
        event.preventDefault();
        onPrice(min, max);
      }}
    >
      <input
        className="input input-sm"
        type="number"
        min="0"
        placeholder={facets?.minPrice ? `From ${Math.floor(facets.minPrice)}` : "Min"}
        value={min}
        onChange={(e) => setMin(e.target.value)}
        aria-label="Minimum price"
      />
      <input
        className="input input-sm"
        type="number"
        min="0"
        placeholder={facets?.maxPrice ? `To ${Math.ceil(facets.maxPrice)}` : "Max"}
        value={max}
        onChange={(e) => setMax(e.target.value)}
        aria-label="Maximum price"
      />
      <Button size="sm" type="submit">
        Go
      </Button>
    </form>
  );
}

function FilterPanel({ filters, facets, categories, onCategory, onToggleBrand, onToggleSeller, onPrice, onRating, onFlag }) {
  const [allBrands, setAllBrands] = useState(false);
  const departmentFacets = facets?.categories?.length
    ? facets.categories
    : categories.map((c) => ({ value: String(c.id), label: c.name, count: c.productCount }));
  const brands = facets?.brands || [];
  const shownBrands = allBrands ? brands : brands.slice(0, 10);
  return (
    <div>
      <div className="filter-group">
        <h3>Department</h3>
        <div className="filter-options" style={{ gap: 2 }}>
          {filters.categoryId && (
            <button type="button" className="facet-link" onClick={() => onCategory("")}>
              All departments
            </button>
          )}
          {departmentFacets.map((c) => (
            <button
              key={c.value}
              type="button"
              className={`facet-link ${String(c.value) === String(filters.categoryId) ? "active" : ""}`}
              onClick={() => onCategory(c.value)}
            >
              <span>{c.label}</span>
              {c.count !== undefined && <span className="facet-count">{formatNumber(c.count)}</span>}
            </button>
          ))}
        </div>
      </div>
      <div className="filter-group">
        <h3>Price (AED)</h3>
        <PriceRange key={`${filters.minPrice}|${filters.maxPrice}`} minPrice={filters.minPrice} maxPrice={filters.maxPrice} facets={facets} onPrice={onPrice} />
      </div>
      {brands.length > 0 && (
        <div className="filter-group">
          <h3>Brand</h3>
          <div className="filter-options">
            {shownBrands.map((b) => (
              <Checkbox key={b.value} checked={filters.brand.includes(b.value)} onChange={() => onToggleBrand(b.value)}>
                <span>{b.label}</span>
                <span className="facet-count">{formatNumber(b.count)}</span>
              </Checkbox>
            ))}
            {brands.length > 10 && (
              <button type="button" className="facet-link" onClick={() => setAllBrands((v) => !v)}>
                {allBrands ? "Show fewer brands" : `Show all ${brands.length} brands`}
              </button>
            )}
          </div>
        </div>
      )}
      {facets?.sellers?.length > 0 && (
        <div className="filter-group">
          <h3>Store</h3>
          <div className="filter-options">
            {facets.sellers.slice(0, 12).map((s) => (
              <Checkbox key={s.value} checked={filters.sellerId.includes(String(s.value))} onChange={() => onToggleSeller(String(s.value))}>
                <span>{s.label}</span>
                <span className="facet-count">{formatNumber(s.count)}</span>
              </Checkbox>
            ))}
          </div>
        </div>
      )}
      <div className="filter-group">
        <h3>Customer rating</h3>
        <div className="filter-options" style={{ gap: 2 }}>
          {[4, 3, 2, 1].map((stars) => {
            const facet = facets?.ratings?.find((r) => Number(r.value) === stars);
            return (
              <button
                key={stars}
                type="button"
                className={`facet-link ${String(filters.minRating) === String(stars) ? "active" : ""}`}
                onClick={() => onRating(String(filters.minRating) === String(stars) ? "" : String(stars))}
              >
                <span className="row" style={{ gap: 6 }}>
                  <Stars value={stars} /> and up
                </span>
                {facet && <span className="facet-count">{formatNumber(facet.count)}</span>}
              </button>
            );
          })}
        </div>
      </div>
      <div className="filter-group">
        <h3>Availability and offers</h3>
        <div className="filter-options">
          <Checkbox checked={filters.inStock} onChange={(v) => onFlag("inStock", v)}>
            <span>In stock now</span>
            {facets && <span className="facet-count">{formatNumber(facets.inStock)}</span>}
          </Checkbox>
          <Checkbox checked={filters.deals} onChange={(v) => onFlag("deals", v)}>
            <span>On sale</span>
            {facets && <span className="facet-count">{formatNumber(facets.deals)}</span>}
          </Checkbox>
        </div>
      </div>
    </div>
  );
}
