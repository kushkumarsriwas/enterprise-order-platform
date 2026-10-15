-- Removes every store, customer, product, order, payment and message, so the demo can be loaded again from scratch
-- (scripts\windows\render-demo.cmd runs it before scripts/demo-data.mjs). What the platform itself needs stays:
-- the marketplace departments, the token signing keys and the administrator account.
-- Run on the shared PostgreSQL database (Render, or a local run where every service uses PostgreSQL).

BEGIN;

DO $$
DECLARE
  tables text;
BEGIN
  SELECT string_agg(format('%I.%I', table_schema, table_name), ', ')
    INTO tables
    FROM information_schema.tables
   WHERE table_type = 'BASE TABLE'
     AND table_schema IN ('auth_service', 'inventory_service', 'order_service', 'payment_service', 'notification_service')
     AND (table_schema, table_name) NOT IN (('auth_service', 'users'), ('auth_service', 'jwt_signing_keys'),
                                            ('inventory_service', 'categories'));
  IF tables IS NOT NULL THEN
    EXECUTE 'TRUNCATE ' || tables || ' CASCADE';
  END IF;
END $$;

DELETE FROM auth_service.users WHERE role <> 'ADMIN';

-- Version 2 kept its tables in other schemas; nothing reads them any more.
DROP SCHEMA IF EXISTS accounts, inventory, sales, finance, messaging CASCADE;

COMMIT;

SELECT (SELECT count(*) FROM auth_service.users) AS accounts_left,
       (SELECT count(*) FROM inventory_service.categories) AS departments;
