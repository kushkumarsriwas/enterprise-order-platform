#!/usr/bin/env bash
# End-to-end smoke test of the whole platform through the API gateway.
# Usage: scripts/smoke-test.sh [gateway-url]   (default http://localhost:8080). Needs curl + jq.
set -euo pipefail
GW="${1:-http://localhost:8080}"
PASS=0
step() { printf '\n==> %s\n' "$1"; }
ok() { PASS=$((PASS + 1)); printf '    OK  %s\n' "$1"; }
fail() { printf '    FAIL %s\n' "$1"; exit 1; }
call() { # method path token [json] -> prints "status|body"
  local method=$1 path=$2 token=${3:-} data=${4:-}
  local args=(-s -o /tmp/smoke-body -w '%{http_code}' -X "$method" "$GW$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-d "$data")
  local code; code=$(curl "${args[@]}")
  echo "$code|$(cat /tmp/smoke-body)"
}
expect() { # expected-status "status|body" label
  local code=${2%%|*}
  [ "$code" = "$1" ] && ok "$3 ($code)" || fail "$3: expected $1, got $code -> ${2#*|}"
}
body() { echo "${1#*|}"; }

step "Waiting for the gateway and services"
for i in $(seq 1 90); do
  r=$(call GET /api/products?size=1 "" ""); [ "${r%%|*}" = "200" ] && break; sleep 5
done
expect 200 "$r" "catalogue reachable through the gateway"

step "Authentication"
r=$(call POST /api/auth/login "" '{"username":"admin","password":"Admin@12345"}'); expect 200 "$r" "admin login"
ADMIN=$(body "$r" | jq -r .accessToken)
USER="smoke$(date +%s)"
r=$(call POST /api/auth/register "" "{\"username\":\"$USER\",\"email\":\"$USER@example.com\",\"password\":\"Smoke12345\",\"fullName\":\"Smoke Test\"}")
expect 201 "$r" "customer registration"
CUSTOMER=$(body "$r" | jq -r .accessToken)
r=$(call GET /api/orders "" ""); expect 401 "$r" "orders need a token"
r=$(call GET /api/users "$CUSTOMER" ""); expect 403 "$r" "customer cannot list users"

step "Catalogue (admin creates a product with 3 units)"
SKU="SMK-$(date +%s)"
r=$(call POST /api/products "$ADMIN" "{\"sku\":\"$SKU\",\"name\":\"Smoke Test Headset\",\"price\":250.00,\"initialStock\":3,\"reorderLevel\":1}")
expect 201 "$r" "product created"
PID=$(body "$r" | jq -r .id)
r=$(call POST /api/products "$CUSTOMER" "{\"sku\":\"X-$SKU\",\"name\":\"Nope\",\"price\":1,\"initialStock\":1}"); expect 403 "$r" "customer cannot create products"
r=$(call GET "/api/products/$PID" "" ""); expect 200 "$r" "product details (cache miss)"
r=$(call GET "/api/products/$PID" "" ""); expect 200 "$r" "product details (served from cache)"

step "Order 1: place -> pay by card -> ship -> deliver"
r=$(call POST /api/orders "$CUSTOMER" "{\"items\":[{\"productId\":$PID,\"quantity\":2}],\"shippingAddress\":\"Office 12, Business Bay, Dubai, UAE\"}")
expect 201 "$r" "order placed (stock reserved)"
O1=$(body "$r" | jq -r .id); [ "$(body "$r" | jq -r .totalAmount)" = "500" ] || [ "$(body "$r" | jq -r .totalAmount)" = "500.0" ] || [ "$(body "$r" | jq -r .totalAmount)" = "500.00" ] && ok "server-side total = 500.00" || fail "unexpected total $(body "$r" | jq -r .totalAmount)"
r=$(call POST /api/orders "$CUSTOMER" "{\"items\":[{\"productId\":$PID,\"quantity\":2}],\"shippingAddress\":\"Office 12, Business Bay, Dubai, UAE\"}")
expect 409 "$r" "second order rejected: only 1 unit left (no overselling)"
r=$(call POST "/api/orders/$O1/pay" "$CUSTOMER" '{"method":"CARD","cardNumber":"4000000000000002","cardHolder":"Smoke Test","expiry":"12/30","cvv":"123"}')
expect 200 "$r" "declined card handled"; [ "$(body "$r" | jq -r .order.status)" = "PENDING" ] && ok "order still PENDING after decline" || fail "order should stay pending"
r=$(call POST "/api/orders/$O1/pay" "$CUSTOMER" '{"method":"CARD","cardNumber":"4111111111111111","cardHolder":"Smoke Test","expiry":"12/30","cvv":"123"}')
expect 200 "$r" "card payment"; [ "$(body "$r" | jq -r .order.status)" = "CONFIRMED" ] && ok "order CONFIRMED" || fail "order not confirmed: $(body "$r")"
r=$(call PUT "/api/orders/$O1/status" "$CUSTOMER" '{"status":"SHIPPED"}'); expect 403 "$r" "customer cannot ship"
r=$(call PUT "/api/orders/$O1/status" "$ADMIN" '{"status":"SHIPPED","note":"Aramex AWB 1234"}'); expect 200 "$r" "admin ships"
r=$(call PUT "/api/orders/$O1/status" "$ADMIN" '{"status":"DELIVERED"}'); expect 200 "$r" "admin delivers"

step "Order 2: cash on delivery, then cancel (stock returns)"
r=$(call POST /api/orders "$CUSTOMER" "{\"items\":[{\"productId\":$PID,\"quantity\":1}],\"shippingAddress\":\"Office 12, Business Bay, Dubai, UAE\"}")
expect 201 "$r" "order placed"; O2=$(body "$r" | jq -r .id)
r=$(call POST "/api/orders/$O2/pay" "$CUSTOMER" '{"method":"COD"}'); expect 200 "$r" "cash on delivery accepted"
[ "$(body "$r" | jq -r .order.paymentState)" = "PAY_ON_DELIVERY" ] && ok "payment state PAY_ON_DELIVERY" || fail "wrong payment state"
r=$(call PUT "/api/orders/$O2/cancel" "$CUSTOMER" '{"reason":"Ordered by mistake"}'); expect 200 "$r" "order cancelled"
sleep 1
r=$(call GET "/api/products/$PID" "" ""); AVAIL=$(body "$r" | jq -r .availableQuantity)
[ "$AVAIL" = "1" ] && ok "stock back to 1 after cancellation" || fail "expected 1 available, got $AVAIL"

step "Payments, notifications, statistics"
r=$(call GET /api/payments "$CUSTOMER" ""); expect 200 "$r" "payment history"
r=$(call GET "/api/inventory/$PID/movements" "$ADMIN" ""); expect 200 "$r" "stock audit trail"
sleep 2
r=$(call GET /api/notifications "$CUSTOMER" ""); expect 200 "$r" "customer notifications"
[ "$(body "$r" | jq -r .totalElements)" -ge 3 ] && ok "notifications delivered ($(body "$r" | jq -r .totalElements))" || fail "too few notifications"
r=$(call GET /api/orders/stats "$ADMIN" ""); expect 200 "$r" "admin statistics"
r=$(call GET /internal/payments "$ADMIN" ""); expect 404 "$r" "internal APIs are not exposed by the gateway"

printf '\nALL %d CHECKS PASSED\n' "$PASS"
