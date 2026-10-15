import { useCallback, useEffect, useRef, useState } from "react";
import {
  BadgePercent,
  Bell,
  ChevronLeft,
  ChevronRight,
  Contrast,
  LayoutDashboard,
  LogOut,
  MapPin,
  MessageSquare,
  Package,
  Search,
  ShoppingCart,
  Star,
  Store,
  Sun,
  User,
} from "lucide-react";
import { api, qs } from "../api.js";
import { useAuth } from "../auth.jsx";
import { CategoryIcon, Logo } from "../components/brand.jsx";
import { Avatar, IconButton, Menu } from "../components/ui.jsx";
import { useCart } from "../cart.js";
import { useClickOutside, useDebounced, useInterval, useLoader, useTheme } from "../hooks.jsx";

/** A row that scrolls sideways. Arrow buttons (for mouse users) and soft edges show when there is more to see. */
function ScrollRail({ label, children }) {
  const viewport = useRef(null);
  const [more, setMore] = useState({ left: false, right: false });
  const measure = useCallback(() => {
    const el = viewport.current;
    if (!el) return;
    const left = el.scrollLeft > 2;
    const right = el.scrollLeft + el.clientWidth < el.scrollWidth - 2;
    setMore((m) => (m.left === left && m.right === right ? m : { left, right }));
  }, []);
  useEffect(() => {
    // Fires once when observing starts, then whenever the rail or its content (departments loading) changes size.
    const observer = new ResizeObserver(() => measure());
    observer.observe(viewport.current);
    observer.observe(viewport.current.firstElementChild);
    return () => observer.disconnect();
  }, [measure]);
  const scroll = (direction) => viewport.current.scrollBy({ left: direction * viewport.current.clientWidth * 0.6, behavior: "smooth" });
  return (
    <div className="scroll-rail" data-more-left={more.left || undefined} data-more-right={more.right || undefined}>
      <button
        type="button"
        className="rail-arrow rail-arrow-left"
        tabIndex={-1}
        aria-label={`Scroll ${label} left`}
        hidden={!more.left}
        onClick={() => scroll(-1)}
      >
        <ChevronLeft />
      </button>
      <div className="scroll-rail-viewport" ref={viewport} onScroll={measure}>
        <div className="scroll-rail-track">{children}</div>
      </div>
      <button
        type="button"
        className="rail-arrow rail-arrow-right"
        tabIndex={-1}
        aria-label={`Scroll ${label} right`}
        hidden={!more.right}
        onClick={() => scroll(1)}
      >
        <ChevronRight />
      </button>
    </div>
  );
}

/** Unread badges for the header (messages and notifications), refreshed every 30 seconds. */
export function useUnreadCounts(enabled) {
  const counts = useLoader(async () => {
    if (!enabled) return { messages: 0, notifications: 0 };
    const [messages, notifications] = await Promise.all([
      api("/api/conversations/unread-count").catch(() => ({ unread: 0 })),
      api("/api/notifications/unread-count").catch(() => ({ unread: 0 })),
    ]);
    return { messages: messages.unread, notifications: notifications.unread };
  }, [enabled]);
  useInterval(() => counts.reload(), 30000, enabled);
  return counts;
}

/** Counts how many times the cart has grown on this page, so the cart icon can jump each time (see motion.css). */
function useAdditions(count) {
  const [added, setAdded] = useState(0);
  const last = useRef(count);
  useEffect(() => {
    if (count > last.current) setAdded((n) => n + 1);
    last.current = count;
  }, [count]);
  return added;
}

export function ThemeToggle() {
  const { dark, toggle } = useTheme();
  const Icon = dark ? Sun : Contrast;
  const text = dark ? "Switch to light mode" : "Switch to dark mode";
  return (
    <button type="button" className="icon-btn" aria-label={text} title={text} onClick={toggle}>
      <Icon key={dark ? "light" : "dark"} className="theme-icon" />
    </button>
  );
}

function HeaderSearch({ categories, route, navigate }) {
  const onSearch = route.path === "/search";
  const [text, setText] = useState(onSearch ? route.query.q || "" : "");
  const [category, setCategory] = useState(onSearch ? route.query.category || "" : "");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const box = useRef(null);
  const term = useDebounced(text.trim(), 220);
  const suggestions = useLoader(() => (term.length >= 2 ? api(`/api/products/suggest${qs({ q: term })}`, { auth: false }) : null), [term]);
  useClickOutside(box, () => setOpen(false), open);

  const items = [
    ...(suggestions.data?.products || []).map((name) => ({ type: "product", label: name })),
    ...(suggestions.data?.categories || []).map((c) => ({ type: "category", label: c.name, id: c.id })),
  ];

  const go = (query, categoryId = category) => {
    setOpen(false);
    navigate(`/search${qs({ q: query.trim(), category: categoryId })}`);
  };

  return (
    <form
      className="header-search"
      ref={box}
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        if (active >= 0 && items[active]) {
          const item = items[active];
          if (item.type === "category") go("", item.id);
          else go(item.label);
        } else {
          go(text);
        }
      }}
    >
      <select aria-label="Department" value={category} onChange={(event) => setCategory(event.target.value)}>
        <option value="">All departments</option>
        {(categories || []).map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </select>
      <input
        type="search"
        value={text}
        placeholder="Search products, brands and stores"
        aria-label="Search Nexora"
        aria-autocomplete="list"
        onChange={(event) => {
          setText(event.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown") {
            event.preventDefault();
            setActive((i) => Math.min(i + 1, items.length - 1));
          } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActive((i) => Math.max(i - 1, -1));
          }
        }}
      />
      <button type="submit" aria-label="Search">
        <Search />
      </button>
      {open && term.length >= 2 && items.length > 0 && (
        <div className="suggestions" role="listbox">
          {items.map((item, index) => (
            <button
              key={`${item.type}-${item.label}`}
              type="button"
              role="option"
              aria-selected={index === active}
              className={index === active ? "active" : ""}
              onClick={() => (item.type === "category" ? go("", item.id) : go(item.label))}
            >
              {item.type === "category" ? <CategoryIcon name={item.label} size={16} /> : <Search />}
              <span>{item.type === "category" ? `In ${item.label}` : item.label}</span>
            </button>
          ))}
        </div>
      )}
    </form>
  );
}

function AccountMenu({ user, logout, navigate }) {
  const firstName = (user.fullName || user.username).split(" ")[0];
  return (
    <Menu
      trigger={({ toggle, open }) => (
        <button type="button" className="account-btn" aria-expanded={open} onClick={toggle}>
          <Avatar name={user.fullName || user.username} />
          <span className="who">
            <small>{user.role === "SELLER" ? user.store?.storeName || "Your store" : "Hello"}</small>
            <b>{firstName}</b>
          </span>
        </button>
      )}
    >
      <div className="menu-head">
        <strong>{user.fullName || user.username}</strong>
        {user.email}
      </div>
      <div className="menu-sep" />
      {user.role === "SELLER" ? (
        <>
          <a className="menu-item" href="#/seller">
            <LayoutDashboard /> Seller Central
          </a>
          <a className="menu-item" href={`#/store/${user.id}`}>
            <Store /> View my store
          </a>
        </>
      ) : (
        <>
          <a className="menu-item" href="#/account/orders">
            <Package /> Orders
          </a>
          <a className="menu-item" href="#/account/messages">
            <MessageSquare /> Messages
          </a>
          <a className="menu-item" href="#/account/reviews">
            <Star /> Reviews
          </a>
          <a className="menu-item" href="#/account/profile">
            <User /> Account details
          </a>
        </>
      )}
      <div className="menu-sep" />
      <button
        type="button"
        className="menu-item"
        onClick={() => {
          logout();
          navigate("/");
        }}
      >
        <LogOut /> Sign out
      </button>
    </Menu>
  );
}

export function StoreLayout({ route, navigate, children }) {
  const { user, isSeller, isCustomer, logout } = useAuth();
  const cart = useCart();
  const added = useAdditions(cart.count);
  const categories = useLoader(() => api("/api/categories", { auth: false }), []);
  const announcements = useLoader(() => api("/api/announcements").catch(() => []), [user?.id]);
  const unread = useUnreadCounts(!!user);
  const region = user?.address?.region || "Dubai";
  const activeCategory = route.path === "/search" ? route.query.category : null;

  return (
    <>
      {(announcements.data || []).slice(0, 1).map((a) => (
        <div key={a.id} className={`announcement announcement-${a.level}`} role="note">
          <div className="container">
            <strong>{a.title}</strong>
            {a.message}
          </div>
        </div>
      ))}
      <header className="store-header">
        <div className="container store-header-main">
          <Logo />
          <HeaderSearch
            key={route.path === "/search" ? `${route.query.q || ""}|${route.query.category || ""}` : "elsewhere"}
            categories={categories.data}
            route={route}
            navigate={navigate}
          />
          <div className="header-actions">
            <ThemeToggle />
            {user && (
              <>
                <IconButton
                  icon={MessageSquare}
                  label="Messages"
                  className="header-messages"
                  count={unread.data?.messages}
                  onClick={() => navigate(isSeller ? "/seller/messages" : "/account/messages")}
                />
                <IconButton
                  icon={Bell}
                  label="Notifications"
                  count={unread.data?.notifications}
                  onClick={() => navigate(isSeller ? "/seller/notifications" : "/account/notifications")}
                />
                <AccountMenu user={user} logout={logout} navigate={navigate} />
              </>
            )}
            {!user && (
              <a className="account-btn" href="#/signin">
                <span className="avatar">
                  <User size={17} />
                </span>
                <span className="who">
                  <small>Hello, sign in</small>
                  <b>Account</b>
                </span>
              </a>
            )}
            {isSeller ? (
              <a className="cart-btn" href="#/seller">
                <LayoutDashboard />
                <span className="cart-label">Seller Central</span>
              </a>
            ) : (
              <a className="cart-btn" href="#/cart" aria-label={`Cart, ${cart.count} items`}>
                <ShoppingCart key={added} className={added ? "bump" : undefined} />
                <span className="cart-label">Cart</span>
                {cart.count > 0 && (
                  <span key={cart.count} className="cart-count">
                    {cart.count}
                  </span>
                )}
              </a>
            )}
          </div>
        </div>
        <nav className="dept-rail" aria-label="Departments">
          <div className="container dept-rail-inner">
            <ScrollRail label="departments">
              <a className={`dept-link ${route.path === "/search" && !activeCategory && route.query.deals ? "active" : ""}`} href="#/search?deals=true">
                <BadgePercent /> Deals
              </a>
              {(categories.data || []).map((c) => (
                <a key={c.id} className={`dept-link ${String(c.id) === activeCategory ? "active" : ""}`} href={`#/search?category=${c.id}`}>
                  <CategoryIcon icon={c.icon} name={c.name} size={16} />
                  {c.name}
                </a>
              ))}
              <a className="dept-link" href="#/stores">
                <Store /> Stores
              </a>
              {(isCustomer || !user) && (
                <span className="deliver-to">
                  <MapPin /> Delivering across the UAE{user?.address ? ` - ${region}` : ""}
                </span>
              )}
            </ScrollRail>
          </div>
        </nav>
      </header>
      <main className="store-main">
        <div className="container">{children}</div>
      </main>
      <footer className="store-footer">
        <div className="container">
          <div className="footer-grid">
            <div className="stack-sm">
              <Logo size={26} />
              <p style={{ maxWidth: "36ch", marginTop: 8 }}>
                A marketplace for independent stores across the Emirates. Prices include 5% VAT; every order is invoiced by the store that sells it.
              </p>
            </div>
            <div>
              <h4>Shop</h4>
              <ul>
                <li>
                  <a href="#/search?deals=true">Today's deals</a>
                </li>
                <li>
                  <a href="#/search?sort=bestselling">Best sellers</a>
                </li>
                <li>
                  <a href="#/search?sort=newest">New arrivals</a>
                </li>
                <li>
                  <a href="#/stores">All stores</a>
                </li>
              </ul>
            </div>
            <div>
              <h4>Your account</h4>
              <ul>
                <li>
                  <a href="#/account/orders">Orders and returns</a>
                </li>
                <li>
                  <a href="#/account/messages">Messages with stores</a>
                </li>
                <li>
                  <a href="#/cart">Cart</a>
                </li>
              </ul>
            </div>
            <div>
              <h4>Sell on Nexora</h4>
              <ul>
                <li>
                  <a href="#/seller/register">Open a store</a>
                </li>
                <li>
                  <a href="#/seller/signin">Seller Central sign in</a>
                </li>
              </ul>
            </div>
          </div>
          <div className="footer-bottom">
            <span>Nexora Marketplace and Seller Inventory Management Platform</span>
            <span>Prices in UAE dirhams (AED), VAT included</span>
          </div>
        </div>
      </footer>
    </>
  );
}
