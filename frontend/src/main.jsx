import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
// fonts are bundled with the app (no third-party font CDN): Bricolage Grotesque with optical sizes, Instrument Sans
import "@fontsource-variable/bricolage-grotesque/opsz.css";
import "@fontsource-variable/instrument-sans";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/ui.css";
import "./styles/store.css";
import App from "./App.jsx";
import { AuthProvider } from "./auth.jsx";
import { ToastProvider } from "./hooks.jsx";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AuthProvider>
      <ToastProvider>
        <App />
      </ToastProvider>
    </AuthProvider>
  </StrictMode>,
);
