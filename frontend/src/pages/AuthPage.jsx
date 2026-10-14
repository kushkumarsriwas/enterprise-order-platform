import { useState } from "react";
import { Boxes, Building2, LogIn, ShoppingBag, Store, UserPlus } from "lucide-react";
import { useAuth } from "../auth.jsx";
import { Alert, Field } from "../components/ui.jsx";

const CITIES = ["Dubai", "Abu Dhabi", "Sharjah", "Ajman", "Ras Al Khaimah", "Fujairah", "Umm Al Quwain", "Al Ain"];
const EMPTY = { username: "", email: "", password: "", fullName: "", phone: "", storeName: "", storeDescription: "", city: "Dubai", tradeLicenseNumber: "" };

/** One page, two account types: customers shop, merchants sell and run their back office. */
export default function AuthPage({ mode, route, navigate }) {
  const { login, register, sessionExpired } = useAuth();
  const [type, setType] = useState(route.query.type === "merchant" ? "MERCHANT" : "CUSTOMER");
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState(sessionExpired ? "Your session has expired. Please sign in again." : "");
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const merchant = type === "MERCHANT";
  const isLogin = mode === "login";
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const next = route.query.next ? `&next=${encodeURIComponent(route.query.next)}` : "";

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      if (isLogin) {
        await login(form.username.trim(), form.password, type);
      } else {
        const body = { ...form, accountType: type };
        if (!merchant) {
          delete body.storeName;
          delete body.storeDescription;
          delete body.city;
          delete body.tradeLicenseNumber;
        }
        await register(body);
      }
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.details || {});
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <section className="auth-hero">
        <a className="brand" href="#/shop" style={{ padding: 0, fontSize: 22 }}>
          <Boxes size={28} /> Nexora Commerce
        </a>
        <div className="stack" style={{ gap: 18 }}>
          <h1>E-commerce and inventory management on one platform</h1>
          <p className="lead">
            Customers shop from independent stores across the UAE in one place. Merchants sell on the marketplace and run their
            business from a complete back office: products and stock, orders and delivery, payments, expenses and profit.
          </p>
          <div className="auth-sides">
            <div className="auth-side">
              <h2>
                <ShoppingBag size={17} /> For customers
              </h2>
              <ul>
                <li>One cart across many stores</li>
                <li>Card, UPI or cash on delivery</li>
                <li>Order tracking and chat with sellers</li>
              </ul>
            </div>
            <div className="auth-side">
              <h2>
                <Building2 size={17} /> For merchants
              </h2>
              <ul>
                <li>Inventory with costs, margins and stock alerts</li>
                <li>Order fulfilment, refunds and reviews</li>
                <li>Analytics, budgets, profit and loss, PDF and Excel reports</li>
              </ul>
            </div>
          </div>
        </div>
        <p className="auth-tech">Built with Java 21 and Spring Boot microservices, PostgreSQL, MySQL, Oracle, Redis, Docker and Kubernetes.</p>
      </section>

      <section className="auth-main">
        <div className="auth-card">
          <div>
            <h2>{isLogin ? "Sign in" : "Create your account"}</h2>
            <p className="muted">{isLogin ? "Choose the account you want to use." : "Shop as a customer, or open a store as a merchant."}</p>
          </div>
          <div className="type-picker" role="radiogroup" aria-label="Account type">
            <button type="button" role="radio" aria-checked={!merchant} className={!merchant ? "active" : ""} onClick={() => setType("CUSTOMER")}>
              <strong>
                <ShoppingBag size={16} /> Customer
              </strong>
              <span>Shop products from stores across the UAE</span>
            </button>
            <button type="button" role="radio" aria-checked={merchant} className={merchant ? "active" : ""} onClick={() => setType("MERCHANT")}>
              <strong>
                <Store size={16} /> Merchant
              </strong>
              <span>Sell online and manage your inventory and finances</span>
            </button>
          </div>

          <form onSubmit={submit} className="stack">
            <Alert onClose={() => setError("")}>{error}</Alert>
            {isLogin ? (
              <>
                <Field label="Username or email" error={fieldErrors.username}>
                  <input value={form.username} onChange={set("username")} autoComplete="username" required />
                </Field>
                <Field label="Password" error={fieldErrors.password}>
                  <input type="password" value={form.password} onChange={set("password")} autoComplete="current-password" required />
                </Field>
                {merchant && <p className="muted small">Platform administrators also sign in here.</p>}
              </>
            ) : (
              <div className="form-grid">
                {merchant && (
                  <>
                    <Field label="Store name" error={fieldErrors.storeName} className="span-2" hint="Shown to customers on the marketplace">
                      <input value={form.storeName} onChange={set("storeName")} maxLength={80} required />
                    </Field>
                    <Field label="City" error={fieldErrors.city}>
                      <select value={form.city} onChange={set("city")}>
                        {CITIES.map((city) => (
                          <option key={city}>{city}</option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Trade licence number" error={fieldErrors.tradeLicenseNumber} hint="Optional">
                      <input value={form.tradeLicenseNumber} onChange={set("tradeLicenseNumber")} maxLength={40} />
                    </Field>
                    <Field label="What do you sell?" error={fieldErrors.storeDescription} className="span-2" hint="Optional, shown on your store page">
                      <textarea rows={2} value={form.storeDescription} onChange={set("storeDescription")} maxLength={500} />
                    </Field>
                  </>
                )}
                <Field label={merchant ? "Owner's full name" : "Full name"} error={fieldErrors.fullName}>
                  <input value={form.fullName} onChange={set("fullName")} autoComplete="name" />
                </Field>
                <Field label="Mobile number" error={fieldErrors.phone} hint={merchant ? null : "Optional, used for delivery"}>
                  <input value={form.phone} onChange={set("phone")} autoComplete="tel" placeholder="+971 50 123 4567" required={merchant} />
                </Field>
                <Field label="Username" error={fieldErrors.username}>
                  <input value={form.username} onChange={set("username")} autoComplete="username" required />
                </Field>
                <Field label="Email" error={fieldErrors.email}>
                  <input type="email" value={form.email} onChange={set("email")} autoComplete="email" required />
                </Field>
                <Field label="Password" error={fieldErrors.password} hint="At least 8 characters with a letter and a digit" className="span-2">
                  <input type="password" value={form.password} onChange={set("password")} autoComplete="new-password" required />
                </Field>
              </div>
            )}
            <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
              {isLogin ? <LogIn size={17} /> : <UserPlus size={17} />}
              {busy ? "Please wait..." : isLogin ? `Sign in as ${merchant ? "merchant" : "customer"}` : merchant ? "Open my store" : "Create account"}
            </button>
          </form>
          <p className="auth-switch">
            {isLogin ? (
              <>
                New to Nexora?{" "}
                <a className="link" href={`#/register?type=${merchant ? "merchant" : "customer"}${next}`}>
                  Create an account
                </a>
              </>
            ) : (
              <>
                Already have an account?{" "}
                <a className="link" href={`#/login?type=${merchant ? "merchant" : "customer"}${next}`}>
                  Sign in
                </a>
              </>
            )}
            {" - "}
            <button className="link" onClick={() => navigate("/shop")}>
              Browse the marketplace
            </button>
          </p>
        </div>
      </section>
    </div>
  );
}
