import { useState } from "react";
import { ArrowLeft, MessageSquare, ShieldCheck, ShoppingCart, Store, Truck } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatAmount, formatDay } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import ContactSeller from "../../components/ContactSeller.jsx";
import ProductCard from "../../components/ProductCard.jsx";
import { Alert, Avatar, Empty, Pagination, ProductArt, Qty, Spinner, Stars, StockNote } from "../../components/ui.jsx";

export default function ProductPage({ id, user, navigate, cart, toast }) {
  const product = useLoader(() => api(`/api/products/${id}`, { auth: false }), [id]);
  const [reviewPage, setReviewPage] = useState(0);
  const reviews = useLoader(() => api(`/api/reviews${qs({ productId: id, page: reviewPage, size: 5 })}`, { auth: false }), [id, reviewPage]);
  const [quantity, setQuantity] = useState(1);
  const [contact, setContact] = useState(false);
  const p = product.data;
  const store = useLoader(() => (p ? api(`/api/merchants/${p.merchantId}`, { auth: false }) : Promise.resolve(null)), [p?.merchantId]);
  const more = useLoader(
    () => (p ? api(`/api/products${qs({ merchantId: p.merchantId, size: 5 })}`, { auth: false }) : Promise.resolve(null)),
    [p?.merchantId]
  );

  if (product.loading && !p) return <Spinner />;
  if (product.error)
    return (
      <Empty icon={Store} title="This product is not available" action={<button className="btn" onClick={() => navigate("/shop")}>Back to the marketplace</button>}>
        It may have been removed or is no longer listed by the seller.
      </Empty>
    );

  const canBuy = !user || user.role === "CUSTOMER";
  const inCart = cart.quantityOf(p.id);
  const maxQty = Math.max(0, Math.min(p.availableQuantity - inCart, 100));
  const r = reviews.data;
  const others = (more.data?.content || []).filter((item) => item.id !== p.id).slice(0, 4);

  const addToCart = (goToCart) => {
    cart.add(p, quantity);
    if (goToCart) navigate("/cart");
    else toast.show(`${quantity} x ${p.name} added to your cart`);
    setQuantity(1);
  };
  const startChat = () => (user ? setContact(true) : navigate(`/login?next=${encodeURIComponent(`/product/${p.id}`)}`));

  return (
    <div className="stack" style={{ gap: 28 }}>
      <a className="back" href="#/shop">
        <ArrowLeft size={15} /> Marketplace
      </a>
      <div className="pdp">
        <ProductArt product={p} size="lg" />
        <div className="pdp-info">
          <span className="muted">{p.categoryName || "General"}</span>
          <h1>{p.name}</h1>
          <div className="row wrap">
            {r && r.reviewCount > 0 ? (
              <a className="rating link" href="#reviews" onClick={(e) => { e.preventDefault(); document.getElementById("reviews")?.scrollIntoView({ behavior: "smooth" }); }}>
                <Stars value={r.averageRating} /> <span>{Number(r.averageRating).toFixed(1)} ({r.reviewCount} review{r.reviewCount === 1 ? "" : "s"})</span>
              </a>
            ) : (
              <span className="muted small">No reviews yet</span>
            )}
            <span className="muted small">SKU {p.sku}</span>
          </div>
          <div className="pdp-price">
            <small>AED</small>
            {formatAmount(p.price)}
          </div>
          <StockNote available={p.availableQuantity} low={p.lowStock} />
          {canBuy ? (
            <div className="buy-box">
              {maxQty > 0 && <Qty value={Math.min(quantity, maxQty)} max={maxQty} onChange={setQuantity} />}
              <button className="btn btn-primary btn-lg" disabled={maxQty <= 0} onClick={() => addToCart(false)}>
                <ShoppingCart size={17} /> Add to cart
              </button>
              <button className="btn btn-lg" disabled={maxQty <= 0} onClick={() => addToCart(true)}>
                Buy now
              </button>
            </div>
          ) : (
            <Alert kind="info">Sign in with a customer account to buy this product.</Alert>
          )}
          {inCart > 0 && (
            <p className="muted small">
              {inCart} already in your cart. <a className="link" href="#/cart">View cart</a>
            </p>
          )}
          <div className="stack tight">
            <span className="info-row">
              <Truck size={15} /> Delivered by the seller with courier tracking
            </span>
            <span className="info-row">
              <ShieldCheck size={15} /> Card, UPI or cash on delivery. Cancel free of charge before it ships.
            </span>
          </div>
          {p.description && (
            <div className="card">
              <h3>About this product</h3>
              <p style={{ whiteSpace: "pre-wrap" }}>{p.description}</p>
            </div>
          )}
          <div className="card seller-card">
            <Avatar name={p.merchantName} />
            <div className="grow">
              <span className="muted small">Sold by</span>
              <div>
                <a className="link" href={`#/store/${p.merchantId}`}>
                  {p.merchantName}
                </a>
              </div>
              {store.data && (
                <span className="muted small">
                  {store.data.city}, {store.data.country} - on Nexora since {formatDay(store.data.memberSince)}
                </span>
              )}
            </div>
            {canBuy && (
              <button className="btn btn-sm" onClick={startChat}>
                <MessageSquare size={15} /> Contact seller
              </button>
            )}
          </div>
        </div>
      </div>

      <section id="reviews" className="card stack">
        <div className="card-head">
          <h2>Customer reviews</h2>
          {r && r.reviewCount > 0 && (
            <span className="rating">
              <Stars value={r.averageRating} size={17} /> <strong>{Number(r.averageRating).toFixed(1)} out of 5</strong>
            </span>
          )}
        </div>
        {!r ? (
          <Spinner />
        ) : r.reviewCount === 0 ? (
          <p className="muted">No reviews yet. Only customers who received this product can review it.</p>
        ) : (
          <div className="two-col even" style={{ alignItems: "start" }}>
            <div className="stack tight">
              {[5, 4, 3, 2, 1].map((star) => {
                const count = Number(r.distribution?.[star] || 0);
                return (
                  <div className="dist" key={star}>
                    <span>{star} star</span>
                    <div className="dist-track">
                      <span style={{ width: `${r.reviewCount ? (count / r.reviewCount) * 100 : 0}%` }} />
                    </div>
                    <span className="muted num">{count}</span>
                  </div>
                );
              })}
            </div>
            <div>
              {r.reviews.content.map((review) => (
                <div className="review" key={review.id}>
                  <div className="row between">
                    <Stars value={review.rating} />
                    <span className="muted small">{formatDay(review.createdAt)}</span>
                  </div>
                  {review.comment && <p style={{ marginTop: 6 }}>{review.comment}</p>}
                  <span className="muted small">{review.customerName} - verified purchase</span>
                </div>
              ))}
              <Pagination page={r.reviews} onChange={setReviewPage} />
            </div>
          </div>
        )}
      </section>

      {others.length > 0 && (
        <section className="stack" style={{ gap: 12 }}>
          <h2>More from {p.merchantName}</h2>
          <div className="product-grid">
            {others.map((item) => (
              <ProductCard key={item.id} product={item} cart={cart} canBuy={canBuy} onAdded={(x) => toast.show(`${x.name} added to your cart`)} />
            ))}
          </div>
        </section>
      )}

      {contact && (
        <ContactSeller
          merchantId={p.merchantId}
          storeName={p.merchantName}
          product={p}
          onClose={() => setContact(false)}
          onSent={(conversationId) => navigate(`/messages/${conversationId}`)}
        />
      )}
    </div>
  );
}
