import { useState } from "react";
import {
  ChevronRight,
  CircleAlert,
  MessageSquare,
  PackageCheck,
  PackageX,
  Pencil,
  RotateCcw,
  ShieldCheck,
  ShoppingCart,
  Store,
  Truck,
  Zap,
} from "lucide-react";
import { api, qs } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { useCart } from "../../cart.js";
import { Price, ProductImage } from "../../components/brand.jsx";
import { ContactSeller, ProductShelf, QuantityStepper } from "../../components/commerce.jsx";
import { Badge, Button, Empty, Loader, Meter, Notice, Pagination, Select, Stars } from "../../components/ui.jsx";
import { compact, formatDay, formatMoney, formatNumber, plural, unitName } from "../../format.js";
import { useLoader, useTitle, useToast } from "../../hooks.jsx";

const REVIEW_SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "highest", label: "Highest rated" },
  { value: "lowest", label: "Lowest rated" },
  { value: "oldest", label: "Oldest first" },
];

export function ProductPage({ id, navigate }) {
  const product = useLoader(() => api(`/api/products/${id}`, { auth: false }), [id]);
  const related = useLoader(() => api(`/api/products/${id}/related`, { auth: false }).catch(() => null), [id]);
  useTitle(product.data?.name || "Product");

  if (product.loading && !product.data) return <Loader text="Loading the product" />;
  if (product.error && !product.data) {
    return (
      <div className="panel">
        <Empty
          icon={PackageX}
          title="This product is not available"
          action={
            <Button variant="primary" onClick={() => navigate("/")}>
              Back to the marketplace
            </Button>
          }
        >
          {product.error} The store may have stopped selling it.
        </Empty>
      </div>
    );
  }

  const p = product.data;
  const saving = p.compareAtPrice && Number(p.compareAtPrice) > Number(p.price) ? Number(p.compareAtPrice) - Number(p.price) : 0;
  const vat = (Number(p.price) * 5) / 105;

  return (
    <div className="stack-lg">
      <nav className="crumbs" aria-label="Breadcrumb">
        <a href="#/">Home</a>
        <ChevronRight aria-hidden="true" />
        {p.categoryId && (
          <>
            <a href={`#/search?category=${p.categoryId}`}>{p.categoryName}</a>
            <ChevronRight aria-hidden="true" />
          </>
        )}
        <span className="ellipsis" style={{ maxWidth: "40ch" }}>
          {p.name}
        </span>
      </nav>

      <div className="pdp">
        <div className="pdp-gallery">
          <ProductImage product={p} iconSize={110} />
        </div>

        <div className="pdp-info">
          {p.brand && (
            <a className="pdp-brand" href={`#/search${qs({ brand: p.brand })}`}>
              {p.brand}
            </a>
          )}
          <h1>{p.name}</h1>
          <div className="pdp-rating">
            {p.ratingCount > 0 ? (
              <button type="button" className="link-btn" onClick={() => document.getElementById("reviews")?.scrollIntoView({ behavior: "smooth" })}>
                <Stars value={p.ratingAverage} showValue count={p.ratingCount} />
              </button>
            ) : (
              <span className="muted">No reviews yet</span>
            )}
            {p.unitsSold > 0 && <span className="muted">{compact(p.unitsSold)} sold</span>}
          </div>

          <div className="pdp-price">
            <div className="row" style={{ gap: 10, alignItems: "center" }}>
              {p.discountPercent > 0 && <Badge tone="solid-bloom">-{p.discountPercent}%</Badge>}
              <Price price={p.price} compareAtPrice={p.compareAtPrice} currency={p.currency} size="lg" />
            </div>
            {saving > 0 && (
              <div className="small" style={{ marginTop: 6, color: "var(--accent-ink)", fontWeight: 650 }}>
                You save {formatMoney(saving, p.currency)}
              </div>
            )}
            <div className="vat">Includes 5% VAT ({formatMoney(vat, p.currency)}). Delivery is free.</div>
          </div>

          <dl className="dl" style={{ marginTop: 18 }}>
            {p.brand && (
              <>
                <dt>Brand</dt>
                <dd>{p.brand}</dd>
              </>
            )}
            <dt>Department</dt>
            <dd>{p.categoryName || "Other"}</dd>
            <dt>Sold as</dt>
            <dd>{unitName(p.unit)}</dd>
            <dt>Item code</dt>
            <dd className="num" style={{ textAlign: "left" }}>
              {p.sku}
            </dd>
            <dt>Sold by</dt>
            <dd>
              <a href={`#/store/${p.sellerId}`}>{p.sellerName}</a>
            </dd>
          </dl>

          {p.description && (
            <div className="pdp-desc">
              <h2 className="h-small">About this product</h2>
              {p.description}
            </div>
          )}
        </div>

        <BuyBox product={p} navigate={navigate} />
      </div>

      <Reviews product={p} />

      <ProductShelf title="Similar products" products={related.data?.similar} link={p.categoryId ? `#/search?category=${p.categoryId}` : null} />
      <ProductShelf
        title={`More from ${p.sellerName}`}
        products={related.data?.fromSameSeller?.filter((item) => !related.data.similar?.some((s) => s.id === item.id))}
        link={`#/store/${p.sellerId}`}
        linkLabel="Visit the store"
      />
    </div>
  );
}

function BuyBox({ product: p, navigate }) {
  const { user, isSeller } = useAuth();
  const cart = useCart();
  const toast = useToast();
  const [quantity, setQuantity] = useState(1);
  const [contact, setContact] = useState(false);
  const inCart = cart.quantityOf(p.id);
  const max = Math.max(1, Math.min(p.availableQuantity - inCart, 100));
  const ownProduct = isSeller && user?.id === p.sellerId;

  const addToCart = () => {
    cart.add(p, quantity);
    toast(`Added ${plural(quantity, "item")} to your cart`);
    setQuantity(1);
  };

  return (
    <aside className="buy-box" aria-label="Buy">
      <Price price={p.price} compareAtPrice={p.compareAtPrice} currency={p.currency} />
      {p.inStock ? (
        <div className="stock-line" style={{ color: p.fewLeft ? "var(--warn)" : "var(--good)" }}>
          <PackageCheck size={18} />
          {p.fewLeft ? `Only ${formatNumber(p.availableQuantity)} left` : "In stock"}
        </div>
      ) : (
        <div className="stock-line" style={{ color: "var(--bad)" }}>
          <PackageX size={18} />
          Out of stock
        </div>
      )}

      {isSeller ? (
        <Notice tone="info" icon={Store}>
          {ownProduct ? "This is one of your products." : "You are signed in with a store account. Customer accounts buy on Nexora."}
          {ownProduct && (
            <div style={{ marginTop: 8 }}>
              <Button size="sm" icon={Pencil} onClick={() => navigate(`/seller/products/${p.id}`)}>
                Edit in Seller Central
              </Button>
            </div>
          )}
        </Notice>
      ) : p.inStock ? (
        <>
          <div className="row-between">
            <QuantityStepper value={Math.min(quantity, max)} max={max} onChange={setQuantity} />
            {inCart > 0 && <span className="small muted">{formatNumber(inCart)} in your cart</span>}
          </div>
          <Button variant="primary" size="lg" block icon={ShoppingCart} disabled={inCart >= p.availableQuantity} onClick={addToCart}>
            Add to cart
          </Button>
          <Button
            variant="accent"
            size="lg"
            block
            icon={Zap}
            onClick={() => navigate(`/checkout${qs({ buy: p.id, qty: Math.min(quantity, p.availableQuantity) })}`)}
          >
            Buy now
          </Button>
          {inCart >= p.availableQuantity && (
            <p className="tiny muted" style={{ margin: 0 }}>
              <CircleAlert size={13} style={{ verticalAlign: "-2px" }} /> Your cart already holds all the stock this store has.
            </p>
          )}
        </>
      ) : (
        <p className="small soft" style={{ margin: 0 }}>
          The store has sold out. Message them to ask when it will be back.
        </p>
      )}

      <div className="stack-sm">
        <div className="delivery-line">
          <Truck />
          <span>Shipped by {p.sellerName} to any address in the UAE. Delivery is free.</span>
        </div>
        <div className="delivery-line">
          <ShieldCheck />
          <span>Pay by card, instant bank transfer or cash on delivery.</span>
        </div>
        <div className="delivery-line">
          <RotateCcw />
          <span>Cancel free of charge until the store ships your order.</span>
        </div>
      </div>

      <div className="sold-by">
        <span className="muted tiny">Sold and invoiced by</span>
        <a className="strong" href={`#/store/${p.sellerId}`}>
          {p.sellerName}
        </a>
        {!isSeller && (
          <Button
            size="sm"
            variant="ghost"
            icon={MessageSquare}
            style={{ justifySelf: "start", marginLeft: -10 }}
            onClick={() => (user ? setContact(true) : navigate(`/signin${qs({ next: `/product/${p.id}` })}`))}
          >
            Ask the store a question
          </Button>
        )}
      </div>
      {contact && <ContactSeller open onClose={() => setContact(false)} sellerId={p.sellerId} sellerName={p.sellerName} product={p} />}
    </aside>
  );
}

function Reviews({ product }) {
  const [rating, setRating] = useState(null);
  const [sort, setSort] = useState("newest");
  const [page, setPage] = useState(0);
  const reviews = useLoader(
    () => api(`/api/reviews${qs({ productId: product.id, rating, sort, page, size: 6 })}`, { auth: false }),
    [product.id, rating, sort, page],
  );
  const data = reviews.data;
  const total = data?.reviewCount || 0;

  return (
    <section className="panel" id="reviews" aria-label="Customer reviews">
      <div className="panel-body">
        <div className="reviews-layout">
          <div className="rating-summary stack">
            <h2>Customer reviews</h2>
            {data && total > 0 ? (
              <>
                <div className="row" style={{ gap: 12, alignItems: "center" }}>
                  <span className="big">{Number(data.averageRating).toFixed(1)}</span>
                  <div className="stack-sm" style={{ gap: 2 }}>
                    <Stars value={data.averageRating} />
                    <span className="small muted">{plural(total, "review")} from verified buyers</span>
                  </div>
                </div>
                <div>
                  {[5, 4, 3, 2, 1].map((stars) => {
                    const count = Number(data.distribution?.[stars] || 0);
                    const percent = total ? Math.round((count / total) * 100) : 0;
                    return (
                      <button
                        key={stars}
                        type="button"
                        className={`dist-row ${rating === stars ? "active" : ""}`}
                        aria-pressed={rating === stars}
                        disabled={count === 0}
                        onClick={() => {
                          setRating(rating === stars ? null : stars);
                          setPage(0);
                        }}
                      >
                        <span>{stars} star</span>
                        <Meter percent={percent} />
                        <span className="num small muted">{percent}%</span>
                      </button>
                    );
                  })}
                </div>
              </>
            ) : (
              <p className="small muted">Customers can review a product after the store delivers it.</p>
            )}
          </div>

          <div>
            {total > 0 && (
              <div className="row-between" style={{ marginBottom: 14 }}>
                <span className="small muted">
                  {rating ? `Showing ${rating}-star reviews` : `Showing all ${plural(total, "review")}`}
                  {rating && (
                    <button type="button" className="link-btn" style={{ marginLeft: 8 }} onClick={() => setRating(null)}>
                      Show all
                    </button>
                  )}
                </span>
                <Select
                  value={sort}
                  options={REVIEW_SORTS}
                  aria-label="Sort reviews"
                  className="select-sm"
                  onChange={(event) => {
                    setSort(event.target.value);
                    setPage(0);
                  }}
                />
              </div>
            )}
            {reviews.loading && !data ? (
              <Loader text="Loading reviews" />
            ) : data?.reviews?.content?.length ? (
              <div style={{ opacity: reviews.loading ? 0.6 : 1 }}>
                {data.reviews.content.map((r) => (
                  <article key={r.id} className="review">
                    <div className="row row-wrap" style={{ gap: 10 }}>
                      <Stars value={r.rating} />
                      <span className="strong small">{r.customerName}</span>
                      {r.verifiedPurchase && (
                        <Badge tone="teal" icon={PackageCheck}>
                          Verified purchase
                        </Badge>
                      )}
                      <span className="tiny muted">{formatDay(r.createdAt)}</span>
                    </div>
                    {r.comment && <p>{r.comment}</p>}
                    {r.sellerReply && (
                      <div className="seller-reply">
                        <div className="tiny strong" style={{ marginBottom: 4 }}>
                          Reply from {r.sellerName}
                          {r.sellerRepliedAt && <span className="muted"> on {formatDay(r.sellerRepliedAt)}</span>}
                        </div>
                        {r.sellerReply}
                      </div>
                    )}
                  </article>
                ))}
                <Pagination page={page} totalPages={data.reviews.totalPages} totalElements={data.reviews.totalElements} size={6} onPage={setPage} />
              </div>
            ) : total > 0 ? (
              <p className="small muted">No reviews with this rating.</p>
            ) : (
              <Empty title="No reviews yet">Be the first: after delivery you can rate this product from your orders.</Empty>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
