package com.byawasaya.platform.onboarding;

import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate; import org.springframework.transaction.PlatformTransactionManager;
import com.byawasaya.platform.business.*;

/** Opt-in migration of legacy shared-table tenant data to generated prefixed tables. */
@Component
@ConditionalOnProperty(name="app.tenant.migrate-legacy-on-startup", havingValue="true")
public class LegacyTenantMigrator implements CommandLineRunner {
 private final BusinessRepository businesses; private final JdbcTemplate jdbc; private final TenantProvisioningService provisioning; private final TenantTableNameResolver tables; private final TransactionTemplate transactionTemplate;
 public LegacyTenantMigrator(BusinessRepository businesses,JdbcTemplate jdbc,TenantProvisioningService provisioning,TenantTableNameResolver tables,PlatformTransactionManager txManager){this.businesses=businesses;this.jdbc=jdbc;this.provisioning=provisioning;this.tables=tables;this.transactionTemplate=new TransactionTemplate(txManager);}
 @Override public void run(String...args){for(Business b:businesses.findAllByOrderByCreatedAtDesc())if(b.getStatus()==BusinessStatus.ACTIVE&&tables.prefix(b.getId()).isEmpty())transactionTemplate.executeWithoutResult(status->migrateOneInternal(b.getId()));}
 private void migrateOneInternal(UUID businessId){Business b=businesses.findById(businessId).orElseThrow();String prefix="t_"+UUID.randomUUID().toString().replace("-","").substring(0,8);while(businesses.existsByTenantTagIgnoreCase(prefix))prefix="t_"+UUID.randomUUID().toString().replace("-","").substring(0,8);b.setTenantTag(prefix);provisioning.provision(businessId,prefix);String products=tables.table(prefix,"products"),variants=tables.table(prefix,"product_variants"),orders=tables.table(prefix,"orders"),items=tables.table(prefix,"order_items"),settings=tables.table(prefix,"store_settings");
  jdbc.update("INSERT INTO "+products+"(id,name,slug,sku,category,description,retail_price,wholesale_price,wholesale_min_quantity,image_url,payment_policy,advance_payment_percent,active,created_at) SELECT id,name,slug,sku,category,description,retail_price,wholesale_price,wholesale_min_quantity,image_url,payment_policy,advance_payment_percent,active,created_at FROM products WHERE business_id=? ON CONFLICT(id) DO NOTHING",businessId);
  jdbc.update("INSERT INTO "+variants+"(id,product_id,label,sku,retail_price,wholesale_price,active,sort_order,created_at) SELECT id,product_id,label,sku,retail_price,wholesale_price,active,sort_order,created_at FROM product_variants WHERE business_id=? ON CONFLICT(id) DO NOTHING",businessId);
  jdbc.update("INSERT INTO "+orders+"(id,order_number,customer_name,phone,email,province,district,municipality,ward,address_line,sales_type,payment_method,payment_status,status,subtotal,delivery_fee,advance_amount,total_amount,customer_note,idempotency_key,request_hash,created_at) SELECT id,order_number,customer_name,phone,email,province,district,municipality,ward,address_line,sales_type,payment_method,payment_status,status,subtotal,delivery_fee,advance_amount,total_amount,customer_note,idempotency_key,idempotency_hash,created_at FROM customer_orders WHERE business_id=? ON CONFLICT(id) DO NOTHING",businessId);
  jdbc.update("INSERT INTO "+items+"(id,order_id,product_id,product_name,sku,quantity,unit_price,line_total,variant_id,variant_label) SELECT i.id,i.order_id,i.product_id,i.product_name,i.sku,i.quantity,i.unit_price,i.line_total,i.variant_id,i.variant_label FROM order_items i JOIN customer_orders o ON o.id=i.order_id WHERE o.business_id=? ON CONFLICT(id) DO NOTHING",businessId);
  jdbc.update("UPDATE "+settings+" SET display_name=?,description=?,template_key=?,primary_color=?,cod_enabled=?,online_payment_enabled=?,advance_payment_percent=?,delivery_fee=?,minimum_order_amount=? WHERE id=1",b.getName(),b.getDescription(),b.getTemplateKey(),b.getPrimaryColor(),b.isCodEnabled(),b.isOnlinePaymentEnabled(),b.getAdvancePaymentPercent(),b.getDeliveryFee(),b.getMinimumOrderAmount());
 }
}
