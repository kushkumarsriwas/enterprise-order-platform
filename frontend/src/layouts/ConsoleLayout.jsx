import { Boxes, LogOut, Moon, Sun } from "lucide-react";

/** Back-office chrome for merchants and administrators. `sections` is [{ label, items: [[path, text, Icon, badge]] }]. */
export default function ConsoleLayout({ user, sections, route, onLogout, dark, setDark, waking, subtitle, children }) {
  const isActive = (to) => route.path === to || (to.split("/").length > 2 && route.path.startsWith(`${to}/`));
  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href={user.role === "ADMIN" ? "#/admin" : "#/merchant"}>
          <Boxes size={22} /> Nexora
        </a>
        <div className="brand-sub">{subtitle}</div>
        <nav aria-label="Console">
          {sections.map((section) => (
            <div key={section.label || "main"} style={{ display: "contents" }}>
              {section.label && <div className="nav-label">{section.label}</div>}
              {section.items.map(([to, text, Icon, badge]) => (
                <a key={to} href={`#${to}`} className={isActive(to) ? "active" : ""} aria-current={isActive(to) ? "page" : undefined}>
                  <Icon size={17} />
                  <span>{text}</span>
                  {badge > 0 && <em className="pill">{badge}</em>}
                </a>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="me">
            <strong>{user.store?.storeName || user.fullName || user.username}</strong>
            <span>{user.role === "ADMIN" ? "Administrator" : user.username}</span>
          </div>
          <div className="row" style={{ gap: 2 }}>
            <button className="icon-btn" onClick={() => setDark(!dark)} aria-label={dark ? "Use light theme" : "Use dark theme"}>
              {dark ? <Sun size={16} /> : <Moon size={16} />}
            </button>
            <button className="icon-btn" onClick={onLogout} aria-label="Sign out">
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>
      <main className="content">
        {waking && <div className="waking">Starting the servers. Free hosting can take up to a minute after a period of inactivity.</div>}
        {children}
      </main>
    </div>
  );
}
