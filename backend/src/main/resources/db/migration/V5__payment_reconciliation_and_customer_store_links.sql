ALTER TABLE payment_attempts
    ADD COLUMN last_verification_attempt_at TIMESTAMPTZ;

ALTER TABLE customer_order_history
    ADD COLUMN store_slug VARCHAR(80);

UPDATE customer_order_history history
   SET store_slug = businesses.slug
  FROM businesses
 WHERE businesses.id = history.business_id
   AND history.store_slug IS NULL;

ALTER TABLE customer_order_history
    ALTER COLUMN store_slug SET NOT NULL;
