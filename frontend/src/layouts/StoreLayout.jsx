import { useEffect, useRef, useState } from "react";
import { Bell, Boxes, ChevronDown, LayoutDashboard, LogOut, MessageSquare, Moon, Package, Search, ShoppingCart, Sun, UserRound } from "lucide-react";
import { qs } from "../api.js";

/** Storefront chrome for customers and visitors: search, orders, messages, notifications and the cart. */
export default function StoreLayout({ user, cart, counts, route, navigate, onLogout, dark, setDark, waking, children }) {
  const [menu, setMenu] = useState(false);
  const menuRef = useRef(null);
  const isCustomer = user?.role === "CUSTOMER";
  const isStaff = user && !isCustomer;

  useEffect(() => {
    if (!menu) return undefined;
    const close = (event) => menuRef.current && !menuRef.current.contains(event.target) && setMenu(false);
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [menu]);

  const active = (prefix) => (route.path === prefix || route.path.startsWith(`${prefix}/`) ? "active" : "");

  return (
    <div className="store-shell">
      {isStaff && (
        <div className="preview-bar">
          You are viewing the marketplace as {user.role === "ADMIN" ? "an administrator" : "a merchant"}. Shopping is only available to customer accounts.
        </div>
      )}
      <header className="store-header">
        <div className="store-header-inner">
          <a className="store-brand" href="#/shop">
            <Boxes size={24} /> Nexora
          </a>
          <form
            key={route.query.q || ""}
            className="store-search"
            role="search"
            onSubmit={(event) => {
              event.preventDefault();
              const q = new FormData(event.currentTarget).get("q") || "";
              navigate(`/shop${qs({ q: String(q).trim() })}`);
            }}
          >
            <Search size={17} />
            <input name="q" defaultValue={route.query.q || ""} placeholder="Search products and stores" aria-label="Search products" />
          </form>
          <nav className="store-actions" aria-label="Account">
            {isCustomer && (
              <>
                <a href="#/orders" className={active("/orders")}>
                  <Package size={18} /> <span className="hidden-mobile">Orders</span>
                </a>
                <a href="#/messages" className={active("/messages")} aria-label={`Messages${counts.messages ? `, ${counts.messages} unread` : ""}`}>
                  <MessageSquare size={18} /> <span className="hidden-mobile">Messages</span>
                  {counts.messages > 0 && <em className="count-dot">{counts.messages}</em>}
                </a>
                <a href="#/notifications" className={active("/notifications")} aria-label={`Notifications${counts.notifications ? `, ${counts.notifications} unread` : ""}`}>
                  <Bell size={18} />
                  {counts.notifications > 0 && <em className="count-dot">{counts.notifications}</em>}
                </a>
              </>
            )}
            {!isStaff && (
              <a href="#/cart" className={active("/cart")} aria-label={`Cart, ${cart.count} items`}>
                <ShoppingCart size={18} /> <span className="hidden-mobile">Cart</span>
                {cart.count > 0 && <em className="count-dot">{cart.count}</em>}
              </a>
            )}
            {isStaff && (
              <a href={user.role === "ADMIN" ? "#/admin" : "#/merchant"} className="btn btn-primary btn-sm">
                <LayoutDashboard size={15} /> Back to console
              </a>
            )}
            {user ? (
              <div className="account-menu" ref={menuRef}>
                <button className="nav-btn" onClick={() => setMenu(!menu)} aria-expanded={menu} aria-haspopup="menu">
                  <UserRound size={18} /> <span className="hidden-mobile">{user.fullName?.split(" ")[0] || user.username}</span> <ChevronDown size={14} />
                </button>
                {menu && (
                  <div className="menu" role="menu" onClick={() => setMenu(false)}>
                    <div className="menu-head">
                      <strong>{user.fullName || user.username}</strong>
                      <div className="muted small">{user.email}</div>
                    </div>
                    {isCustomer && (
                      <a href="#/account" role="menuitem">
                        <UserRound size={16} /> My account
                      </a>
                    )}
                    <button role="menuitem" onClick={() => setDark(!dark)}>
                      {dark ? <Sun size={16} /> : <Moon size={16} />} {dark ? "Light theme" : "Dark theme"}
                    </button>
                    <button role="menuitem" onClick={onLogout}>
                      <LogOut size={16} /> Sign out
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <>
                <a href="#/login" className="btn btn-ghost btn-sm">
                  Sign in
                </a>
                <a href="#/register" className="btn btn-primary btn-sm">
                  Create account
                </a>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="store-main">
        {waking && <div className="waking">Starting the servers. Free hosting can take up to a minute after a period of inactivity.</div>}
        {children}
      </main>
      <footer className="store-footer">
        <div className="store-footer-inner">
          <span>Nexora Commerce - a multi-vendor marketplace with a back office for every merchant.</span>
          <span>
            {user ? null : (
              <a className="link" href="#/register?type=merchant">
                Sell on Nexora
              </a>
            )}
          </span>
        </div>
      </footer>
    </div>
  );
}
