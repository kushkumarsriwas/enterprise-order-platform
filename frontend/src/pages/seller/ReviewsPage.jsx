import { useState } from "react";
import { MessageSquareReply, Star } from "lucide-react";
import { api, qs } from "../../api.js";
import { Badge, Button, Empty, ErrorState, Loader, Meter, Pagination, Panel, PillToggle, Select, Stars, Textarea } from "../../components/ui.jsx";
import { formatDay, formatNumber, plural } from "../../format.js";
import { useLoader, useToast } from "../../hooks.jsx";
import { DebouncedSearch, PageHead, Toolbar, useFilters } from "./common.jsx";
import { useSeller } from "./SellerLayout.jsx";

function ReplyBox({ review, onSaved, onCancel }) {
  const toast = useToast();
  const [text, setText] = useState(review.sellerReply || "");
  const [busy, setBusy] = useState(false);
  return (
    <div className="reply-box">
      <Textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={1000}
        rows={3}
        placeholder="Thank the customer, answer their question or explain what you will do. Everyone can read your reply."
        autoFocus
      />
      <div className="row">
        <Button
          size="sm"
          variant="primary"
          loading={busy}
          disabled={!text.trim()}
          onClick={async () => {
            setBusy(true);
            try {
              const updated = await api(`/api/seller/reviews/${review.id}/reply`, { method: "PUT", body: { reply: text.trim() } });
              toast("Reply published");
              onSaved(updated);
            } catch (e) {
              toast(e.message, "bad");
            } finally {
              setBusy(false);
            }
          }}
        >
          Publish reply
        </Button>
        <Button size="sm" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  );
}

export function SellerReviewsPage({ route, navigate }) {
  const { reloadCounts } = useSeller();
  const [f, set] = useFilters(route, navigate, { sort: "newest" });
  const page = Number(f.page || 0);
  const query = { rating: f.rating, replied: f.replied, productId: f.productId, q: f.q, sort: f.sort };
  const reviews = useLoader(() => api(`/api/seller/reviews${qs({ ...query, page, size: 10 })}`), [JSON.stringify(query), page]);
  const [replying, setReplying] = useState(null);
  const data = reviews.data;

  return (
    <>
      <PageHead title="Reviews" sub="What verified buyers say about your products. A short, helpful reply shows other customers that you care." />
      {reviews.loading && !data ? (
        <Loader text="Loading reviews" />
      ) : reviews.error && !data ? (
        <ErrorState message={reviews.error} onRetry={reviews.reload} />
      ) : (
        <div className="sc-grid-side">
          <Panel title="Your rating">
            <div className="stack">
              <div className="row" style={{ gap: 12, alignItems: "center" }}>
                <span className="rating-summary">
                  <span className="big">{data.reviewCount ? Number(data.averageRating).toFixed(1) : "-"}</span>
                </span>
                <div className="stack-sm" style={{ gap: 2 }}>
                  <Stars value={data.averageRating || 0} />
                  <span className="small muted">{plural(data.reviewCount, "review")}</span>
                </div>
              </div>
              <div>
                {[5, 4, 3, 2, 1].map((stars) => {
                  const count = Number(data.distribution?.[stars] || 0);
                  const percent = data.reviewCount ? Math.round((count / data.reviewCount) * 100) : 0;
                  return (
                    <button
                      key={stars}
                      type="button"
                      className={`dist-row ${String(f.rating) === String(stars) ? "active" : ""}`}
                      aria-pressed={String(f.rating) === String(stars)}
                      onClick={() => set({ rating: String(f.rating) === String(stars) ? "" : String(stars) })}
                    >
                      <span>{stars} star</span>
                      <Meter percent={percent} />
                      <span className="num small muted">{formatNumber(count)}</span>
                    </button>
                  );
                })}
              </div>
              {data.awaitingReply > 0 && (
                <Button size="sm" icon={MessageSquareReply} onClick={() => set({ replied: "false" })}>
                  {plural(data.awaitingReply, "review")} without a reply
                </Button>
              )}
            </div>
          </Panel>

          <Panel flush>
            <Toolbar>
              <DebouncedSearch value={f.q} onChange={(q) => set({ q })} placeholder="Words in the review, product or customer" />
              <PillToggle
                label="Reply"
                value={f.replied || ""}
                onChange={(replied) => set({ replied })}
                options={[
                  { value: "", label: "All" },
                  { value: "false", label: "Not answered" },
                  { value: "true", label: "Answered" },
                ]}
              />
              <Select
                className="select-sm"
                value={f.sort}
                aria-label="Sort"
                onChange={(e) => set({ sort: e.target.value })}
                options={[
                  { value: "newest", label: "Newest first" },
                  { value: "lowest", label: "Lowest rated" },
                  { value: "highest", label: "Highest rated" },
                  { value: "oldest", label: "Oldest first" },
                ]}
              />
            </Toolbar>
            <div className="panel-body" style={{ opacity: reviews.loading ? 0.6 : 1 }}>
              {data.reviews.content.length === 0 ? (
                <Empty icon={Star} title={f.rating || f.replied || f.q ? "No review matches" : "No reviews yet"}>
                  Customers can review a product once you deliver it.
                </Empty>
              ) : (
                data.reviews.content.map((r) => (
                  <article key={r.id} className="review">
                    <div className="row row-wrap" style={{ gap: 10 }}>
                      <Stars value={r.rating} />
                      <a href={`#/seller/products/${r.productId}`} className="strong small">
                        {r.productName}
                      </a>
                      {!r.sellerReply && <Badge tone="amber">Waiting for your reply</Badge>}
                    </div>
                    <div className="tiny muted" style={{ marginTop: 4 }}>
                      {r.customerName}, {formatDay(r.createdAt)}
                      {r.verifiedPurchase ? ", verified purchase" : ""}
                    </div>
                    {r.comment ? <p>{r.comment}</p> : <p className="muted">No written comment.</p>}
                    {replying === r.id ? (
                      <ReplyBox
                        review={r}
                        onCancel={() => setReplying(null)}
                        onSaved={(updated) => {
                          setReplying(null);
                          reviews.setData((d) => ({
                            ...d,
                            reviews: { ...d.reviews, content: d.reviews.content.map((x) => (x.id === updated.id ? updated : x)) },
                          }));
                          reviews.reload();
                          reloadCounts();
                        }}
                      />
                    ) : r.sellerReply ? (
                      <div className="seller-reply">
                        <div className="row-between">
                          <span className="tiny strong">Your reply, {formatDay(r.sellerRepliedAt)}</span>
                          <button type="button" className="link-btn small" onClick={() => setReplying(r.id)}>
                            Edit
                          </button>
                        </div>
                        {r.sellerReply}
                      </div>
                    ) : (
                      <div style={{ marginTop: 10 }}>
                        <Button size="sm" icon={MessageSquareReply} onClick={() => setReplying(r.id)}>
                          Reply
                        </Button>
                      </div>
                    )}
                  </article>
                ))
              )}
              <Pagination
                page={page}
                totalPages={data.reviews.totalPages}
                totalElements={data.reviews.totalElements}
                size={10}
                onPage={(p) => set({ page: p })}
              />
            </div>
          </Panel>
        </div>
      )}
    </>
  );
}
