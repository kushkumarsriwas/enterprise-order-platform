import { Plus } from "lucide-react";
import { formatAmount } from "../format.js";
import { ProductArt, StockNote } from "./ui.jsx";

export default function ProductCard({ product, cart, canBuy, onAdded }) {
  const inCart = cart.quantityOf(product.id);
  const canAdd = canBuy && product.availableQuantity > inCart;
  return (
    <article className="product-card">
      <a className="art-link" href={`#/product/${product.id}`} aria-label={product.name}>
        <ProductArt product={product} />
      </a>
      <div className="product-body">
        <a className="sold-by" href={`#/store/${product.merchantId}`}>
          {product.merchantName}
        </a>
        <h3 className="clamp">
          <a href={`#/product/${product.id}`}>{product.name}</a>
        </h3>
        <span className="muted small">{product.categoryName || "General"}</span>
        <div className="product-foot">
          <div className="stack tight" style={{ gap: 2 }}>
            <span className="price">
              <small>AED</small>
              {formatAmount(product.price)}
            </span>
            <StockNote available={product.availableQuantity} low={product.lowStock} />
          </div>
          {canBuy && (
            <button
              className="btn btn-primary btn-sm"
              disabled={!canAdd}
              onClick={() => {
                cart.add(product);
                onAdded?.(product);
              }}
              aria-label={`Add ${product.name} to cart`}
            >
              <Plus size={14} /> {inCart ? `Add (${inCart})` : "Add"}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
