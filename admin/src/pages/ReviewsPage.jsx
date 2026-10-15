import { useState } from "react";
import { CircleAlert, MessageSquareReply, Star, Trash2 } from "lucide-react";
import { api, qs } from "../api.js";
import { ActiveFilters, DebouncedSearch, PageHead, Toolbar, storeOptions, useFilters, useStores } from "../components/common.jsx";
import { Badge, Button, Empty, ErrorState, Field, Loader, Modal, Notice, Pagination, Panel, Select, Stars, Textarea } from "../components/ui.jsx";
import { formatDate, formatNumber } from "../format.js";
import { useLoader, useToast } from "../hooks.jsx";

const RATINGS = [
  { value: "", label: "Any rating" },
  { value: "5", label: "5 stars" },
  { value: "4", label: "4 stars" },
  { value: "3", label: "3 stars" },
  { value: "2", label: "2 stars" },
  { value: "1", label: "1 star" },
];

const REPLIED = [
  { value: "", label: "Answered or not" },
  { value: "false", label: "Not answered" },
  { value: "true", label: "Answered" },
];

const SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "lowest", label: "Lowest rating first" },
  { value: "highest", label: "Highest rating first" },
  { value: "oldest", label: "Oldest first" },
];

const REMOVAL_REASONS = [
  "It is not about the product",
  "It contains offensive or abusive language",
  "It contains personal information",
  "It is advertising or spam",
  "Other",
];

function RemoveDialog({ review, onClose, onDone }) {
  const toast = useToast();
  const [reason, setReason] = useState(REMOVAL_REASONS[0]);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const submit = async () => {
    const text = reason === "Other" ? note.trim() : note.trim() ? `${reason}. ${note.trim()}` : `${reason}.`;
    if (!text) {
      setError("Write the reason the customer will read.");
      return;
    }
    setBusy(true);
    try {
      await api(`/api/admin/reviews/${review.id}${qs({ reason: text.slice(0, 250) })}`, { method: "DELETE" });
      toast("Review removed; the product rating was recalculated");
      onDone();
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  };
  return (
    <Modal
      open
      onClose={onClose}
      title="Remove this review?"
      sub={`${review.customerName}'s ${review.rating}-star review of ${review.productName} disappears from the marketplace, the product rating is recalculated and the customer gets a notification with the reason.`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Keep the review
          </Button>
          <Button variant="danger" loading={busy} onClick={submit}>
            Remove the review
          </Button>
        </>
      }
    >
      <div className="stack">
        <blockquote className="review-quote">{review.comment || "(no written comment)"}</blockquote>
        <Field label="Reason">
          <Select value={reason} onChange={(e) => setReason(e.target.value)} options={REMOVAL_REASONS.map((r) => ({ value: r, label: r }))} />
        </Field>
        <Field label={reason === "Other" ? "Tell the customer why" : "Anything to add? (optional)"} hint="The customer reads this in their notifications">
          <Textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={200} />
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

/** Average, count and the spread of stars. */
export function RatingSummary({ data }) {
  const total = Object.values(data.distribution || {}).reduce((sum, n) => sum + Number(n), 0);
  return (
    <div className="stack">
      <div className="row" style={{ gap: 12, alignItems: "flex-end" }}>
        <span style={{ fontSize: "2.2rem", fontWeight: 700, lineHeight: 1 }}>{Number(data.averageRating || 0).toFixed(1)}</span>
        <div>
          <Stars value={Number(data.averageRating || 0)} />
          <div className="small muted">{formatNumber(data.reviewCount)} reviews</div>
        </div>
      </div>
      <div className="rating-bars">
        {[5, 4, 3, 2, 1].map((n) => {
          const count = Number(data.distribution?.[n] || 0);
          return (
            <div key={n}>
              <span>{n} star</span>
              <span className="track">
                <span style={{ width: `${total ? (count / total) * 100 : 0}%` }} />
              </span>
              <span className="num">{formatNumber(count)}</span>
            </div>
          );
        })}
      </div>
      {data.awaitingReply > 0 && <div className="small muted">{formatNumber(data.awaitingReply)} still without a reply from the store</div>}
    </div>
  );
}

/** A list of reviews with the remove action; used by the Reviews page and a store's page. */
export function ReviewRows({ reviews, showStore = true, onRemove }) {
  if (!reviews.length) return <Empty icon={Star} title="No review matches" />;
  return (
    <div>
      {reviews.map((r) => (
        <article className="review-row" key={r.id}>
          <div className="row-between" style={{ alignItems: "flex-start", gap: 12 }}>
            <div className="stack-sm" style={{ gap: 3, minWidth: 0 }}>
              <div className="row row-wrap" style={{ gap: 8 }}>
                <Stars value={r.rating} />
                <span className="strong small">{r.customerName}</span>
                {r.verifiedPurchase && <Badge tone="brand">Verified purchase</Badge>}
                <span className="tiny muted">{formatDate(r.createdAt)}</span>
              </div>
              <div className="small">
                <span className="strong">{r.productName}</span>
                {showStore && (
                  <>
                    {" "}
                    <span className="muted">from</span> <a href={`#/stores/${r.sellerId}`}>{r.sellerName}</a>
                  </>
                )}
              </div>
            </div>
            {onRemove && (
              <Button size="sm" variant="danger" icon={Trash2} onClick={() => onRemove(r)}>
                Remove
              </Button>
            )}
          </div>
          {r.comment ? <p className="small">{r.comment}</p> : <p className="small muted">No written comment.</p>}
          {r.sellerReply && (
            <div className="reply">
              <div className="tiny muted" style={{ marginBottom: 2 }}>
                <MessageSquareReply size={13} style={{ verticalAlign: "-2px" }} /> Reply from {r.sellerName}
                {r.sellerRepliedAt ? `, ${formatDate(r.sellerRepliedAt)}` : ""}
              </div>
              {r.sellerReply}
            </div>
          )}
        </article>
      ))}
    </div>
  );
}

/** Store reviews loaded with fixed filters (a store's page) and the remove action. */
export function StoreReviewsPanel({ sellerId }) {
  const [page, setPage] = useState(0);
  const [removing, setRemoving] = useState(null);
  const data = useLoader(() => api(`/api/admin/reviews${qs({ sellerId, page, size: 10 })}`), [sellerId, page]);
  if (data.loading && !data.data) return <Loader />;
  if (data.error && !data.data) return <ErrorState message={data.error} onRetry={data.reload} />;
  const d = data.data;
  return (
    <div className="grid-side">
      <Panel title="Rating">
        <RatingSummary data={d} />
      </Panel>
      <Panel title="Reviews" flush>
        <ReviewRows reviews={d.reviews.content} showStore={false} onRemove={setRemoving} />
        <Pagination page={page} totalPages={d.reviews.totalPages} totalElements={d.reviews.totalElements} size={10} onPage={setPage} />
      </Panel>
      {removing && (
        <RemoveDialog
          review={removing}
          onClose={() => setRemoving(null)}
          onDone={() => {
            setRemoving(null);
            data.reload();
          }}
        />
      )}
    </div>
  );
}

/** Moderation of every review on the marketplace. */
export function ReviewsPage({ route, navigate }) {
  const [f, set, clear] = useFilters(route, navigate, { sort: "newest" });
  const stores = useStores();
  const [removing, setRemoving] = useState(null);
  const page = Number(f.page || 0);
  const query = { q: f.q, sellerId: f.sellerId, rating: f.rating, replied: f.replied, sort: f.sort };
  const reviews = useLoader(() => api(`/api/admin/reviews${qs({ ...query, page, size: 20 })}`), [JSON.stringify(query), page]);
  const d = reviews.data;

  return (
    <>
      <PageHead
        title="Reviews"
        sub="Every review on Nexora. Remove one only when it breaks the review guidelines; the customer is told why and the product rating is recalculated."
      />
      {reviews.loading && !d ? (
        <Loader text="Loading reviews" />
      ) : reviews.error && !d ? (
        <ErrorState message={reviews.error} onRetry={reviews.reload} />
      ) : (
        <div className="grid-side" style={{ opacity: reviews.loading ? 0.6 : 1 }}>
          <Panel title={f.sellerId || f.q || f.rating || f.replied ? "Matching reviews" : "All reviews"}>
            <RatingSummary data={d} />
          </Panel>
          <Panel flush>
            <Toolbar right={<Select className="select-sm" value={f.sort} options={SORTS} onChange={(e) => set({ sort: e.target.value })} aria-label="Sort" />}>
              <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Words in the review, product or customer" />
              <Select
                className="select-sm"
                value={f.sellerId || ""}
                options={storeOptions(stores.data)}
                onChange={(e) => set({ sellerId: e.target.value })}
                aria-label="Store"
              />
              <Select className="select-sm" value={f.rating || ""} options={RATINGS} onChange={(e) => set({ rating: e.target.value })} aria-label="Rating" />
              <Select
                className="select-sm"
                value={f.replied || ""}
                options={REPLIED}
                onChange={(e) => set({ replied: e.target.value })}
                aria-label="Answered"
              />
            </Toolbar>
            <ActiveFilters
              onClear={() => clear()}
              chips={[
                { key: "q", label: `"${f.q}"`, value: f.q, onRemove: () => set({ q: "" }) },
                {
                  key: "store",
                  label: stores.data?.find((s) => String(s.id) === f.sellerId)?.store?.storeName || "Store",
                  value: f.sellerId,
                  onRemove: () => set({ sellerId: "" }),
                },
                { key: "rating", label: `${f.rating} stars`, value: f.rating, onRemove: () => set({ rating: "" }) },
                { key: "replied", label: f.replied === "true" ? "Answered" : "Not answered", value: f.replied, onRemove: () => set({ replied: "" }) },
              ]}
            />
            <ReviewRows reviews={d.reviews.content} onRemove={setRemoving} />
            <Pagination page={page} totalPages={d.reviews.totalPages} totalElements={d.reviews.totalElements} size={20} onPage={(p) => set({ page: p })} />
          </Panel>
        </div>
      )}
      {removing && (
        <RemoveDialog
          review={removing}
          onClose={() => setRemoving(null)}
          onDone={() => {
            setRemoving(null);
            reviews.reload();
          }}
        />
      )}
    </>
  );
}
