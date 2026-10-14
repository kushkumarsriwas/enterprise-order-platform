import { useState } from "react";
import { ArrowLeft, BadgeCheck, MapPin, MessageSquare, PackageSearch, Store } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDay } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import ContactSeller from "../../components/ContactSeller.jsx";
import ProductCard from "../../components/ProductCard.jsx";
import { Avatar, Empty, Pagination, Spinner } from "../../components/ui.jsx";

export default function StorePage({ id, user, navigate, cart, toast }) {
  const store = useLoader(() => api(`/api/merchants/${id}`, { auth: false }), [id]);
  const [page, setPage] = useState(0);
  const products = useLoader(() => api(`/api/products${qs({ merchantId: id, page, size: 20 })}`, { auth: false }), [id, page]);
  const [contact, setContact] = useState(false);
  const canBuy = !user || user.role === "CUSTOMER";

  if (store.loading && !store.data) return <Spinner />;
  if (store.error)
    return (
      <Empty icon={Store} title="This store is not available" action={<a className="btn" href="#/shop">Back to the marketplace</a>}>
        The store may have closed or been suspended.
      </Empty>
    );
  const s = store.data;
  return (
    <div className="stack" style={{ gap: 22 }}>
      <a className="back" href="#/shop">
        <ArrowLeft size={15} /> Marketplace
      </a>
      <div className="card row wrap" style={{ gap: 18, alignItems: "flex-start" }}>
        <Avatar name={s.storeName} large />
        <div className="grow stack tight">
          <h1>{s.storeName}</h1>
          <span className="info-row">
            <MapPin size={14} /> {s.city}, {s.country} - on Nexora since {formatDay(s.memberSince)}
          </span>
          {s.tradeLicenseNumber && (
            <span className="info-row">
              <BadgeCheck size={14} /> Registered business (trade licence on file)
            </span>
          )}
          {s.description && <p style={{ marginTop: 6, maxWidth: 720 }}>{s.description}</p>}
        </div>
        {canBuy && (
          <button className="btn" onClick={() => (user ? setContact(true) : navigate(`/login?next=${encodeURIComponent(`/store/${id}`)}`))}>
            <MessageSquare size={15} /> Contact seller
          </button>
        )}
      </div>
      <h2>Products{products.data ? ` (${products.data.totalElements})` : ""}</h2>
      {products.loading && !products.data ? (
        <Spinner />
      ) : products.data?.content.length === 0 ? (
        <Empty icon={PackageSearch} title="No products listed yet">This store has not listed any products on the marketplace yet.</Empty>
      ) : (
        <div className="product-grid">
          {products.data?.content.map((p) => (
            <ProductCard key={p.id} product={p} cart={cart} canBuy={canBuy} onAdded={(x) => toast.show(`${x.name} added to your cart`)} />
          ))}
        </div>
      )}
      <Pagination page={products.data} onChange={setPage} />
      {contact && (
        <ContactSeller merchantId={Number(id)} storeName={s.storeName} onClose={() => setContact(false)} onSent={(cid) => navigate(`/messages/${cid}`)} />
      )}
    </div>
  );
}
