-- Optional second step of the demo: spreads the activity created by scripts/demo-data.mjs over the last five and a
-- half months (sales growing month by month), so the dashboards, trends, P&L by month and stock flow charts have
-- history. The age curve below must match HISTORY_DAYS in demo-data.mjs, which sizes the monthly expenses.
-- The API cannot create orders in the past (on purpose), so this is the one place that edits rows directly.
-- Run it once, right after demo-data.mjs, on the shared PostgreSQL database:
--   docker compose exec -T postgres psql -U postgres -d enterprise < scripts/demo-backdate.sql
-- Then clear the product cache (redis-cli FLUSHALL) so "new arrivals" and the home page use the new dates.
-- Orders waiting for payment or for shipping keep today's date. Every checkout is moved by one random age and all
-- of its timestamps (status history, payments, stock movements, reservations, reviews, notifications and e-mails)
-- move with it consistently; order numbers (ORD-yymmdd-xxxxxx) are renumbered to match the new date everywhere.

BEGIN;
SELECT setseed(0.2026);

-- ---------------------------------------------------------------- orders: one age per checkout
-- A checkout with products of several stores makes one order per store, a few milliseconds apart: they keep
-- moving together. Orders waiting for payment must stay recent (unpaid orders expire after 30 minutes); a checkout
-- where one store has delivered and another has not shipped yet is a day or two old.
CREATE TEMP TABLE demo_age ON COMMIT DROP AS
WITH ordered AS (
  SELECT o.id, o.user_id, o.status,
         CASE WHEN o.created_at - lag(o.created_at) OVER (PARTITION BY o.user_id ORDER BY o.created_at, o.id) < interval '60 milliseconds'
              THEN 0 ELSE 1 END AS starts_checkout,
         o.created_at
  FROM order_service.orders o
), grouped AS (
  SELECT id, user_id, status, sum(starts_checkout) OVER (PARTITION BY user_id ORDER BY created_at, id) AS checkout_no
  FROM ordered
), checkout_age AS (
  SELECT user_id, checkout_no,
         CASE
           WHEN bool_and(status IN ('PENDING', 'CONFIRMED')) THEN 0
           WHEN bool_or(status IN ('PENDING', 'CONFIRMED')) THEN 1 + random() * 1.5
           WHEN bool_or(status = 'SHIPPED') THEN 1 + random() * 2
           ELSE 3 + 160 * (1 - sqrt(random()))
         END AS age_days
  FROM grouped
  GROUP BY user_id, checkout_no
)
SELECT g.id, c.age_days
FROM grouped g JOIN checkout_age c ON c.user_id = g.user_id AND c.checkout_no = g.checkout_no
ORDER BY g.id;

CREATE TEMP TABLE demo_orders ON COMMIT DROP AS
WITH moved AS (
  SELECT o.id, o.order_number AS old_number, o.status, o.seller_id, o.user_id, o.created_at AS old_created,
         o.created_at - a.age_days * interval '1 day' AS new_created,
         o.shipped_at, o.delivered_at, a.age_days
  FROM order_service.orders o JOIN demo_age a ON a.id = o.id
)
SELECT id, old_number, status, seller_id, user_id, old_created, new_created,
       CASE WHEN shipped_at IS NULL THEN NULL
            WHEN status = 'SHIPPED' THEN new_created + age_days * 0.5 * interval '1 day'
            ELSE new_created + least(age_days * 0.3, 1.4) * interval '1 day' END AS new_shipped,
       CASE WHEN delivered_at IS NULL THEN NULL
            ELSE new_created + least(age_days * 0.3, 1.4) * interval '1 day' + least(age_days * 0.4, 2.2) * interval '1 day' END AS new_delivered,
       'ORD-' || to_char(new_created AT TIME ZONE 'UTC', 'YYMMDD') || substr(old_number, 11) AS new_number
FROM moved;

UPDATE order_service.orders o
SET created_at = d.new_created,
    shipped_at = d.new_shipped,
    delivered_at = d.new_delivered,
    payment_started_at = CASE WHEN o.payment_started_at IS NULL THEN NULL ELSE least(d.new_created + interval '2 minutes', now()) END,
    updated_at = least(coalesce(d.new_delivered, d.new_shipped, d.new_created + interval '5 minutes'), now()),
    order_number = d.new_number
FROM demo_orders d
WHERE o.id = d.id;

UPDATE order_service.order_status_history h
SET changed_at = least(CASE h.to_status
                         WHEN 'PENDING' THEN d.new_created
                         WHEN 'CONFIRMED' THEN d.new_created + interval '3 minutes'
                         WHEN 'SHIPPED' THEN d.new_shipped
                         WHEN 'DELIVERED' THEN d.new_delivered
                         ELSE d.new_created + interval '40 minutes' END, now())
FROM demo_orders d
WHERE h.order_id = d.id;

-- Review notifications do not name an order: pair them with their review (same store or customer, same order of
-- creation) before the reviews move, so they can take the review's new time afterwards.
CREATE TEMP TABLE demo_review_note ON COMMIT DROP AS
SELECT n.id AS notification_id, r.id AS review_id, 'NEW_REVIEW' AS kind
FROM (SELECT id, user_id, row_number() OVER (PARTITION BY user_id ORDER BY id) AS k
      FROM notification_service.notifications WHERE type = 'NEW_REVIEW') n
JOIN (SELECT id, seller_id, row_number() OVER (PARTITION BY seller_id ORDER BY created_at, id) AS k
      FROM order_service.reviews) r ON r.seller_id = n.user_id AND r.k = n.k
UNION ALL
SELECT n.id, r.id, 'REVIEW_REPLY'
FROM (SELECT id, user_id, row_number() OVER (PARTITION BY user_id ORDER BY id) AS k
      FROM notification_service.notifications WHERE type = 'REVIEW_REPLY') n
JOIN (SELECT id, customer_id, row_number() OVER (PARTITION BY customer_id ORDER BY seller_replied_at, id) AS k
      FROM order_service.reviews WHERE seller_replied_at IS NOT NULL) r ON r.customer_id = n.user_id AND r.k = n.k;

UPDATE order_service.reviews r
SET created_at = t.at,
    seller_replied_at = CASE WHEN r.seller_replied_at IS NULL THEN NULL ELSE least(t.at + interval '7 hours', now()) END
FROM (SELECT r2.id, least(d.new_delivered + (0.5 + random() * 3) * interval '1 day', now() - interval '2 hours') AS at
      FROM order_service.reviews r2 JOIN demo_orders d ON d.id = r2.order_id
      WHERE d.new_delivered IS NOT NULL) t
WHERE r.id = t.id;

-- ---------------------------------------------------------------- payments of those orders
UPDATE payment_service.payments p
SET created_at = least(d.new_created + CASE WHEN p.payment_status = 'FAILED' THEN interval '1 minute' ELSE interval '2 minutes' END, now()),
    updated_at = least(CASE
                         WHEN p.payment_status IN ('REFUNDED', 'CANCELLED') THEN d.new_created + interval '40 minutes'
                         WHEN p.payment_method = 'COD' AND d.new_delivered IS NOT NULL THEN d.new_delivered
                         ELSE d.new_created + interval '2 minutes' END, now()),
    order_number = d.new_number,
    transaction_reference = replace(p.transaction_reference, d.old_number, d.new_number),
    provider_reference = replace(p.provider_reference, d.old_number, d.new_number)
FROM demo_orders d
WHERE p.order_id = d.id;

-- ---------------------------------------------------------------- stock reserved, sold and returned for those orders
UPDATE inventory_service.stock_movements m
SET created_at = least(CASE m.type
                         WHEN 'RESERVED' THEN d.new_created
                         WHEN 'SOLD' THEN d.new_created + interval '3 minutes'
                         ELSE d.new_created + interval '40 minutes' END, now()),
    reference = d.new_number
FROM demo_orders d
WHERE m.reference = d.old_number AND m.seller_id = d.seller_id;

UPDATE inventory_service.stock_reservations r
SET created_at = d.new_created,
    updated_at = least(d.new_created + interval '3 minutes', now()),
    expires_at = d.new_created + interval '30 minutes',
    order_ref = d.new_number
FROM demo_orders d
WHERE r.order_ref = d.old_number;

UPDATE notification_service.messages m
SET order_number = d.new_number
FROM demo_orders d
WHERE m.order_number = d.old_number;

-- ---------------------------------------------------------------- notifications and e-mails about those orders
-- They name the order number in the title or text: they get the new number and the time of the event they report.
UPDATE notification_service.notifications n
SET title = replace(n.title, d.old_number, d.new_number),
    message = replace(n.message, d.old_number, d.new_number),
    created_at = least(CASE n.type
                         WHEN 'ORDER_PLACED' THEN d.new_created
                         WHEN 'PAYMENT_FAILED' THEN d.new_created + interval '1 minute'
                         WHEN 'ORDER_SHIPPED' THEN coalesce(d.new_shipped, d.new_created)
                         WHEN 'ORDER_DELIVERED' THEN coalesce(d.new_delivered, d.new_created)
                         WHEN 'ORDER_CANCELLED' THEN d.new_created + interval '40 minutes'
                         ELSE d.new_created + interval '3 minutes' END, now())
FROM demo_orders d
WHERE strpos(n.title, d.old_number) > 0 OR strpos(n.message, d.old_number) > 0;

UPDATE notification_service.email_outbox e
SET subject = replace(e.subject, d.old_number, d.new_number),
    body = replace(e.body, d.old_number, d.new_number),
    created_at = least(CASE e.notification_type
                         WHEN 'ORDER_PLACED' THEN d.new_created
                         WHEN 'ORDER_SHIPPED' THEN coalesce(d.new_shipped, d.new_created)
                         WHEN 'ORDER_DELIVERED' THEN coalesce(d.new_delivered, d.new_created)
                         WHEN 'ORDER_CANCELLED' THEN d.new_created + interval '40 minutes'
                         ELSE d.new_created + interval '3 minutes' END, now())
FROM demo_orders d
WHERE strpos(e.subject, d.old_number) > 0 OR strpos(e.body, d.old_number) > 0;
-- "3 orders placed" (one checkout, several stores) names no order: match it to its checkout by customer and time.
UPDATE notification_service.notifications n
SET created_at = least(x.new_created, now())
FROM (SELECT DISTINCT ON (n2.id) n2.id, d.new_created
      FROM notification_service.notifications n2
      JOIN demo_orders d ON d.user_id = n2.user_id AND abs(extract(epoch FROM d.old_created - n2.created_at)) < 5
      WHERE n2.type = 'ORDER_PLACED' AND strpos(n2.title, 'ORD-') = 0
      ORDER BY n2.id, abs(extract(epoch FROM d.old_created - n2.created_at))) x
WHERE n.id = x.id;

UPDATE notification_service.email_outbox e
SET created_at = least(x.new_created, now())
FROM (SELECT DISTINCT ON (e2.id) e2.id, d.new_created
      FROM notification_service.email_outbox e2
      JOIN demo_orders d ON d.user_id = e2.user_id AND abs(extract(epoch FROM d.old_created - e2.created_at)) < 5
      WHERE e2.notification_type = 'ORDER_PLACED' AND strpos(e2.subject, 'ORD-') = 0
      ORDER BY e2.id, abs(extract(epoch FROM d.old_created - e2.created_at))) x
WHERE e.id = x.id;
UPDATE notification_service.email_outbox SET sent_at = least(created_at + interval '20 seconds', now()) WHERE sent_at IS NOT NULL;

UPDATE notification_service.notifications n
SET created_at = CASE x.kind WHEN 'NEW_REVIEW' THEN r.created_at ELSE coalesce(r.seller_replied_at, r.created_at) END
FROM demo_review_note x JOIN order_service.reviews r ON r.id = x.review_id
WHERE n.id = x.notification_id;

-- People have read what is older than two days; only the latest news stays unread.
UPDATE notification_service.notifications SET is_read = true WHERE created_at < now() - interval '2 days';

-- ---------------------------------------------------------------- the stores: catalogue, suppliers and warehouses existed before the first order
UPDATE inventory_service.products SET created_at = now() - (168 + random() * 12) * interval '1 day';
UPDATE inventory_service.products SET updated_at = greatest(created_at, now() - random() * 12 * interval '1 day');
UPDATE inventory_service.stock_movements m
SET created_at = p.created_at
FROM inventory_service.products p
WHERE m.product_id = p.id AND m.type = 'OPENING_STOCK';
UPDATE inventory_service.suppliers SET created_at = now() - interval '185 days', updated_at = now() - interval '185 days';
UPDATE inventory_service.warehouses SET created_at = now() - interval '188 days';

-- purchase orders: received four months ago (the stock that sold since), partly received last week, ordered two days
-- ago (drafts stay as they are)
CREATE TEMP TABLE demo_po ON COMMIT DROP AS
SELECT id, seller_id,
       CASE status WHEN 'RECEIVED' THEN 128 WHEN 'PARTIALLY_RECEIVED' THEN 9 WHEN 'ORDERED' THEN 2 ELSE 0 END AS ordered_ago,
       CASE status WHEN 'RECEIVED' THEN 121 WHEN 'PARTIALLY_RECEIVED' THEN 6 ELSE 0 END AS received_ago
FROM inventory_service.purchase_orders
WHERE status IN ('RECEIVED', 'PARTIALLY_RECEIVED', 'ORDERED');

UPDATE inventory_service.purchase_orders po
SET created_at = now() - (x.ordered_ago + 1) * interval '1 day',
    order_date = (now() - x.ordered_ago * interval '1 day')::date,
    -- the received order arrived two days early; the partly received one is late (it shows as overdue)
    expected_date = CASE po.status
                      WHEN 'ORDERED' THEN po.expected_date
                      WHEN 'RECEIVED' THEN (now() - (x.received_ago - 2) * interval '1 day')::date
                      ELSE (now() - (x.received_ago + 1) * interval '1 day')::date END,
    received_at = CASE WHEN po.received_at IS NULL THEN NULL ELSE now() - x.received_ago * interval '1 day' END,
    updated_at = now() - least(x.received_ago, x.ordered_ago) * interval '1 day'
FROM demo_po x
WHERE po.id = x.id;

UPDATE inventory_service.goods_receipts g
SET received_at = now() - x.received_ago * interval '1 day' + interval '3 hours'
FROM demo_po x
WHERE g.purchase_order_id = x.id;

UPDATE inventory_service.stock_movements m
SET created_at = g.received_at
FROM inventory_service.goods_receipts g
WHERE m.type = 'RECEIVED' AND m.reference = g.number AND m.seller_id = g.seller_id;

-- transfers, counts and adjustments happened over the last weeks
UPDATE inventory_service.stock_transfers SET created_at = now() - interval '12 days';
UPDATE inventory_service.stock_movements m
SET created_at = t.created_at
FROM inventory_service.stock_transfers t
WHERE m.type IN ('TRANSFER_OUT', 'TRANSFER_IN') AND m.reference = t.number AND m.seller_id = t.seller_id;

UPDATE inventory_service.stock_counts SET created_at = now() - interval '5 days', completed_at = now() - interval '5 days' + interval '3 hours'
WHERE status = 'COMPLETED';
UPDATE inventory_service.stock_movements m
SET created_at = c.completed_at
FROM inventory_service.stock_counts c
WHERE m.type = 'COUNT_ADJUSTMENT' AND m.reference = c.number AND m.seller_id = c.seller_id;

UPDATE inventory_service.stock_movements SET created_at = now() - (2 + random() * 20) * interval '1 day'
WHERE type = 'ADJUSTMENT';

-- ---------------------------------------------------------------- accounts: stores opened half a year ago, customers before their first order
UPDATE auth_service.users SET created_at = now() - interval '190 days' WHERE role = 'SELLER';
UPDATE auth_service.users u
SET created_at = f.first_order - interval '9 days'
FROM (SELECT user_id, min(new_created) AS first_order FROM demo_orders GROUP BY user_id) f
WHERE u.id = f.user_id AND u.role = 'CUSTOMER';

-- ---------------------------------------------------------------- the stock ledger in its new time order
-- Every physical movement stores the warehouse balance after it; recompute that running balance in the new order.
UPDATE inventory_service.stock_movements m
SET balance_after = x.balance
FROM (SELECT id, sum(quantity) OVER (PARTITION BY product_id, warehouse_id ORDER BY created_at, id) AS balance
      FROM inventory_service.stock_movements WHERE warehouse_id IS NOT NULL) x
WHERE m.id = x.id;

COMMIT;

SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month, count(*) AS orders, sum(total_amount) AS sales
FROM order_service.orders GROUP BY 1 ORDER BY 1;

-- Should be 0: a running stock balance below zero would mean a sale was dated before the stock arrived.
SELECT count(*) AS negative_stock_balances FROM inventory_service.stock_movements WHERE balance_after < 0;
