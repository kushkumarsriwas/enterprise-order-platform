import { useState } from "react";
import { Star } from "lucide-react";
import { api, qs } from "../../api.js";
import { formatDay } from "../../format.js";
import { useLoader } from "../../hooks.jsx";
import { Alert, Empty, PageHeader, Pagination, Spinner, Stars } from "../../components/ui.jsx";

export default function MerchantReviews() {
  const [page, setPage] = useState(0);
  const reviews = useLoader(() => api(`/api/merchant/reviews${qs({ page, size: 20 })}`), [page]);
  const list = reviews.data?.content || [];
  const average = list.length ? list.reduce((sum, r) => sum + r.rating, 0) / list.length : 0;
  return (
    <div className="page" style={{ maxWidth: 900 }}>
      <PageHeader title="Reviews" subtitle="What customers say about your products. Only customers who received the product can review it." />
      <Alert>{reviews.error}</Alert>
      {reviews.loading && !reviews.data ? (
        <Spinner />
      ) : list.length === 0 ? (
        <Empty icon={Star} title="No reviews yet">Customers can review a product after it is delivered.</Empty>
      ) : (
        <>
          <div className="card row">
            <Stars value={average} size={20} />
            <strong>{average.toFixed(1)} average</strong>
            <span className="muted">
              from {reviews.data.totalElements} review{reviews.data.totalElements === 1 ? "" : "s"}
              {reviews.data.totalPages > 1 ? " (this page)" : ""}
            </span>
          </div>
          <div className="card">
            {list.map((r) => (
              <div className="review" key={r.id}>
                <div className="row between wrap">
                  <div className="row">
                    <Stars value={r.rating} />
                    <a className="link" href={`#/product/${r.productId}`}>
                      {r.productName}
                    </a>
                  </div>
                  <span className="muted small">{formatDay(r.createdAt)}</span>
                </div>
                <p style={{ marginTop: 6 }}>{r.comment || <span className="muted">No comment</span>}</p>
                <span className="muted small">{r.customerName}</span>
              </div>
            ))}
          </div>
        </>
      )}
      <Pagination page={reviews.data} onChange={setPage} />
    </div>
  );
}
