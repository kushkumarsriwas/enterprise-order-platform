import { Bell, MessageSquare, Package, Star, UserRound } from "lucide-react";
import { Messenger, NotificationFeed } from "../../components/inbox.jsx";
import { useTitle } from "../../hooks.jsx";
import { match } from "../../router.js";
import { NotFound } from "../NotFound.jsx";
import { CustomerOrderPage, CustomerOrdersPage } from "./OrdersPages.jsx";
import { ProfilePage } from "./ProfilePage.jsx";
import { CustomerReviewsPage } from "./ReviewsPage.jsx";

const NAV = [
  { path: "/account/orders", label: "Orders", icon: Package },
  { path: "/account/messages", label: "Messages", icon: MessageSquare },
  { path: "/account/notifications", label: "Notifications", icon: Bell },
  { path: "/account/reviews", label: "Reviews", icon: Star },
  { path: "/account/profile", label: "Account details", icon: UserRound },
];

function MessagesPage({ route }) {
  useTitle("Messages");
  return (
    <div className="stack">
      <div className="page-title">
        <div>
          <h1>Messages</h1>
          <p>Conversations with stores about products and orders. Never pay a store outside Nexora.</p>
        </div>
      </div>
      <Messenger initialId={route.query.c} emptyText="Ask a store a question from any product page, store page or order." />
    </div>
  );
}

function NotificationsPage() {
  useTitle("Notifications");
  return (
    <div className="stack">
      <div className="page-title">
        <div>
          <h1>Notifications</h1>
          <p>Order updates, payments and replies from stores.</p>
        </div>
      </div>
      <NotificationFeed />
    </div>
  );
}

/** The customer's own area: orders, messages, notifications, reviews and account details. */
export function AccountArea({ route, navigate }) {
  const { path } = route;
  let page;
  let params;
  if (path === "/account" || path === "/account/orders") page = <CustomerOrdersPage route={route} navigate={navigate} />;
  else if ((params = match("/account/orders/:id", path))) page = <CustomerOrderPage key={params.id} id={params.id} navigate={navigate} />;
  else if (path === "/account/messages") page = <MessagesPage route={route} />;
  else if (path === "/account/notifications") page = <NotificationsPage />;
  else if (path === "/account/reviews") page = <CustomerReviewsPage route={route} navigate={navigate} />;
  else if (path === "/account/profile") page = <ProfilePage />;
  else page = <NotFound />;

  const active = NAV.find((item) => path === item.path || path.startsWith(`${item.path}/`)) || (path === "/account" ? NAV[0] : null);

  return (
    <div className="account-layout">
      <nav className="account-nav" aria-label="Your account">
        {NAV.map((item) => (
          <a key={item.path} href={`#${item.path}`} className={active === item ? "active" : ""} aria-current={active === item ? "page" : undefined}>
            <item.icon /> {item.label}
          </a>
        ))}
      </nav>
      <div>{page}</div>
    </div>
  );
}
