# Nexora Control Center: the administrator's console, a separate web application from the marketplace.
# ---------- build stage ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Where the console's "Marketplace" link points (the storefront of the same deployment)
ARG VITE_MARKETPLACE_URL=http://localhost:3000
ENV VITE_MARKETPLACE_URL=$VITE_MARKETPLACE_URL
RUN npm run build

# ---------- runtime stage: nginx serves the console and proxies /api to the gateway ----------
FROM nginx:1.27-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV GATEWAY_URL=http://api-gateway:8080
EXPOSE 80
