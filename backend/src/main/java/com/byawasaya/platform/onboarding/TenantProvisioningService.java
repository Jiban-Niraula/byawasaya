package com.byawasaya.platform.onboarding;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates tenant-specific physical tables from fixed SQL templates. The caller owns the provisioning transaction. */
@Service
public class TenantProvisioningService {
 private final JdbcTemplate jdbc;
 public TenantProvisioningService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional
 public void provision(UUID businessId,String prefix){
  if(prefix==null||!prefix.matches("t_[a-z0-9]{8}"))throw new IllegalArgumentException("Invalid generated tenant prefix.");
  java.util.List<String> existing=jdbc.query("SELECT table_prefix FROM tenant_provisioning WHERE business_id=?",(rs,n)->rs.getString(1),businessId);if(!existing.isEmpty()&&!existing.get(0).equals(prefix))throw new IllegalStateException("Tenant prefix is immutable once registered.");
  jdbc.update("INSERT INTO tenant_provisioning(id,business_id,table_prefix,status,schema_version,attempts,updated_at) VALUES(gen_random_uuid(),?,?,'PROVISIONING',3,1,now()) ON CONFLICT (business_id) DO UPDATE SET status='PROVISIONING', attempts=tenant_provisioning.attempts+1, last_error=NULL, updated_at=now()",businessId,prefix);
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_categories (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(100) NOT NULL, slug VARCHAR(120) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(slug))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_products (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(140) NOT NULL, slug VARCHAR(160) NOT NULL, sku VARCHAR(80), category VARCHAR(80) NOT NULL, description VARCHAR(3000), retail_price NUMERIC(12,2) NOT NULL CHECK(retail_price>=0), wholesale_price NUMERIC(12,2) NOT NULL CHECK(wholesale_price>=0), wholesale_min_quantity INTEGER NOT NULL DEFAULT 5, image_url VARCHAR(1000), payment_policy VARCHAR(30) NOT NULL DEFAULT 'COD_ALLOWED', advance_payment_percent INTEGER NOT NULL DEFAULT 30, active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(slug), UNIQUE(sku))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_product_variants (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), product_id UUID NOT NULL REFERENCES "+prefix+"_products(id) ON DELETE CASCADE, label VARCHAR(140) NOT NULL, sku VARCHAR(80), retail_price NUMERIC(12,2) CHECK(retail_price IS NULL OR retail_price>0), wholesale_price NUMERIC(12,2) CHECK(wholesale_price IS NULL OR wholesale_price>0), active BOOLEAN NOT NULL DEFAULT true, sort_order INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(product_id, label), UNIQUE(sku))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_staffs (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), account_id UUID NOT NULL REFERENCES user_accounts(id), role VARCHAR(40) NOT NULL DEFAULT 'STAFF', permissions JSONB NOT NULL DEFAULT '{}'::jsonb, active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(account_id))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_customers (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(120) NOT NULL, phone VARCHAR(30) NOT NULL, email VARCHAR(180), account_id UUID REFERENCES customer_accounts(id) ON DELETE SET NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(phone))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_store_assets (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), asset_type VARCHAR(30) NOT NULL, mime_type VARCHAR(120) NOT NULL, bytes BYTEA NOT NULL, sha256_hex VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_orders (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_number VARCHAR(40) NOT NULL UNIQUE, customer_id UUID REFERENCES "+prefix+"_customers(id), customer_account_id UUID REFERENCES customer_accounts(id) ON DELETE SET NULL, customer_name VARCHAR(120) NOT NULL, phone VARCHAR(30) NOT NULL, email VARCHAR(180), province VARCHAR(100) NOT NULL, district VARCHAR(100) NOT NULL, municipality VARCHAR(120), ward VARCHAR(10), address_line VARCHAR(300) NOT NULL, sales_type VARCHAR(20) NOT NULL, payment_method VARCHAR(25) NOT NULL, payment_status VARCHAR(25) NOT NULL, status VARCHAR(25) NOT NULL, subtotal NUMERIC(12,2) NOT NULL, delivery_fee NUMERIC(12,2) NOT NULL, advance_amount NUMERIC(12,2) NOT NULL, total_amount NUMERIC(12,2) NOT NULL, customer_note VARCHAR(500), idempotency_key VARCHAR(120) UNIQUE, request_hash VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
  jdbc.execute("ALTER TABLE "+prefix+"_customers ADD COLUMN IF NOT EXISTS account_id UUID REFERENCES customer_accounts(id) ON DELETE SET NULL");
  jdbc.execute("ALTER TABLE "+prefix+"_orders ADD COLUMN IF NOT EXISTS customer_account_id UUID REFERENCES customer_accounts(id) ON DELETE SET NULL");
  jdbc.execute("CREATE INDEX IF NOT EXISTS "+prefix+"_orders_customer_account_created_idx ON "+prefix+"_orders(customer_account_id,created_at DESC)");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_order_items (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES "+prefix+"_orders(id) ON DELETE CASCADE, product_id UUID, product_name VARCHAR(140) NOT NULL, sku VARCHAR(80), quantity INTEGER NOT NULL CHECK(quantity>0), unit_price NUMERIC(12,2) NOT NULL, line_total NUMERIC(12,2) NOT NULL, variant_id UUID, variant_label VARCHAR(140))");
  jdbc.execute("ALTER TABLE "+prefix+"_order_items ADD COLUMN IF NOT EXISTS variant_id UUID");
  jdbc.execute("ALTER TABLE "+prefix+"_order_items ADD COLUMN IF NOT EXISTS variant_label VARCHAR(140)");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_payments (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES "+prefix+"_orders(id), provider VARCHAR(40), provider_reference VARCHAR(180), amount NUMERIC(12,2) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'NPR', status VARCHAR(25) NOT NULL, idempotency_key VARCHAR(120) UNIQUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_payment_events (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), payment_id UUID NOT NULL REFERENCES "+prefix+"_payments(id), event_type VARCHAR(80) NOT NULL, provider_event_id VARCHAR(180), payload JSONB NOT NULL DEFAULT '{}'::jsonb, received_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(provider_event_id))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS "+prefix+"_store_settings (id SMALLINT PRIMARY KEY DEFAULT 1 CHECK(id=1), display_name VARCHAR(120) NOT NULL, description VARCHAR(1000), logo_asset_id UUID, template_key VARCHAR(40) NOT NULL DEFAULT 'classic', primary_color VARCHAR(20) NOT NULL DEFAULT '#2f6f5e', cod_enabled BOOLEAN NOT NULL DEFAULT true, online_payment_enabled BOOLEAN NOT NULL DEFAULT false, advance_payment_percent INTEGER NOT NULL DEFAULT 30, delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 150, minimum_order_amount NUMERIC(12,2) NOT NULL DEFAULT 0)");
  jdbc.execute("CREATE INDEX IF NOT EXISTS "+prefix+"_products_active_created_idx ON "+prefix+"_products(active,created_at DESC)");
  jdbc.execute("CREATE INDEX IF NOT EXISTS "+prefix+"_orders_status_created_idx ON "+prefix+"_orders(status,created_at DESC)");
  jdbc.execute("CREATE INDEX IF NOT EXISTS "+prefix+"_orders_phone_idx ON "+prefix+"_orders(phone)");
  jdbc.execute("CREATE INDEX IF NOT EXISTS "+prefix+"_order_items_order_idx ON "+prefix+"_order_items(order_id)");
  jdbc.update("INSERT INTO "+prefix+"_store_settings(id,display_name) SELECT 1,name FROM businesses WHERE id=? ON CONFLICT(id) DO NOTHING",businessId);
  jdbc.update("UPDATE tenant_provisioning SET status='READY', schema_version=3, last_error=NULL, last_migrated_at=now(), updated_at=now() WHERE business_id=?",businessId);
 }
}
