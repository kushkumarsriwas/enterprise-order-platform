import Messenger from "../../components/Messenger.jsx";

export default function MessagesPage({ user, conversationId, basePath, refreshCounts }) {
  const merchant = user.role === "MERCHANT";
  return (
    <div className="page">
      <div>
        <h1>Messages</h1>
        <p className="muted">
          {merchant
            ? "Questions from customers about your products and orders. Reply quickly to win more sales."
            : "Your conversations with sellers. Use Contact seller on a product, store or order page to start one."}
        </p>
      </div>
      <Messenger
        user={user}
        conversationId={conversationId}
        basePath={basePath}
        onRead={refreshCounts}
        emptyHint={merchant ? "When a customer contacts your store, the conversation appears here." : "Ask a seller about a product before you buy, or about an order."}
      />
    </div>
  );
}
