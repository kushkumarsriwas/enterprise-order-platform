import { useCallback, useEffect, useMemo, useState } from "react";

const KEY = "nexora_cart_v3";
const EVENT = "nexora-cart";

function read() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || "[]");
  } catch {
    return [];
  }
}

/**
 * The cart lives in the browser and can hold products from many stores. Prices shown here are a preview:
 * the server re-prices everything at checkout and creates one order per store.
 */
export function useCart() {
  const [items, setItems] = useState(read);

  // keep every component (header badge, cart page) and every tab in sync
  useEffect(() => {
    const sync = () => setItems(read());
    window.addEventListener(EVENT, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(EVENT, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  const save = useCallback((updater) => {
    const next = updater(read());
    try {
      localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
      /* storage unavailable */
    }
    setItems(next);
    window.dispatchEvent(new Event(EVENT));
  }, []);

  const add = useCallback(
    (product, quantity = 1) =>
      save((current) => {
        const limit = Math.max(0, Math.min(product.availableQuantity ?? 100, 100));
        const existing = current.find((item) => item.productId === product.id);
        if (existing) {
          return current.map((item) =>
            item.productId === product.id
              ? { ...item, quantity: Math.min(item.quantity + quantity, limit), available: product.availableQuantity, price: product.price }
              : item,
          );
        }
        return [
          ...current,
          {
            productId: product.id,
            name: product.name,
            brand: product.brand,
            price: product.price,
            compareAtPrice: product.compareAtPrice,
            currency: product.currency || "AED",
            sellerId: product.sellerId,
            sellerName: product.sellerName,
            categoryName: product.categoryName,
            imageUrl: product.imageUrl,
            available: product.availableQuantity,
            quantity: Math.min(quantity, limit),
          },
        ];
      }),
    [save],
  );

  const update = useCallback(
    (productId, quantity) =>
      save((current) =>
        quantity <= 0
          ? current.filter((item) => item.productId !== productId)
          : current.map((item) => (item.productId === productId ? { ...item, quantity: Math.min(quantity, item.available || 100, 100) } : item)),
      ),
    [save],
  );

  /** Brings a line up to date with the store's current price and stock (the quantity is left as chosen). */
  const refresh = useCallback(
    (product) =>
      save((current) =>
        current.map((item) =>
          item.productId === product.id
            ? {
                ...item,
                name: product.name,
                brand: product.brand,
                price: product.price,
                compareAtPrice: product.compareAtPrice,
                imageUrl: product.imageUrl,
                categoryName: product.categoryName,
                sellerName: product.sellerName,
                available: product.availableQuantity,
              }
            : item,
        ),
      ),
    [save],
  );

  const remove = useCallback((productId) => save((current) => current.filter((item) => item.productId !== productId)), [save]);
  const removeMany = useCallback((ids) => save((current) => current.filter((item) => !ids.includes(item.productId))), [save]);
  const clear = useCallback(() => save(() => []), [save]);

  const groups = useMemo(() => {
    const byStore = new Map();
    items.forEach((item) => {
      const group = byStore.get(item.sellerId) || { sellerId: item.sellerId, sellerName: item.sellerName, items: [], subtotal: 0 };
      group.items.push(item);
      group.subtotal += item.quantity * Number(item.price);
      byStore.set(item.sellerId, group);
    });
    return [...byStore.values()];
  }, [items]);

  const count = items.reduce((sum, item) => sum + item.quantity, 0);
  const total = items.reduce((sum, item) => sum + item.quantity * Number(item.price), 0);
  const quantityOf = useCallback((productId) => items.find((item) => item.productId === productId)?.quantity || 0, [items]);

  return { items, groups, add, update, refresh, remove, removeMany, clear, count, total, quantityOf };
}
