import { useCallback, useEffect, useState } from "react";

const KEY = "nexora_cart";

function read() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || "[]");
  } catch {
    return [];
  }
}

// Cart lives in the browser; prices are always re-calculated by the server when the order is placed.
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
      const existing = current.find((item) => item.productId === product.id);
      if (existing) {
        return current.map((item) =>
          item.productId === product.id
            ? { ...item, quantity: Math.min(item.quantity + quantity, product.availableQuantity, 100) }
            : item
        );
      }
      return [
        ...current,
        {
          productId: product.id,
          name: product.name,
          sku: product.sku,
          price: product.price,
          currency: product.currency,
          available: product.availableQuantity,
          quantity: Math.min(quantity, product.availableQuantity),
        },
      ];
    });
  }, []);

  const update = useCallback((productId, quantity) => {
    setItems((current) =>
      quantity <= 0
        ? current.filter((item) => item.productId !== productId)
        : current.map((item) => (item.productId === productId ? { ...item, quantity } : item))
    );
  }, []);

  const clear = useCallback(() => setItems([]), []);
  const count = items.reduce((sum, item) => sum + item.quantity, 0);
  const total = items.reduce((sum, item) => sum + item.quantity * Number(item.price), 0);

  return { items, add, update, clear, count, total };
}
