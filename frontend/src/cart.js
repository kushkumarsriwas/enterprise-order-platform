import { useCallback, useEffect, useMemo, useState } from "react";

const KEY = "nexora_cart_v2";

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

  useEffect(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify(items));
    } catch {
      /* storage may be unavailable */
    }
  }, [items]);

  const add = useCallback((product, quantity = 1) => {
    setItems((current) => {
      const limit = Math.min(product.availableQuantity, 100);
      const existing = current.find((item) => item.productId === product.id);
      if (existing) {
        return current.map((item) =>
          item.productId === product.id ? { ...item, quantity: Math.min(item.quantity + quantity, limit), available: product.availableQuantity } : item
        );
      }
      return [
        ...current,
        {
          productId: product.id,
          name: product.name,
          price: product.price,
          currency: product.currency || "AED",
          merchantId: product.merchantId,
          merchantName: product.merchantName,
          categoryId: product.categoryId,
          imageUrl: product.imageUrl,
          available: product.availableQuantity,
          quantity: Math.min(quantity, limit),
        },
      ];
    });
  }, []);

  const update = useCallback((productId, quantity) => {
    setItems((current) =>
      quantity <= 0
        ? current.filter((item) => item.productId !== productId)
        : current.map((item) => (item.productId === productId ? { ...item, quantity: Math.min(quantity, item.available || 100, 100) } : item))
    );
  }, []);

  const remove = useCallback((productId) => setItems((current) => current.filter((item) => item.productId !== productId)), []);
  const clear = useCallback(() => setItems([]), []);

  const groups = useMemo(() => {
    const byStore = new Map();
    items.forEach((item) => {
      const group = byStore.get(item.merchantId) || { merchantId: item.merchantId, merchantName: item.merchantName, items: [], subtotal: 0 };
      group.items.push(item);
      group.subtotal += item.quantity * Number(item.price);
      byStore.set(item.merchantId, group);
    });
    return [...byStore.values()];
  }, [items]);

  const count = items.reduce((sum, item) => sum + item.quantity, 0);
  const total = items.reduce((sum, item) => sum + item.quantity * Number(item.price), 0);
  const quantityOf = useCallback((productId) => items.find((item) => item.productId === productId)?.quantity || 0, [items]);

  return { items, groups, add, update, remove, clear, count, total, quantityOf };
}
