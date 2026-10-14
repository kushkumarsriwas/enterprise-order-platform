import { useState } from "react";
import { Send } from "lucide-react";
import { api } from "../api.js";
import { Alert, Field, Modal } from "./ui.jsx";

/** "Contact seller" from a product, order or store page. Continues the existing thread with that store. */
export default function ContactSeller({ merchantId, storeName, product, orderNumber, onClose, onSent }) {
  const [message, setMessage] = useState(
    product ? `Hi, I have a question about "${product.name}".` : orderNumber ? `Hi, I have a question about my order ${orderNumber}.` : ""
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const send = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api("/api/conversations", {
        method: "POST",
        body: { merchantId, message, productId: product?.id, productName: product?.name, orderNumber },
      });
      onSent(result.conversation.id);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal title={`Message ${storeName}`} onClose={onClose}>
      <form className="stack" onSubmit={send}>
        <p className="muted">The seller usually replies in the Messages section. You will also get a notification.</p>
        <Field label="Your message">
          <textarea rows={5} value={message} maxLength={2000} onChange={(event) => setMessage(event.target.value)} required />
        </Field>
        <Alert onClose={() => setError("")}>{error}</Alert>
        <div className="row end">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={busy || !message.trim()}>
            <Send size={15} /> {busy ? "Sending..." : "Send message"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
