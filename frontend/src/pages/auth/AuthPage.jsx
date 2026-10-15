import { useState } from "react";
import { BarChart3, Boxes, CircleAlert, FileSpreadsheet, MessageSquare, PackageCheck, ShieldCheck, Truck, Wallet } from "lucide-react";
import { qs } from "../../api.js";
import { useAuth } from "../../auth.jsx";
import { Logo } from "../../components/brand.jsx";
import { Button, Field, Input, Notice, Select, Textarea } from "../../components/ui.jsx";
import { EMIRATES } from "../../format.js";
import { useTitle } from "../../hooks.jsx";
import { ThemeToggle } from "../../layouts/StoreLayout.jsx";
import { fieldErrorsOf } from "../account/ProfilePage.jsx";

const USERNAME = /^[a-zA-Z0-9._-]{3,50}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[+0-9 ()-]{7,30}$/;

const SIDES = {
  customer: {
    title: "One cart for the independent stores of the UAE",
    points: [
      [Wallet, "Pay once by card, instant bank transfer or cash on delivery"],
      [Truck, "Every store ships its part; follow each delivery in one place"],
      [MessageSquare, "Ask a store about a product or an order directly"],
      [ShieldCheck, "Cancel free of charge until the store ships"],
    ],
  },
  seller: {
    title: "Run your whole inventory from one place",
    points: [
      [Boxes, "Stock in several warehouses, listed on the marketplace or kept as inventory only"],
      [PackageCheck, "Purchase orders, goods receipts, transfers and stock counts"],
      [BarChart3, "Sales analytics, profit and loss, expenses and budgets"],
      [FileSpreadsheet, "Every list and report as Excel or PDF"],
    ],
  },
};

function validate(form, mode, seller) {
  const errors = {};
  if (mode === "signin") {
    if (!form.username.trim()) errors.username = "Enter your username or email";
    if (!form.password) errors.password = "Enter your password";
    return errors;
  }
  if (!USERNAME.test(form.username)) errors.username = "3 to 50 letters, digits, dots, dashes or underscores";
  if (!EMAIL.test(form.email.trim())) errors.email = "Enter a valid email address";
  if (form.password.length < 8 || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password))
    errors.password = "At least 8 characters with a letter and a digit";
  if (form.phone.trim() && !PHONE.test(form.phone.trim())) errors.phone = "Enter a phone number like +971 50 123 4567";
  if (seller) {
    if (!form.storeName.trim()) errors.storeName = "Your store needs a name";
    if (!form.phone.trim()) errors.phone = "Customers and Nexora use this number to reach the store";
    if (!form.city.trim()) errors.city = "Choose where the store ships from";
    if (form.vatNumber.trim() && !/^\d{15}$/.test(form.vatNumber.trim())) errors.vatNumber = "The TRN has exactly 15 digits";
  }
  return errors;
}

/** Sign in and registration for customers and for sellers (Seller Central). The administrator has a separate console. */
export function AuthPage({ seller, mode, route }) {
  const { login, register } = useAuth();
  const side = SIDES[seller ? "seller" : "customer"];
  const next = route.query.next;
  const [form, setForm] = useState({
    username: "",
    password: "",
    email: "",
    fullName: "",
    phone: "",
    storeName: "",
    storeDescription: "",
    city: "Dubai",
    tradeLicenseNumber: "",
    vatNumber: "",
  });
  const [errors, setErrors] = useState({});
  const [problem, setProblem] = useState(null);
  const [busy, setBusy] = useState(false);
  useTitle(`${mode === "signin" ? "Sign in" : seller ? "Open a store" : "Create an account"}${seller ? " to Seller Central" : ""}`);

  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const link = (path) => `#${path}${qs({ next })}`;
  const otherPortal = seller ? "/signin" : "/seller/signin";

  const submit = async (event) => {
    event.preventDefault();
    const found = validate(form, mode, seller);
    setErrors(found);
    setProblem(null);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      if (mode === "signin") {
        await login(form.username.trim(), form.password, seller ? "SELLER" : "CUSTOMER");
      } else {
        await register({
          accountType: seller ? "SELLER" : "CUSTOMER",
          username: form.username.trim(),
          email: form.email.trim(),
          password: form.password,
          fullName: form.fullName.trim() || null,
          phone: form.phone.trim() || null,
          ...(seller
            ? {
                storeName: form.storeName.trim(),
                storeDescription: form.storeDescription.trim() || null,
                city: form.city.trim(),
                tradeLicenseNumber: form.tradeLicenseNumber.trim() || null,
                vatNumber: form.vatNumber.trim() || null,
              }
            : {}),
        });
      }
      // App redirects to "next" (or the home page / Seller Central) once the user is signed in
    } catch (e) {
      const fields = fieldErrorsOf(e);
      setErrors(fields);
      setProblem({
        message: Object.keys(fields).length ? "Please correct the highlighted fields." : e.message,
        wrongPortal: e.status === 403 && /seller|customer/i.test(e.message),
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <aside className="auth-side">
        <Logo suffix={seller ? "Seller Central" : null} />
        <div>
          <h2>{side.title}</h2>
          <ul>
            {side.points.map(([Icon, text]) => (
              <li key={text}>
                <Icon /> {text}
              </li>
            ))}
          </ul>
        </div>
        <p className="small" style={{ opacity: 0.75 }}>
          Nexora Marketplace and Seller Inventory Management Platform
        </p>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <div className="row-between auth-topbar">
            <span className="auth-mobile-logo">
              <Logo suffix={seller ? "Seller Central" : null} size={26} />
            </span>
            <span className="row" style={{ gap: 6, marginLeft: "auto" }}>
              <a href="#/" className="small">
                Back to the marketplace
              </a>
              <ThemeToggle />
            </span>
          </div>

          <h1>
            {mode === "signin" ? (seller ? "Sign in to Seller Central" : "Sign in") : seller ? "Open your store on Nexora" : "Create your Nexora account"}
          </h1>
          <p className="lead">
            {mode === "signin"
              ? seller
                ? "Use the account of your store."
                : "Welcome back. Use your username or email."
              : seller
                ? "Free to start. Add your products as inventory and list them on the marketplace when you are ready."
                : "It takes a minute. You can save a delivery address at checkout."}
          </p>

          <form className="stack" onSubmit={submit} noValidate>
            {mode === "register" && seller && (
              <fieldset className="stack auth-fieldset">
                <legend>Your store</legend>
                <Field label="Store name" error={errors.storeName} hint="Customers see this name on every product and invoice">
                  <Input value={form.storeName} onChange={set("storeName")} maxLength={80} autoComplete="organization" />
                </Field>
                <div className="form-grid">
                  <Field label="Ships from" error={errors.city}>
                    <Select value={form.city} onChange={set("city")} options={EMIRATES.map((e) => ({ value: e, label: e }))} />
                  </Field>
                  <Field label="Business phone" error={errors.phone}>
                    <Input value={form.phone} onChange={set("phone")} autoComplete="tel" placeholder="+971 4 123 4567" />
                  </Field>
                  <Field label="Trade licence number (optional)" error={errors.tradeLicenseNumber}>
                    <Input value={form.tradeLicenseNumber} onChange={set("tradeLicenseNumber")} maxLength={40} />
                  </Field>
                  <Field label="VAT TRN (optional)" error={errors.vatNumber} hint="15 digits; printed on your tax invoices">
                    <Input value={form.vatNumber} onChange={set("vatNumber")} inputMode="numeric" maxLength={15} />
                  </Field>
                </div>
                <Field label="What do you sell? (optional)">
                  <Textarea value={form.storeDescription} onChange={set("storeDescription")} maxLength={500} rows={3} />
                </Field>
              </fieldset>
            )}

            {mode === "register" ? (
              <fieldset className="stack auth-fieldset">
                {seller && <legend>Your sign-in</legend>}
                <div className="form-grid">
                  <Field label="Full name" error={errors.fullName}>
                    <Input value={form.fullName} onChange={set("fullName")} autoComplete="name" maxLength={120} />
                  </Field>
                  {!seller && (
                    <Field label="Mobile number (optional)" error={errors.phone}>
                      <Input value={form.phone} onChange={set("phone")} autoComplete="tel" placeholder="+971 50 123 4567" />
                    </Field>
                  )}
                  <Field label="Email" error={errors.email}>
                    <Input type="email" value={form.email} onChange={set("email")} autoComplete="email" maxLength={120} />
                  </Field>
                  <Field label="Username" error={errors.username}>
                    <Input value={form.username} onChange={set("username")} autoComplete="username" maxLength={50} autoCapitalize="none" spellCheck={false} />
                  </Field>
                  <Field label="Password" error={errors.password} hint="At least 8 characters with a letter and a digit" className={seller ? "" : "span-2"}>
                    <Input type="password" value={form.password} onChange={set("password")} autoComplete="new-password" maxLength={72} />
                  </Field>
                </div>
              </fieldset>
            ) : (
              <>
                <Field label="Username or email" error={errors.username}>
                  <Input value={form.username} onChange={set("username")} autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus />
                </Field>
                <Field label="Password" error={errors.password}>
                  <Input type="password" value={form.password} onChange={set("password")} autoComplete="current-password" />
                </Field>
              </>
            )}

            {problem && (
              <Notice tone="bad" icon={CircleAlert}>
                {problem.message}
                {problem.wrongPortal && (
                  <div style={{ marginTop: 6 }}>
                    <a href={link(otherPortal)} className="strong">
                      {seller ? "Go to the customer sign-in" : "Go to the Seller Central sign-in"}
                    </a>
                  </div>
                )}
              </Notice>
            )}

            <Button type="submit" variant="primary" size="lg" block loading={busy}>
              {mode === "signin" ? "Sign in" : seller ? "Open my store" : "Create account"}
            </Button>
          </form>

          <div className="auth-switch stack-sm">
            {mode === "signin" ? (
              <span>
                {seller ? "New to selling on Nexora?" : "New to Nexora?"}{" "}
                <a href={link(seller ? "/seller/register" : "/register")} className="strong">
                  {seller ? "Open a store" : "Create an account"}
                </a>
              </span>
            ) : (
              <span>
                Already have an account?{" "}
                <a href={link(seller ? "/seller/signin" : "/signin")} className="strong">
                  Sign in
                </a>
              </span>
            )}
            <span>
              {seller ? "Shopping instead? " : "Selling on Nexora? "}
              <a href={`#${otherPortal}`}>{seller ? "Customer sign-in" : "Seller Central sign-in"}</a>
            </span>
          </div>
        </div>
      </main>
    </div>
  );
}
