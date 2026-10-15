// Deployment settings for the console. This file is used by "npm run dev" and static hosting (Render), where the
// marketplace address comes from VITE_MARKETPLACE_URL at build time instead. In the Docker image nginx answers
// /config.js itself, from the MARKETPLACE_URL environment variable of the container (see nginx.conf.template).
window.NEXORA_CONFIG = window.NEXORA_CONFIG || {};
