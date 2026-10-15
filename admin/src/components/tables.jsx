import { useState } from "react";
import { api, qs } from "../api.js";
import { formatDate, formatMoney, formatNumber, formatPercent, label, timeAgo } from "../format.js";
import { useLoader } from "../hooks.jsx";
import { ProductThumb } from "./brand.jsx";
import { DataTable, Empty, ErrorState, Loader, Pagination, Panel, StatusBadge } from "./ui.jsx";

export const METHOD_NAMES = { CARD: "Card", UPI: "Instant bank transfer", COD: "Cash on delivery" };
export const methodName = (method) => METHOD_NAMES[method] || label(method);

/** Payment status in words that fit a payment (PENDING here is cash still to collect, not an unpaid order). */
export function PaymentStatus({ status }) {
  if (status === "PENDING") return <StatusBadge status="PAY_ON_DELIVERY">Cash to collect</StatusBadge>;
  if (status === "CANCELLED") return <StatusBadge status="CANCELLED">Cancelled</StatusBadge>;
  return <StatusBadge status={status} />;
}

// ---------------------------------------------------------------- column sets shared by the list and detail pages

export function orderColumns({ store = true, customer = true } = {}) {
  return [
    {
      key: "order",
      header: "Order",
      render: (o) => (
        <div>
          <div className="cell-ref">{o.orderNumber}</div>
          <div className="cell-sub">{timeAgo(o.createdAt)}</div>
        </div>
      ),
    },
    store && { key: "store", header: "Store", render: (o) => <span className="nowrap">{o.sellerName}</span> },
    customer && {
      key: "customer",
      header: "Customer",
      render: (o) => (
        <div>
          <div className="cell-title">{o.recipientName || o.customerUsername}</div>
          <div className="cell-sub">{[...new Set([o.shippingCity, o.shippingRegion].filter(Boolean))].join(", ")}</div>
        </div>
      ),
    },
    { key: "units", header: "Units", align: "right", render: (o) => formatNumber(o.units) },
    { key: "total", header: "Total", align: "right", render: (o) => <span className="strong">{formatMoney(o.totalAmount)}</span> },
    {
      key: "profit",
      header: "Store profit",
      align: "right",
      render: (o) => <span className={Number(o.grossProfit) < 0 ? "bad" : ""}>{formatMoney(o.grossProfit)}</span>,
    },
    {
      key: "payment",
      header: "Payment",
      render: (o) => (
        <div>
          <StatusBadge status={o.paymentState} />
          <div className="cell-sub">{o.paymentMethod ? methodName(o.paymentMethod) : "Not chosen yet"}</div>
        </div>
      ),
    },
    { key: "status", header: "Status", render: (o) => <StatusBadge status={o.status} /> },
  ].filter(Boolean);
}

export function paymentColumns({ store = true, customer = true } = {}) {
  return [
    {
      key: "ref",
      header: "Payment",
      render: (p) => (
        <div>
          <div className="cell-ref">{p.transactionReference}</div>
          <div className="cell-sub">{formatDate(p.createdAt)}</div>
        </div>
      ),
    },
    { key: "order", header: "Order", render: (p) => <span className="ref nowrap">{p.orderNumber}</span> },
    store && { key: "store", header: "Store", render: (p) => <span className="nowrap">{p.sellerName}</span> },
    customer && { key: "customer", header: "Customer", render: (p) => p.customerUsername },
    {
      key: "method",
      header: "Method",
      render: (p) => (
        <div>
          <div>{methodName(p.method)}</div>
          {p.maskedInstrument && <div className="cell-sub">{p.maskedInstrument}</div>}
        </div>
      ),
    },
    { key: "amount", header: "Amount", align: "right", render: (p) => <span className="strong">{formatMoney(p.amount)}</span> },
    { key: "fee", header: "Nexora fee", align: "right", render: (p) => formatMoney(p.platformFee) },
    { key: "net", header: "Store gets", align: "right", render: (p) => formatMoney(p.sellerNet) },
    { key: "status", header: "Status", render: (p) => <PaymentStatus status={p.status} /> },
  ].filter(Boolean);
}

export function productColumns({ store = true } = {}) {
  return [
    {
      key: "product",
      header: "Product",
      render: (p) => (
        <div className="product-cell">
          <ProductThumb product={p} />
          <div style={{ minWidth: 0 }}>
            <div className="cell-title clamp-2">{p.name}</div>
            <div className="cell-sub">
              {p.sku}
              {p.brand ? ` - ${p.brand}` : ""}
            </div>
          </div>
        </div>
      ),
    },
    store && { key: "store", header: "Store", render: (p) => <span className="nowrap">{p.sellerName}</span> },
    { key: "department", header: "Department", render: (p) => p.categoryName || <span className="muted">None</span> },
    { key: "price", header: "Price", align: "right", render: (p) => <span className="strong">{formatMoney(p.price)}</span> },
    { key: "margin", header: "Margin", align: "right", render: (p) => formatPercent(p.marginPercent) },
    {
      key: "stock",
      header: "Available",
      align: "right",
      render: (p) => (
        <div>
          <div className="strong">{formatNumber(p.availableQuantity)}</div>
          <div className="cell-sub">{formatNumber(p.onHandQuantity)} on hand</div>
        </div>
      ),
    },
    { key: "sold", header: "Sold", align: "right", render: (p) => formatNumber(p.unitsSold) },
    {
      key: "status",
      header: "Status",
      render: (p) => (
        <div className="stack-sm" style={{ gap: 4, alignItems: "flex-start" }}>
          <StatusBadge status={p.stockStatus} />
          <span className="cell-sub">{p.storeSuspended ? "Store suspended" : p.listed ? "Listed" : "Inventory only"}</span>
        </div>
      ),
    },
  ].filter(Boolean);
}

/**
 * A panel that pages through one admin list with a fixed filter (a store's orders, a customer's payments).
 * The full list pages have their own filters; these embedded lists only page.
 */
export function PagedPanel({ title, sub, path, query = {}, columns, rowKey = "id", onRowClick, empty, actions, size = 10, map = (page) => page }) {
  const [page, setPage] = useState(0);
  const list = useLoader(() => api(`${path}${qs({ ...query, page, size })}`).then(map), [path, JSON.stringify(query), page]);
  const data = list.data;
  return (
    <Panel title={title} sub={sub} actions={actions} flush>
      {list.loading && !data ? (
        <Loader />
      ) : list.error && !data ? (
        <ErrorState message={list.error} onRetry={list.reload} />
      ) : (
        <div style={{ opacity: list.loading ? 0.6 : 1 }}>
          <DataTable rowKey={rowKey} rows={data.content} columns={columns} onRowClick={onRowClick} empty={<Empty title={empty || "Nothing here"} />} />
          <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={size} onPage={setPage} />
        </div>
      )}
    </Panel>
  );
}

export const MOVEMENT_NAMES = {
  OPENING_STOCK: "Opening stock",
  RECEIVED: "Received from supplier",
  SOLD: "Sold",
  RETURNED: "Returned",
  ADJUSTMENT: "Adjustment",
  COUNT_ADJUSTMENT: "Stock count",
  TRANSFER_OUT: "Transfer out",
  TRANSFER_IN: "Transfer in",
  RESERVED: "Reserved for an order",
  RELEASED: "Reservation released",
  EXPIRED: "Reservation expired",
};

export const movementName = (type) => MOVEMENT_NAMES[type] || label(type);

/** Signed quantity: "+20" in green, "-3" in red. */
export function Quantity({ value }) {
  const n = Number(value) || 0;
  return <span className={`strong ${n > 0 ? "good" : n < 0 ? "bad" : ""}`}>{`${n > 0 ? "+" : ""}${formatNumber(n)}`}</span>;
}
