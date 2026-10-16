import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/sora";
import "@fontsource-variable/figtree";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/ui.css";
import "./styles/console.css";
import "./styles/motion.css";
import { AdminSessionProvider } from "./auth.jsx";
import { ToastProvider } from "./hooks.jsx";
import App from "./App.jsx";
import { keepServicesAwake } from "./wake.js";
import { scrollSidewaysWithWheel } from "./wheel.js";

keepServicesAwake();
scrollSidewaysWithWheel();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AdminSessionProvider>
      <ToastProvider>
        <App />
      </ToastProvider>
    </AdminSessionProvider>
  </StrictMode>,
);
