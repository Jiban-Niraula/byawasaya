package com.byawasaya.platform.orders;

import java.math.BigDecimal; import java.math.RoundingMode; import java.sql.ResultSet; import java.sql.SQLException; import java.time.Instant; import java.time.LocalDate; import java.time.OffsetDateTime; import java.util.*;
import org.springframework.dao.DataIntegrityViolationException; import org.springframework.http.HttpStatus; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException;
import com.byawasaya.platform.business.*; import com.byawasaya.platform.catalog.*; import com.byawasaya.platform.payments.PaymentGatewayService;

@Service @Transactional
public class OrderService {
 private final CustomerOrderRepository orders; private final ProductRepository legacyProducts; private final BusinessRepository businesses; private final ProductService products; private final JdbcTemplate jdbc; private final TenantTableNameResolver tables; private final PaymentGatewayService payments;
 public OrderService(CustomerOrderRepository orders,ProductRepository legacyProducts,BusinessRepository businesses,ProductService products,JdbcTemplate jdbc,TenantTableNameResolver tables,PaymentGatewayService payments){this.orders=orders;this.legacyProducts=legacyProducts;this.businesses=businesses;this.products=products;this.jdbc=jdbc;this.tables=tables;this.payments=payments;}
 public OrderDtos.OrderResponse checkout(String slug,OrderDtos.CreateOrderRequest r){return checkout(slug,r,null,null);}
 public OrderDtos.OrderResponse checkout(String slug,OrderDtos.CreateOrderRequest r,String idempotencyKey){return checkout(slug,r,idempotencyKey,null);}
 @org.springframework.cache.annotation.CacheEvict(cacheNames="adminDashboard",key="'overview'") public OrderDtos.OrderResponse checkout(String slug,OrderDtos.CreateOrderRequest r,String idempotencyKey,UUID customerAccountId){Business store=businesses.findBySlugIgnoreCase(slug).filter(Business::isOperational).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found."));
  if(customerAccountId!=null){String verifiedEmail;try{verifiedEmail=jdbc.queryForObject("SELECT email FROM customer_accounts WHERE id=? AND enabled=true AND email_verified=true",String.class,customerAccountId);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in with a verified customer account to link order history.");}r=new OrderDtos.CreateOrderRequest(r.customerName(),r.phone(),verifiedEmail,r.province(),r.district(),r.municipality(),r.ward(),r.addressLine(),r.salesType(),r.paymentMethod(),r.paymentProvider(),r.customerNote(),r.items());}
  if(r.items().size()>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"An order can contain at most 50 line items.");
  String prefix=tables.prefix(store.getId()).orElse(null);String normalizedKey=normalizeIdempotencyKey(idempotencyKey);String requestHash=sha256(canonicalCheckoutRequest(r)+"|customer="+customerAccountId);
  if(prefix!=null&&normalizedKey!=null){String existing=table(prefix,"orders");List<Map<String,Object>> found=jdbc.queryForList("SELECT id,request_hash FROM "+existing+" WHERE idempotency_key=?",normalizedKey);if(!found.isEmpty()){if(!requestHash.equals(found.get(0).get("request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different checkout request.");return dynamicResponse(prefix,store.getId(),(UUID)found.get(0).get("id"));}}
  if(prefix==null&&normalizedKey!=null){var existing=orders.findByBusinessIdAndIdempotencyKey(store.getId(),normalizedKey);if(existing.isPresent()){if(!requestHash.equals(existing.get().getIdempotencyHash()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different checkout request.");return OrderDtos.OrderResponse.from(existing.get());}}
  BigDecimal subtotal=BigDecimal.ZERO;boolean onlineOnly=false,advanceRequired=false;int advancePercent=store.getAdvancePaymentPercent();var calculated=new ArrayList<CalculatedLine>();
  for(var line:r.items()){if(line.quantity()<1||line.quantity()>999)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Product quantity must be between 1 and 999.");ProductService.ProductData p=products.findForCheckout(store.getId(),line.productId(),line.variantId());BigDecimal price=(r.salesType()==SalesType.WHOLESALE&&line.quantity()>=p.wholesaleMinQuantity())?p.wholesalePrice():p.retailPrice();BigDecimal lineTotal=price.multiply(BigDecimal.valueOf(line.quantity())).setScale(2,RoundingMode.HALF_UP);subtotal=subtotal.add(lineTotal);if(p.paymentPolicy()==PaymentPolicy.ONLINE_ONLY)onlineOnly=true;if(p.paymentPolicy()==PaymentPolicy.ADVANCE_REQUIRED){advanceRequired=true;advancePercent=Math.max(advancePercent,p.advancePaymentPercent());}calculated.add(new CalculatedLine(p,line.quantity(),price,lineTotal));}
  if(subtotal.compareTo(store.getMinimumOrderAmount())<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The minimum order amount is Rs. "+store.getMinimumOrderAmount().toPlainString()+".");
  if(onlineOnly&&r.paymentMethod()!=PaymentMethod.ONLINE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This order contains an online-payment-only product. Select online payment.");
  if(advanceRequired&&r.paymentMethod()!=PaymentMethod.ADVANCE_COD&&r.paymentMethod()!=PaymentMethod.ONLINE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This order requires an advance payment before dispatch.");
  if(r.paymentMethod()==PaymentMethod.COD&&(!store.isCodEnabled()||onlineOnly||advanceRequired))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cash on delivery is not available for this order.");
  if(r.paymentMethod()==PaymentMethod.ADVANCE_COD&&!store.isCodEnabled())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cash-on-delivery balance collection is disabled for this store.");
  if(r.paymentMethod()==PaymentMethod.ONLINE&&!store.isOnlinePaymentEnabled())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Online payment is not enabled for this store.");
  if((r.paymentMethod()==PaymentMethod.ONLINE||r.paymentMethod()==PaymentMethod.ADVANCE_COD)&&r.paymentProvider()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a connected online payment provider.");
  if(r.paymentMethod()==PaymentMethod.COD&&r.paymentProvider()!=null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A payment provider is only valid for online checkout.");
  if(r.paymentMethod()==PaymentMethod.ADVANCE_COD&&advancePercent<=0)advancePercent=30;
  BigDecimal delivery=store.getDeliveryFee()==null?BigDecimal.ZERO:store.getDeliveryFee();BigDecimal total=subtotal.add(delivery);BigDecimal advance=r.paymentMethod()==PaymentMethod.ADVANCE_COD?total.multiply(BigDecimal.valueOf(advancePercent)).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP):BigDecimal.ZERO;PaymentStatus paymentStatus=r.paymentMethod()==PaymentMethod.COD?PaymentStatus.UNPAID:PaymentStatus.PAYMENT_PENDING;
  String number="BYW-"+LocalDate.now().toString().replace("-","").substring(2)+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase();
  if(prefix!=null)return createDynamicOrder(prefix,store,r,calculated,normalizedKey,requestHash,number,subtotal,delivery,advance,total,paymentStatus,customerAccountId);
  var order=new CustomerOrder(store.getId(),number,r.customerName().trim(),r.phone().trim(),blank(r.email()),r.province().trim(),r.district().trim(),blank(r.municipality()),blank(r.ward()),r.addressLine().trim(),r.salesType(),r.paymentMethod(),paymentStatus,OrderStatus.PLACED,subtotal,delivery,advance,total,blank(r.customerNote()));order.setCustomerAccountId(customerAccountId);order.setIdempotencyKey(normalizedKey);order.setIdempotencyHash(requestHash);for(var line:calculated)order.addItem(new OrderItem(line.product().id(),line.product().variantId(),line.product().variantLabel(),line.product().name(),line.product().sku(),line.quantity(),line.price(),line.lineTotal()));CustomerOrder saved=orders.save(order);if(customerAccountId!=null)recordCustomerHistory(customerAccountId,store,saved.getId(),number,total,paymentStatus,OrderStatus.PLACED);return OrderDtos.OrderResponse.from(saved);
 }
 private OrderDtos.OrderResponse createDynamicOrder(String p,Business store,OrderDtos.CreateOrderRequest r,List<CalculatedLine> lines,String key,String requestHash,String number,BigDecimal subtotal,BigDecimal delivery,BigDecimal advance,BigDecimal total,PaymentStatus paymentStatus,UUID customerAccountId){
  String customers=table(p,"customers"),ordersTable=table(p,"orders"),items=table(p,"order_items");
  UUID customerId=jdbc.queryForObject("INSERT INTO "+customers+"(name,phone,email,account_id) VALUES(?,?,?,?) ON CONFLICT(phone) DO UPDATE SET name=EXCLUDED.name,email=EXCLUDED.email,account_id=COALESCE(EXCLUDED.account_id,"+customers+".account_id) RETURNING id",UUID.class,r.customerName().trim(),r.phone().trim(),blank(r.email()),customerAccountId);
  UUID orderId=jdbc.query("INSERT INTO "+ordersTable+"(order_number,customer_id,customer_account_id,customer_name,phone,email,province,district,municipality,ward,address_line,sales_type,payment_method,payment_status,status,subtotal,delivery_fee,advance_amount,total_amount,customer_note,idempotency_key,request_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (idempotency_key) DO NOTHING RETURNING id",rs->rs.next()?rs.getObject(1,UUID.class):null,number,customerId,customerAccountId,r.customerName().trim(),r.phone().trim(),blank(r.email()),r.province(),r.district(),blank(r.municipality()),blank(r.ward()),r.addressLine().trim(),r.salesType().name(),r.paymentMethod().name(),paymentStatus.name(),OrderStatus.PLACED.name(),subtotal,delivery,advance,total,blank(r.customerNote()),key,requestHash);
  if(orderId==null&&key!=null){List<Map<String,Object>> found=jdbc.queryForList("SELECT id,request_hash FROM "+ordersTable+" WHERE idempotency_key=?",key);if(!found.isEmpty()){if(!requestHash.equals(found.get(0).get("request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different checkout request.");return dynamicResponse(p,store.getId(),(UUID)found.get(0).get("id"));}}
  if(orderId==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Checkout could not be recorded; retry with the same idempotency key.");
  for(CalculatedLine line:lines)jdbc.update("INSERT INTO "+items+"(order_id,product_id,variant_id,variant_label,product_name,sku,quantity,unit_price,line_total) VALUES(?,?,?,?,?,?,?,?,?)",orderId,line.product().id(),line.product().variantId(),line.product().variantLabel(),line.product().name(),line.product().sku(),line.quantity(),line.price(),line.lineTotal());
  if(customerAccountId!=null)recordCustomerHistory(customerAccountId,store,orderId,number,total,paymentStatus,OrderStatus.PLACED);
  return dynamicResponse(p,store.getId(),orderId);
 }
 private void recordCustomerHistory(UUID customerAccountId,Business store,UUID orderId,String number,BigDecimal total,PaymentStatus paymentStatus,OrderStatus orderStatus){jdbc.update("INSERT INTO customer_order_history(id,customer_account_id,business_id,order_id,order_number,store_name,store_slug,total_amount,payment_status,order_status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,now()) ON CONFLICT(customer_account_id,business_id,order_id) DO NOTHING",UUID.randomUUID(),customerAccountId,store.getId(),orderId,number,store.getName(),store.getSlug(),total,paymentStatus.name(),orderStatus.name());}
 private OrderDtos.OrderResponse dynamicResponse(String p,UUID businessId,UUID id){String ot=table(p,"orders"),it=table(p,"order_items");Map<String,Object> rs;try{rs=jdbc.queryForMap("SELECT * FROM "+ot+" WHERE id=?",id);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found.");}var items=jdbc.query("SELECT product_id,variant_id,variant_label,product_name,sku,quantity,unit_price,line_total FROM "+it+" WHERE order_id=?",(ir,n)->new OrderDtos.OrderItemResponse(ir.getObject("product_id",UUID.class),ir.getObject("variant_id",UUID.class),ir.getString("variant_label"),ir.getString("product_name"),ir.getString("sku"),ir.getInt("quantity"),ir.getBigDecimal("unit_price"),ir.getBigDecimal("line_total")),id);return new OrderDtos.OrderResponse((UUID)rs.get("id"),(String)rs.get("order_number"),(String)rs.get("customer_name"),(String)rs.get("phone"),(String)rs.get("email"),(String)rs.get("province"),(String)rs.get("district"),(String)rs.get("municipality"),(String)rs.get("address_line"),SalesType.valueOf((String)rs.get("sales_type")),PaymentMethod.valueOf((String)rs.get("payment_method")),PaymentStatus.valueOf((String)rs.get("payment_status")),OrderStatus.valueOf((String)rs.get("status")),(BigDecimal)rs.get("subtotal"),(BigDecimal)rs.get("delivery_fee"),(BigDecimal)rs.get("advance_amount"),(BigDecimal)rs.get("total_amount"),(String)rs.get("customer_note"),instant(rs.get("created_at")),items);}
 public List<OrderDtos.OrderResponse> listForStore(UUID businessId){Optional<String> prefix=tables.prefix(businessId);if(prefix.isPresent()){List<UUID> ids=jdbc.query("SELECT id FROM "+table(prefix.get(),"orders")+" ORDER BY created_at DESC",(rs,n)->rs.getObject(1,UUID.class));return ids.stream().map(id->dynamicResponse(prefix.get(),businessId,id)).toList();}return orders.findByBusinessIdOrderByCreatedAtDesc(businessId).stream().map(OrderDtos.OrderResponse::from).toList();}
 @org.springframework.cache.annotation.CacheEvict(cacheNames="adminDashboard",key="'overview'")
 public OrderDtos.OrderResponse updateStatus(UUID businessId, UUID id, OrderStatus status) {
  Optional<String> prefix = tables.prefix(businessId);
  if (prefix.isPresent()) {
   String tableName = table(prefix.get(), "orders");
   Map<String, Object> row;
   try { row = jdbc.queryForMap("SELECT status,payment_status,payment_method FROM " + tableName + " WHERE id=?", id); }
   catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."); }
   OrderStatus current = OrderStatus.valueOf((String) row.get("status"));
   PaymentStatus payment = PaymentStatus.valueOf((String) row.get("payment_status"));
   PaymentMethod method = PaymentMethod.valueOf((String) row.get("payment_method"));
   if (!allowedTransition(current, status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That order status transition is not allowed.");
   OrderFulfilmentPolicy.validate(method, payment, status);
   if (status == OrderStatus.CANCELLED && (payment == PaymentStatus.PAID || payment == PaymentStatus.PARTIALLY_PAID))
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid orders require a verified refund workflow before cancellation.");
   if (status == OrderStatus.CANCELLED && payment == PaymentStatus.PAYMENT_PENDING && method != PaymentMethod.COD && hasActivePayment(businessId, id))
    throw new ResponseStatusException(HttpStatus.CONFLICT, "The payment session is still active or its status changed. Refresh the order and retry cancellation after the provider confirms failure.");
   jdbc.update("UPDATE " + tableName + " SET status=? WHERE id=?", status.name(), id);
   jdbc.update("UPDATE customer_order_history SET order_status=? WHERE business_id=? AND order_id=?", status.name(), businessId, id);
   return dynamicResponse(prefix.get(), businessId, id);
  }
  CustomerOrder order = orders.findByIdAndBusinessId(id, businessId)
   .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
  if (!allowedTransition(order.getStatus(), status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That order status transition is not allowed.");
  OrderFulfilmentPolicy.validate(order.getPaymentMethod(), order.getPaymentStatus(), status);
  if (status == OrderStatus.CANCELLED && (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID))
   throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid orders require a verified refund workflow before cancellation.");
  if (status == OrderStatus.CANCELLED && order.getPaymentStatus() == PaymentStatus.PAYMENT_PENDING && order.getPaymentMethod() != PaymentMethod.COD && hasActivePayment(businessId, id))
   throw new ResponseStatusException(HttpStatus.CONFLICT, "The payment session is still active or its status changed. Refresh the order and retry cancellation after the provider confirms failure.");
  order.setStatus(status);
  CustomerOrder saved = orders.save(order);
  jdbc.update("UPDATE customer_order_history SET order_status=? WHERE business_id=? AND order_id=?", status.name(), businessId, id);
  return OrderDtos.OrderResponse.from(saved);
 }
 @org.springframework.cache.annotation.CacheEvict(cacheNames="adminDashboard",key="'overview'") public OrderDtos.OrderResponse updatePaymentStatus(UUID businessId,UUID id,PaymentStatus status){if(status!=PaymentStatus.PAID)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only cash-on-delivery receipts can be recorded manually; online payments are verified by the provider callback.");Optional<String> prefix=tables.prefix(businessId);if(prefix.isPresent()){String t=table(prefix.get(),"orders");Map<String,Object> row;try{row=jdbc.queryForMap("SELECT payment_method,payment_status,status FROM "+t+" WHERE id=?",id);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found.");}if("CANCELLED".equals(row.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancelled orders cannot be marked paid.");String method=(String)row.get("payment_method");if(!"COD".equals(method)&&!"ADVANCE_COD".equals(method))throw new ResponseStatusException(HttpStatus.CONFLICT,"Online payment status is updated only after server-side provider verification.");if("COD".equals(method)&&!"DELIVERED".equals(row.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"COD receipt can be recorded only after delivery.");if("ADVANCE_COD".equals(method)&&!"SHIPPED".equals(row.get("status"))&& !"DELIVERED".equals(row.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Collect the remaining COD balance when the order is shipped or delivered.");if("ADVANCE_COD".equals(method)&&!"PARTIALLY_PAID".equals(row.get("payment_status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Record the verified advance before collecting the remaining COD balance.");if("COD".equals(method)&&"PAID".equals(row.get("payment_status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"This order is already paid.");jdbc.update("UPDATE "+t+" SET payment_status=? WHERE id=?",status.name(),id);jdbc.update("UPDATE customer_order_history SET payment_status=? WHERE business_id=? AND order_id=?",status.name(),businessId,id);return dynamicResponse(prefix.get(),businessId,id);}CustomerOrder order=orders.findByIdAndBusinessId(id,businessId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found."));if(order.getStatus()==OrderStatus.CANCELLED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancelled orders cannot be marked paid.");if(order.getPaymentMethod()!=PaymentMethod.COD&&order.getPaymentMethod()!=PaymentMethod.ADVANCE_COD)throw new ResponseStatusException(HttpStatus.CONFLICT,"Online payment status is updated only after server-side provider verification.");if(order.getPaymentMethod()==PaymentMethod.COD&&order.getStatus()!=OrderStatus.DELIVERED)throw new ResponseStatusException(HttpStatus.CONFLICT,"COD receipt can be recorded only after delivery.");if(order.getPaymentMethod()==PaymentMethod.ADVANCE_COD&&order.getStatus()!=OrderStatus.SHIPPED&&order.getStatus()!=OrderStatus.DELIVERED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Collect the remaining COD balance when the order is shipped or delivered.");if(order.getPaymentMethod()==PaymentMethod.ADVANCE_COD&&order.getPaymentStatus()!=PaymentStatus.PARTIALLY_PAID)throw new ResponseStatusException(HttpStatus.CONFLICT,"Record the verified advance before collecting the remaining COD balance.");if(order.getPaymentMethod()==PaymentMethod.COD&&order.getPaymentStatus()==PaymentStatus.PAID)throw new ResponseStatusException(HttpStatus.CONFLICT,"This order is already paid.");order.setPaymentStatus(status);CustomerOrder saved=orders.save(order);jdbc.update("UPDATE customer_order_history SET payment_status=? WHERE business_id=? AND order_id=?",status.name(),businessId,id);return OrderDtos.OrderResponse.from(saved);}

 private boolean hasActivePayment(UUID businessId, UUID orderId) {
  List<Map<String, Object>> active = jdbc.queryForList(
   "SELECT id,store_slug FROM payment_attempts WHERE business_id=? AND order_id=? AND status IN ('INITIATED','PENDING') ORDER BY created_at DESC",
   businessId, orderId);
  if (active.isEmpty()) {
   Integer completed = jdbc.queryForObject(
    "SELECT count(*) FROM payment_attempts WHERE business_id=? AND order_id=? AND status='COMPLETED'",
    Integer.class, businessId, orderId);
   return completed != null && completed > 0;
  }
  // Re-query the provider before keeping an abandoned attempt as a permanent blocker.
  // If provider state is uncertain or reports a completion during this request, fail closed;
  // a subsequent request reloads the order's now-current payment status.
  for (Map<String, Object> attempt : active) {
   try {
    var state = payments.stateForCancellation((String) attempt.get("store_slug"), (UUID) attempt.get("id"));
    if (!"FAILED".equals(state.status())) return true;
   } catch (RuntimeException providerUnavailable) {
    return true;
   }
  }
  Integer count = jdbc.queryForObject(
   "SELECT count(*) FROM payment_attempts WHERE business_id=? AND order_id=? AND status IN ('INITIATED','PENDING','COMPLETED')",
   Integer.class, businessId, orderId);
  return count != null && count > 0;
 }


 private boolean allowedTransition(OrderStatus current,OrderStatus next){return switch(current){case PLACED->next==OrderStatus.CONFIRMED||next==OrderStatus.CANCELLED;case CONFIRMED->next==OrderStatus.PROCESSING||next==OrderStatus.CANCELLED;case PROCESSING->next==OrderStatus.SHIPPED||next==OrderStatus.CANCELLED;case SHIPPED->next==OrderStatus.DELIVERED;case DELIVERED,CANCELLED->false;};}
 private String table(String p,String suffix){return tables.table(p,suffix);}
 private String canonicalCheckoutRequest(OrderDtos.CreateOrderRequest r){
  // Payment provider selection is an attempt-level choice, not part of the order contents.
  // Keeping it out of the idempotency hash lets customers retry the same order with another provider.
  return r.customerName()+"|"+r.phone()+"|"+blank(r.email())+"|"+r.province()+"|"+r.district()+"|"+blank(r.municipality())+"|"+blank(r.ward())+"|"+r.addressLine()+"|"+r.salesType()+"|"+r.paymentMethod()+"|"+blank(r.customerNote())+"|"+r.items().stream().map(i->i.productId()+":"+i.variantId()+":"+i.quantity()).collect(java.util.stream.Collectors.joining(","));
 }
 private String normalizeIdempotencyKey(String key){if(key==null)return null;if(key.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency key must not be blank.");String v=key.trim();if(v.length()>120)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency key is too long.");return v;}
 private String blank(String s){return s==null||s.isBlank()?null:s.trim();}
 private String sha256(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Instant instant(Object v){if(v instanceof Instant i)return i;if(v instanceof OffsetDateTime o)return o.toInstant();if(v instanceof java.sql.Timestamp t)return t.toInstant();return Instant.now();}
 private record CalculatedLine(ProductService.ProductData product,int quantity,BigDecimal price,BigDecimal lineTotal){}
}
