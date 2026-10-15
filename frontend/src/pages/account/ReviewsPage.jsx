import { useState } from "react";
import { CircleAlert, MessageSquareQuote, PackageCheck, Star } from "lucide-react";
import { api, qs } from "../../api.js";
import { ProductImage } from "../../components/brand.jsx";
import { StarPicker } from "../../components/orders.jsx";
import { Badge, Button, Empty, ErrorState, Field, Loader, Modal, Notice, Pagination, Panel, Stars, Textarea } from "../../components/ui.jsx";
import { formatDay } from "../../format.js";
import { useLoader, useTitle, useToast } from "../../hooks.jsx";

function WriteReview({ item, onClose, onDone }) {
  const toast = useToast();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const submit = async () => {
    if (!rating) {
      setError("Pick a rating from 1 to 5 stars.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api("/api/reviews", { method: "POST", body: { orderId: item.orderId, productId: item.productId, rating, comment: comment.trim() || null } });
      toast("Thanks, your review is published");
      onDone();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open
      onClose={onClose}
      title={`Review ${item.productName}`}
      sub={`Bought from ${item.sellerName}, order ${item.orderNumber}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} onClick={submit}>
            Publish review
          </Button>
        </>
      }
    >
      <div className="stack">
        <StarPicker value={rating} onChange={setRating} />
        <Field label="Your review (optional)" hint="What did you like or dislike? Other customers and the store will read it.">
          <Textarea value={comment} onChange={(event) => setComment(event.target.value)} maxLength={1000} rows={5} />
        </Field>
        {error && (
          <Notice tone="bad" icon={CircleAlert}>
            {error}
          </Notice>
        )}
      </div>
    </Modal>
  );
}

/** Products waiting for a review, and the reviews the customer already wrote (with store replies). */
export function CustomerReviewsPage() {
  useTitle("Your reviews");
  const pending = useLoader(() => api("/api/reviews/pending"), []);
  const [page, setPage] = useState(0);
  const mine = useLoader(() => api(`/api/reviews/mine${qs({ page, size: 10 })}`), [page]);
  const [writing, setWriting] = useState(null);

  return (
    <div className="stack">
      <div className="page-title">
        <div>
          <h1>Reviews</h1>
          <p>Only customers who received a product can review it, so every review on Nexora is a verified purchase.</p>
        </div>
      </div>

      <Panel title="Waiting for your review" sub="Products from delivered orders">
        {pending.loading && !pending.data ? (
          <Loader />
        ) : pending.error && !pending.data ? (
          <ErrorState message={pending.error} onRetry={pending.reload} />
        ) : pending.data.length === 0 ? (
          <Empty icon={PackageCheck} title="Nothing to review">
            Delivered products appear here.
          </Empty>
        ) : (
          <div className="review-todo">
            {pending.data.map((item) => (
              <div key={`${item.orderId}-${item.productId}`} className="review-todo-item">
                <a href={`#/product/${item.productId}`} className="review-todo-thumb">
                  <ProductImage product={{ id: item.productId, name: item.productName }} iconSize={22} />
                </a>
                <div className="grow">
                  <a href={`#/product/${item.productId}`} className="strong clamp-2" style={{ color: "var(--ink)" }}>
                    {item.productName}
                  </a>
                  <div className="tiny muted">
                    {item.sellerName}, delivered {formatDay(item.deliveredAt)}
                  </div>
                </div>
                <Button size="sm" icon={Star} onClick={() => setWriting(item)}>
                  Write a review
                </Button>
              </div>
            ))}
          </div>
        )}
      </Panel>

      <Panel title="Your reviews">
        {mine.loading && !mine.data ? (
          <Loader />
        ) : mine.error && !mine.data ? (
          <ErrorState message={mine.error} onRetry={mine.reload} />
        ) : mine.data.content.length === 0 ? (
          <Empty icon={MessageSquareQuote} title="You have not written a review yet" />
        ) : (
          <div>
            {mine.data.content.map((r) => (
              <article key={r.id} className="review">
                <div className="row row-wrap" style={{ gap: 10 }}>
                  <Stars value={r.rating} />
                  <a href={`#/product/${r.productId}`} className="strong small">
                    {r.productName}
                  </a>
                  <Badge tone="brand" icon={PackageCheck}>
                    Verified purchase
                  </Badge>
                  <span className="tiny muted">{formatDay(r.createdAt)}</span>
                </div>
                {r.comment && <p>{r.comment}</p>}
                {r.sellerReply && (
                  <div className="seller-reply">
                    <div className="tiny strong" style={{ marginBottom: 4 }}>
                      Reply from {r.sellerName}
                    </div>
                    {r.sellerReply}
                  </div>
                )}
              </article>
            ))}
            <Pagination page={page} totalPages={mine.data.totalPages} totalElements={mine.data.totalElements} size={10} onPage={setPage} />
          </div>
        )}
      </Panel>

      {writing && (
        <WriteReview
          item={writing}
          onClose={() => setWriting(null)}
          onDone={() => {
            setWriting(null);
            pending.reload();
            mine.reload();
          }}
        />
      )}
    </div>
  );
}
