#!/usr/bin/env bash
# End-to-end test of the whole marketplace through the API gateway:
# two merchants list products, a customer buys from both stores in one checkout, the merchants fulfil,
# the customer reviews and messages a seller, and the finance numbers (fees, expenses, P&L) add up.
# Usage: scripts/smoke-test.sh [gateway-url]   (default http://localhost:8080). Needs curl + jq.
set -euo pipefail
GW="${1:-http://localhost:8080}"
PASS=0
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
step() { printf '\n==> %s\n' "$1"; }
ok() { PASS=$((PASS + 1)); printf '    OK  %s\n' "$1"; }
fail() { printf '    FAIL %s\n' "$1"; exit 1; }
call() { # method path token [json] -> prints "status|body"
  local method=$1 path=$2 token=${3:-} data=${4:-}
  local args=(-s -o "$TMP/body" -w '%{http_code}' -X "$method" "$GW$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-d "$data")
  local code; code=$(curl "${args[@]}")
  echo "$code|$(cat "$TMP/body")"
}
download() { # path token -> prints "status content-type bytes"
  curl -s -o "$TMP/file" -w '%{http_code} %{content_type}' "$GW$1" -H "Authorization: Bearer $2"
  echo " $(wc -c < "$TMP/file")"
}
expect() { # expected-status "status|body" label
  local code=${2%%|*}
  [ "$code" = "$1" ] && ok "$3 ($code)" || fail "$3: expected $1, got $code -> ${2#*|}"
}
body() { echo "${1#*|}"; }
json() { body "$1" | jq -r "$2"; }
equals() { # actual expected label (numbers compared numerically)
  if [ "$(jq -n --arg a "$1" --arg b "$2" '($a|tonumber? // $a) == ($b|tonumber? // $b)')" = "true" ]; then ok "$3 = $2"
  else fail "$3: expected $2, got $1"; fi
}

step "Waiting for the gateway and services"
r="000|"
for _ in $(seq 1 90); do
  r=$(call GET "/api/products?size=1" "" ""); [ "${r%%|*}" = "200" ] && break; sleep 5
done
expect 200 "$r" "marketplace reachable through the gateway"

TS=$(date +%s)
step "Accounts: customer and merchant sign-up, separate portals"
r=$(call POST /api/auth/login "" '{"username":"admin","password":"Admin@12345"}'); expect 200 "$r" "admin login"
ADMIN=$(json "$r" .accessToken)
r=$(call POST /api/auth/register "" "{\"accountType\":\"MERCHANT\",\"username\":\"gulf$TS\",\"email\":\"gulf$TS@example.com\",\"password\":\"Merchant123\",\"fullName\":\"Omar Haddad\",\"phone\":\"+971 50 123 4567\",\"storeName\":\"Gulf Gadgets $TS\",\"storeDescription\":\"Phones and accessories\",\"city\":\"Dubai\",\"tradeLicenseNumber\":\"DED-$TS\"}")
expect 201 "$r" "merchant A registers a store"
MA=$(json "$r" .accessToken); MA_ID=$(json "$r" .user.id)
r=$(call POST /api/auth/register "" "{\"accountType\":\"MERCHANT\",\"username\":\"desert$TS\",\"email\":\"desert$TS@example.com\",\"password\":\"Merchant123\",\"storeName\":\"Desert Home $TS\",\"phone\":\"+971 55 765 4321\",\"city\":\"Sharjah\"}")
expect 201 "$r" "merchant B registers a store"
MB=$(json "$r" .accessToken); MB_ID=$(json "$r" .user.id)
r=$(call POST /api/auth/register "" "{\"accountType\":\"MERCHANT\",\"username\":\"dup$TS\",\"email\":\"dup$TS@example.com\",\"password\":\"Merchant123\",\"storeName\":\"Gulf Gadgets $TS\",\"phone\":\"+971 50 000 0000\",\"city\":\"Dubai\"}")
expect 409 "$r" "store names are unique"
r=$(call POST /api/auth/register "" "{\"accountType\":\"CUSTOMER\",\"username\":\"kush$TS\",\"email\":\"kush$TS@example.com\",\"password\":\"Customer123\",\"fullName\":\"Kush Kumar\",\"phone\":\"+971 52 111 2222\"}")
expect 201 "$r" "customer signs up"
CU=$(json "$r" .accessToken); CU_ID=$(json "$r" .user.id)
r=$(call POST /api/auth/login "" "{\"username\":\"kush$TS\",\"password\":\"Customer123\",\"accountType\":\"MERCHANT\"}"); expect 403 "$r" "customer cannot use the merchant login"
r=$(call POST /api/auth/login "" "{\"username\":\"gulf$TS\",\"password\":\"Merchant123\",\"accountType\":\"CUSTOMER\"}"); expect 403 "$r" "merchant cannot use the customer login"
r=$(call GET "/api/merchants?search=Gulf%20Gadgets%20$TS" "" ""); expect 200 "$r" "public store directory"
equals "$(json "$r" '.content[0].storeName')" "Gulf Gadgets $TS" "store found"
r=$(call GET /api/orders "" ""); expect 401 "$r" "orders need a token"
r=$(call GET /api/users "$CU" ""); expect 403 "$r" "customers cannot list users"

step "Merchant inventory: listed and inventory-only products"
r=$(call POST /api/merchant/products "$MA" "{\"sku\":\"PHN-$TS\",\"name\":\"Galaxy S25 Ultra\",\"description\":\"256 GB, titanium\",\"price\":2500,\"costPrice\":1800,\"initialStock\":5,\"reorderLevel\":2,\"supplier\":\"Samsung Gulf\",\"listed\":true}")
expect 201 "$r" "merchant A lists a phone (cost 1,800, price 2,500)"
P1=$(json "$r" .id)
r=$(call POST /api/merchant/products "$MA" "{\"sku\":\"BOX-$TS\",\"name\":\"Shipping boxes (pack of 50)\",\"price\":90,\"costPrice\":40,\"initialStock\":10,\"listed\":false}")
expect 201 "$r" "merchant A adds an inventory-only item (not listed)"
P2=$(json "$r" .id)
r=$(call POST /api/merchant/products "$MB" "{\"sku\":\"LMP-$TS\",\"name\":\"Brass table lamp\",\"price\":120,\"costPrice\":70,\"initialStock\":3,\"reorderLevel\":1}")
expect 201 "$r" "merchant B lists a lamp"
P3=$(json "$r" .id)
r=$(call POST /api/merchant/products "$CU" "{\"sku\":\"X-$TS\",\"name\":\"Nope\",\"price\":1,\"costPrice\":1,\"initialStock\":1}"); expect 403 "$r" "customers cannot add products"
r=$(call GET "/api/products?merchantId=$MA_ID" "" ""); expect 200 "$r" "store A on the marketplace"
equals "$(json "$r" .totalElements)" "1" "only listed products are visible"
r=$(call GET "/api/products/$P2" "" ""); expect 404 "$r" "inventory-only product is hidden"
r=$(call GET "/api/products/$P1" "" ""); expect 200 "$r" "product page (cache miss)"
equals "$(json "$r" .costPrice)" "null" "cost price is never shown to customers"
r=$(call GET "/api/products/$P1" "" ""); expect 200 "$r" "product page (served from Redis)"

step "Checkout across two stores: one order per store, stock reserved"
r=$(call POST /api/orders "$CU" "{\"items\":[{\"productId\":$P1,\"quantity\":2},{\"productId\":$P3,\"quantity\":1}],\"shippingAddress\":\"Apartment 1204, Marina Gate 1, Dubai Marina, Dubai\",\"phone\":\"+971 52 111 2222\"}")
expect 201 "$r" "checkout"
equals "$(json "$r" '.orders | length')" "2" "orders created (one per store)"
equals "$(json "$r" .grandTotal)" "5120" "server-side grand total"
OA=$(json "$r" ".orders[] | select(.merchantId == $MA_ID) | .id"); OB=$(json "$r" ".orders[] | select(.merchantId == $MB_ID) | .id")
r=$(call POST /api/orders "$CU" "{\"items\":[{\"productId\":$P3,\"quantity\":3}],\"shippingAddress\":\"Apartment 1204, Marina Gate 1, Dubai Marina, Dubai\"}")
expect 409 "$r" "no overselling: only 2 lamps left"
r=$(call POST /api/orders/pay "$CU" "{\"orderIds\":[$OA,$OB],\"payment\":{\"method\":\"CARD\",\"cardNumber\":\"4000000000000002\",\"cardHolder\":\"Kush Kumar\",\"expiry\":\"12/30\",\"cvv\":\"123\"}}")
expect 200 "$r" "declined card handled"
equals "$(json "$r" '[.[] | select(.success)] | length')" "0" "no order paid with a declined card"
r=$(call POST /api/orders/pay "$CU" "{\"orderIds\":[$OA,$OB],\"payment\":{\"method\":\"CARD\",\"cardNumber\":\"4111111111111111\",\"cardHolder\":\"Kush Kumar\",\"expiry\":\"12/30\",\"cvv\":\"123\"}}")
expect 200 "$r" "pay both orders at once"
equals "$(json "$r" '[.[] | select(.result.order.status == "CONFIRMED")] | length')" "2" "orders confirmed"
r=$(call GET "/api/merchant/inventory/$P1" "$MA" ""); expect 200 "$r" "merchant stock level"
equals "$(json "$r" .availableQuantity)" "3" "stock updated automatically after payment (5 - 2)"

step "Fulfilment by the merchant"
r=$(call GET /api/merchant/orders "$MA" ""); expect 200 "$r" "merchant A's orders"
equals "$(json "$r" '.content[0].grossProfit')" "1400" "gross profit on the order (2 x (2,500 - 1,800))"
r=$(call GET "/api/merchant/orders/$OA" "$MB" ""); expect 404 "$r" "merchant B cannot see merchant A's order"
r=$(call PUT "/api/merchant/orders/$OA/ship" "$CU" '{"carrier":"Aramex","trackingNumber":"AWB1"}'); expect 403 "$r" "customers cannot ship"
r=$(call PUT "/api/merchant/orders/$OA/ship" "$MA" '{"carrier":"Aramex","trackingNumber":"AWB-44820193","note":"Packed with care"}'); expect 200 "$r" "merchant ships with tracking"
r=$(call PUT "/api/merchant/orders/$OA/deliver" "$MA" ""); expect 200 "$r" "merchant marks delivered"
r=$(call GET "/api/orders/$OA" "$CU" ""); expect 200 "$r" "customer tracks the order"
equals "$(json "$r" .trackingNumber)" "AWB-44820193" "tracking number"
equals "$(json "$r" .status)" "DELIVERED" "status"

step "Cash on delivery cancelled by the seller: stock returns"
r=$(call POST /api/orders "$CU" "{\"items\":[{\"productId\":$P3,\"quantity\":1}],\"shippingAddress\":\"Apartment 1204, Marina Gate 1, Dubai Marina, Dubai\"}")
expect 201 "$r" "second lamp ordered"; OC=$(json "$r" '.orders[0].id')
r=$(call POST "/api/orders/$OC/pay" "$CU" '{"method":"COD"}'); expect 200 "$r" "cash on delivery"
equals "$(json "$r" .order.paymentState)" "PAY_ON_DELIVERY" "payment state"
r=$(call PUT "/api/merchant/orders/$OC/cancel" "$MB" '{"reason":"Item damaged in the warehouse"}'); expect 200 "$r" "merchant B cancels"
r=$(call GET "/api/merchant/inventory/$P3" "$MB" ""); equals "$(json "$r" .availableQuantity)" "2" "lamp stock back to 2"

step "Reviews and messages"
r=$(call POST /api/reviews "$CU" "{\"orderId\":$OB,\"productId\":$P3,\"rating\":5}"); expect 403 "$r" "only delivered purchases can be reviewed"
r=$(call POST /api/reviews "$CU" "{\"orderId\":$OA,\"productId\":$P1,\"rating\":5,\"comment\":\"Genuine phone, fast delivery\"}"); expect 201 "$r" "verified review"
r=$(call GET "/api/reviews?productId=$P1" "" ""); expect 200 "$r" "public product reviews"
equals "$(json "$r" .averageRating)" "5" "average rating"
r=$(call POST /api/conversations "$CU" "{\"merchantId\":$MA_ID,\"message\":\"Does it come with a UAE warranty?\",\"productId\":$P1,\"productName\":\"Galaxy S25 Ultra\"}")
expect 201 "$r" "customer contacts the seller"; CONV=$(json "$r" .conversation.id)
r=$(call GET /api/conversations/unread-count "$MA" ""); equals "$(json "$r" .unread)" "1" "seller's unread messages"
r=$(call POST "/api/conversations/$CONV/messages" "$MA" '{"message":"Yes, 2 years official UAE warranty."}'); expect 201 "$r" "seller replies"
r=$(call GET "/api/conversations/$CONV" "$MB" ""); expect 404 "$r" "other stores cannot read the conversation"

step "Analytics, restocking and finance"
r=$(call GET /api/merchant/analytics "$MA" ""); expect 200 "$r" "sales analytics"
equals "$(json "$r" .revenue)" "5000" "revenue"
equals "$(json "$r" .grossProfit)" "1400" "gross profit"
r=$(call POST "/api/merchant/inventory/$P1/restock" "$MA" '{"quantity":7,"unitCost":1700,"supplier":"Samsung Gulf","reference":"INV-2041"}'); expect 200 "$r" "restock at a new cost"
equals "$(json "$r" .availableQuantity)" "10" "stock after restock"
equals "$(json "$r" .unitCost)" "1730" "weighted average cost ((3 x 1,800 + 7 x 1,700) / 10)"
r=$(call GET /api/merchant/purchases "$MA" ""); equals "$(json "$r" 'length')" "3" "stock purchases (2 opening stocks + 1 restock)"
MONTH=$(date -u +%Y-%m); TODAY=$(date -u +%F)
r=$(call PUT /api/merchant/budgets "$MA" "{\"month\":\"$MONTH\",\"category\":\"MARKETING\",\"amount\":250}"); expect 200 "$r" "monthly marketing budget"
r=$(call POST /api/merchant/expenses "$MA" "{\"category\":\"MARKETING\",\"amount\":300,\"description\":\"Instagram ads\",\"vendor\":\"Meta\",\"expenseDate\":\"$TODAY\"}"); expect 201 "$r" "expense recorded"
r=$(call GET /api/merchant/budgets "$MA" ""); equals "$(json "$r" '.lines[0].status')" "OVER" "budget status"
r=$(call GET /api/merchant/payments/summary "$MA" ""); expect 200 "$r" "payments received"
equals "$(json "$r" .platformFees)" "250" "platform fee (5% of 5,000)"
equals "$(json "$r" .netEarnings)" "4750" "net earnings"
r=$(call GET /api/merchant/finance/profit-loss "$MA" ""); expect 200 "$r" "profit and loss"
equals "$(json "$r" .netProfit)" "850" "net profit (1,400 - 250 fees - 300 expenses)"
for report in "reports/sales/pdf" "reports/inventory/xlsx" "reports/profit-loss/pdf" "reports/profit-loss/xlsx"; do
  d=$(download "/api/merchant/$report" "$MA"); set -- $d
  [ "$1" = "200" ] && [ "$3" -gt 500 ] && ok "$report ($2, $3 bytes)" || fail "$report: $d"
done

step "Notifications and administration"
sleep 2
r=$(call GET /api/notifications "$MA" ""); expect 200 "$r" "merchant notifications"
[ "$(json "$r" '[.content[].type] | index("NEW_ORDER") != null')" = "true" ] && ok "merchant notified of the new order" || fail "no NEW_ORDER notification"
r=$(call GET /api/notifications "$CU" ""); [ "$(json "$r" .totalElements)" -ge 3 ] && ok "customer notifications ($(json "$r" .totalElements))" || fail "too few customer notifications"
r=$(call GET "/api/users?role=MERCHANT" "$ADMIN" ""); expect 200 "$r" "admin lists merchants"
r=$(call GET /api/orders/stats "$ADMIN" ""); expect 200 "$r" "platform statistics"
r=$(call GET /api/payments/platform-summary "$ADMIN" ""); expect 200 "$r" "platform commission revenue"
r=$(call PUT "/api/users/$MB_ID/status" "$ADMIN" '{"enabled":false}'); expect 200 "$r" "admin suspends merchant B"
r=$(call POST /api/auth/login "" "{\"username\":\"desert$TS\",\"password\":\"Merchant123\",\"accountType\":\"MERCHANT\"}"); expect 403 "$r" "suspended merchant cannot log in"
r=$(call GET "/api/merchants/$MB_ID" "" ""); expect 404 "$r" "suspended store hidden from the directory"
r=$(call GET "/api/products?merchantId=$MB_ID" "" ""); equals "$(json "$r" .totalElements)" "0" "suspended store's products removed from the marketplace"
r=$(call PUT "/api/users/$MB_ID/status" "$ADMIN" '{"enabled":true}'); expect 200 "$r" "admin reactivates merchant B"
r=$(call GET "/api/products?merchantId=$MB_ID" "" ""); equals "$(json "$r" .totalElements)" "1" "products back after reactivation"
r=$(call POST /internal/payments "$ADMIN" '{}'); expect 404 "$r" "internal APIs are not exposed by the gateway"

if [ "${KEEP_TEST_DATA:-0}" != "1" ]; then
  step "Clean-up: the marketplace shows only real data (set KEEP_TEST_DATA=1 to keep the test stores)"
  for item in "$P1:$MA" "$P2:$MA" "$P3:$MB"; do
    r=$(call DELETE "/api/merchant/products/${item%%:*}" "${item#*:}" ""); code=${r%%|*}
    [ "${code:0:1}" = "2" ] && ok "test product ${item%%:*} archived" || fail "archive ${item%%:*}: $r"
  done
  for id in "$MA_ID" "$MB_ID" "$CU_ID"; do
    r=$(call PUT "/api/users/$id/status" "$ADMIN" '{"enabled":false}'); expect 200 "$r" "test account $id deactivated"
  done
fi

printf '\nAll %d checks passed.\n' "$PASS"
