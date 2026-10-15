import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/instrument-sans";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/ui.css";
import "./styles/console.css";
import { AdminSessionProvider } from "./auth.jsx";
import { ToastProvider } from "./hooks.jsx";
import App from "./App.jsx";
import { keepServicesAwake } from "./wake.js";

keepServicesAwake();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AdminSessionProvider>
      <ToastProvider>
        <App />
      </ToastProvider>
    </AdminSessionProvider>
  </StrictMode>,
);
