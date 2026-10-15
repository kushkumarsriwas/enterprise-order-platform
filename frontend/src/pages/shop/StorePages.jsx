import { useEffect, useState } from "react";
import { BadgeCheck, CalendarDays, LayoutDashboard, MapPin, MessageSquare, SearchX, Store } from "lucide-react";
import { api, qs } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { ContactSeller, ProductCard } from "../../components/commerce.jsx";
import { Avatar, Button, Empty, ErrorState, Loader, Pagination, SearchInput, Select } from "../../components/ui.jsx";
import { formatNumber, initials, plural } from "../../format.js";
import { useDebounced, useLoader, useTitle } from "../../hooks.jsx";

const STORE_SORTS = [
  { value: "bestselling", label: "Best selling" },
  { value: "newest", label: "Newest first" },
  { value: "price_asc", label: "Price: low to high" },
  { value: "price_desc", label: "Price: high to low" },
  { value: "rating", label: "Customer rating" },
  { value: "discount", label: "Biggest discount" },
];
const PAGE_SIZE = 24;

const memberSince = (iso) => (iso ? new Date(iso).toLocaleDateString("en-GB", { month: "long", year: "numeric" }) : null);

/** A store's own shop front: who they are, their departments and everything they have listed. */
export function StorePage({ id, route, navigate }) {
  const { user, isSeller } = useAuth();
  const q = route.query;
  const [text, setText] = useState(q.q || "");
  const term = useDebounced(text.trim(), 400);
  const [contact, setContact] = useState(false);
  const store = useLoader(() => api(`/api/sellers/${id}`, { auth: false }), [id]);
  const sort = q.sort || "bestselling";
  const page = Number(q.page || 0);
  const query = { sellerId: id, q: q.q, categoryId: q.category, sort, page, size: PAGE_SIZE };
  const products = useLoader(() => api(`/api/products${qs(query)}`, { auth: false }), [JSON.stringify(query)]);
  const facets = useLoader(() => api(`/api/products/facets${qs({ sellerId: id, q: q.q })}`, { auth: false }).catch(() => null), [id, q.q]);
  useTitle(store.data?.storeName || "Store");

  const update = (changes) => {
    const next = { ...q, ...changes, page: changes.page ?? undefined };
    Object.keys(next).forEach((k) => (next[k] === "" || next[k] === undefined || next[k] === null) && delete next[k]);
    navigate(`/store/${id}${qs(next)}`, { replace: true, keepScroll: true });
  };

  useEffect(() => {
    if (term !== (q.q || "")) update({ q: term });
    // only the debounced search text should trigger this
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [term]);

  if (store.loading && !store.data) return <Loader text="Opening the store" />;
  if (store.error && !store.data) {
    return (
      <div className="panel">
        <Empty icon={Store} title="This store is not on Nexora" action={<Button onClick={() => navigate("/stores")}>See all stores</Button>}>
          {store.error}
        </Empty>
      </div>
    );
  }

  const s = store.data;
  const own = isSeller && user?.id === s.sellerId;
  const departments = facets.data?.categories || [];

  return (
    <div className="stack-lg">
      <section className="store-hero">
        <span className="store-mark" aria-hidden="true">
          {initials(s.storeName)}
        </span>
        <div>
          <h1>{s.storeName}</h1>
          <div className="store-meta">
            <span>
              <MapPin /> {[s.city, s.country].filter(Boolean).join(", ")}
            </span>
            {memberSince(s.memberSince) && (
              <span>
                <CalendarDays /> On Nexora since {memberSince(s.memberSince)}
              </span>
            )}
            {s.vatNumber && (
              <span>
                <BadgeCheck /> VAT registered, TRN {s.vatNumber}
              </span>
            )}
          </div>
          {s.description && <p className="lead">{s.description}</p>}
        </div>
        <div className="row row-wrap">
          {own ? (
            <Button icon={LayoutDashboard} onClick={() => navigate("/seller")}>
              Open Seller Central
            </Button>
          ) : (
            !isSeller && (
              <Button icon={MessageSquare} onClick={() => (user ? setContact(true) : navigate(`/signin${qs({ next: `/store/${id}` })}`))}>
                Message the store
              </Button>
            )
          )}
        </div>
      </section>

      <section className="stack">
        <div className="store-toolbar">
          <SearchInput value={text} onChange={setText} placeholder={`Search ${s.storeName}`} />
          <label className="row small soft">
            <span className="nowrap">Sort by</span>
            <Select
              className="select-auto"
              value={sort}
              options={STORE_SORTS}
              aria-label="Sort products"
              onChange={(event) => update({ sort: event.target.value })}
            />
          </label>
        </div>
        {departments.length > 1 && (
          <div className="dept-chips" role="group" aria-label="Departments in this store">
            <button type="button" aria-pressed={!q.category} onClick={() => update({ category: "" })}>
              Everything
            </button>
            {departments.map((d) => (
              <button key={d.value} type="button" aria-pressed={String(q.category) === String(d.value)} onClick={() => update({ category: d.value })}>
                {d.label} <span className="facet-count">{formatNumber(d.count)}</span>
              </button>
            ))}
          </div>
        )}

        {products.loading && !products.data ? (
          <Loader text="Loading products" />
        ) : products.error && !products.data ? (
          <ErrorState message={products.error} onRetry={products.reload} />
        ) : products.data.content.length === 0 ? (
          <div className="panel">
            <Empty icon={SearchX} title={q.q || q.category ? "Nothing matches" : "No products on sale yet"}>
              {q.q || q.category ? "Try another word or department." : `${s.storeName} has not listed products on the marketplace yet.`}
            </Empty>
          </div>
        ) : (
          <>
            <p className="small muted">{plural(products.data.totalElements, "product")}</p>
            <div className="product-grid" style={{ opacity: products.loading ? 0.6 : 1 }}>
              {products.data.content.map((product) => (
                <ProductCard key={product.id} product={product} showSeller={false} />
              ))}
            </div>
            <div className="panel">
              <Pagination
                page={page}
                totalPages={products.data.totalPages}
                totalElements={products.data.totalElements}
                size={PAGE_SIZE}
                onPage={(p) => {
                  update({ page: p });
                  window.scrollTo(0, 0);
                }}
              />
            </div>
          </>
        )}
      </section>
      {contact && <ContactSeller open onClose={() => setContact(false)} sellerId={s.sellerId} sellerName={s.storeName} />}
    </div>
  );
}

/** Directory of every store on the marketplace. */
export function StoresPage() {
  useTitle("Stores");
  const [text, setText] = useState("");
  const search = useDebounced(text.trim(), 300);
  const [paging, setPaging] = useState({ search: "", page: 0 });
  const page = paging.search === search ? paging.page : 0;
  const setPage = (p) => setPaging({ search, page: p });
  const stores = useLoader(() => api(`/api/sellers${qs({ search, page, size: 24 })}`, { auth: false }), [search, page]);

  return (
    <div className="stack-lg">
      <div className="page-title">
        <div>
          <h1>Stores on Nexora</h1>
          <p>Independent businesses from across the Emirates. Each one ships and invoices its own orders.</p>
        </div>
        <SearchInput value={text} onChange={setText} placeholder="Search stores by name or city" />
      </div>
      {stores.loading && !stores.data ? (
        <Loader text="Loading stores" />
      ) : stores.error && !stores.data ? (
        <ErrorState message={stores.error} onRetry={stores.reload} />
      ) : stores.data.content.length === 0 ? (
        <div className="panel">
          <Empty icon={Store} title={search ? "No store matches" : "No stores yet"}>
            {search ? "Try the store's city or another part of its name." : "Stores appear here as soon as they open on Nexora."}
          </Empty>
        </div>
      ) : (
        <>
          <div className="store-tiles" style={{ opacity: stores.loading ? 0.6 : 1 }}>
            {stores.data.content.map((s) => (
              <a key={s.sellerId} className="store-tile" href={`#/store/${s.sellerId}`}>
                <Avatar name={s.storeName} large />
                <div className="grow">
                  <b>{s.storeName}</b>
                  <p className="clamp-2">{s.description || `Selling from ${s.city || "the UAE"}`}</p>
                  <p>{[s.city, s.country].filter(Boolean).join(", ")}</p>
                </div>
              </a>
            ))}
          </div>
          <div className="panel">
            <Pagination page={page} totalPages={stores.data.totalPages} totalElements={stores.data.totalElements} size={24} onPage={setPage} />
          </div>
        </>
      )}
    </div>
  );
}
