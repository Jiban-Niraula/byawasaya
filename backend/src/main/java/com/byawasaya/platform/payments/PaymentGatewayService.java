package com.byawasaya.platform.payments;

import com.byawasaya.platform.business.Business;
import com.byawasaya.platform.business.BusinessRepository;
import com.byawasaya.platform.business.TenantTableNameResolver;
import com.byawasaya.platform.orders.CustomerOrder;
import com.byawasaya.platform.orders.CustomerOrderRepository;
import com.byawasaya.platform.orders.OrderDtos;
import com.byawasaya.platform.orders.PaymentMethod;
import com.byawasaya.platform.orders.PaymentStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentGatewayService {
    private static final String ATTEMPTS = "payment_attempts";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final CustomerOrderRepository legacyOrders;
    private final BusinessRepository businesses;
    private final TenantTableNameResolver tables;
    private final PaymentProviderCredentialsService credentials;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final TransactionTemplate reconciliationTransactions;
    private final RestClient restClient;
    private final String frontendUrl;
    private final String backendPublicUrl;

    public PaymentGatewayService(JdbcTemplate jdbc, CustomerOrderRepository legacyOrders,
            BusinessRepository businesses, TenantTableNameResolver tables,
            PaymentProviderCredentialsService credentials, ObjectMapper mapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.backend-public-url:${app.frontend-url}}") String backendPublicUrl) {
        this.jdbc = jdbc;
        this.legacyOrders = legacyOrders;
        this.businesses = businesses;
        this.tables = tables;
        this.credentials = credentials;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.reconciliationTransactions = new TransactionTemplate(transactionManager);
        this.reconciliationTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(12));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.frontendUrl = trimSlash(frontendUrl);
        this.backendPublicUrl = trimSlash(backendPublicUrl);
    }

    public PaymentProviderDtos.CheckoutResponse begin(String slug, OrderDtos.OrderResponse order,
            PaymentMethod paymentMethod, PaymentProvider provider) {
        if (paymentMethod == PaymentMethod.COD)
            return new PaymentProviderDtos.CheckoutResponse(order, null, null, null, Map.of());
        Business business = businesses.findBySlugIgnoreCase(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
        if (!business.isOnlinePaymentEnabled())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Online payment is not enabled for this store.");
        PaymentProviderCredentialsService.ProviderConfiguration config = credentials.require(business.getId(), provider);

        BigDecimal amount = paymentMethod == PaymentMethod.ONLINE ? order.totalAmount() : order.advanceAmount();
        long amountPaisa = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        if (amountPaisa <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "The payment amount must be greater than zero.");
        ensureOrderUnpaid(business.getId(), order.id(), paymentMethod);

        var existing = jdbc.query("""
                SELECT id,provider_reference,redirect_url,redirect_fields,status
                  FROM payment_attempts
                 WHERE business_id=? AND order_id=? AND provider=? AND payment_method=?
                   AND status IN ('INITIATED','PENDING')
                 ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> new ExistingAttempt(rs.getObject("id", UUID.class), rs.getString("provider_reference"),
                rs.getString("redirect_url"), readFields(rs.getString("redirect_fields")), rs.getString("status")),
                business.getId(), order.id(), provider.name(), paymentMethod.name());
        if (!existing.isEmpty() && existing.getFirst().redirectUrl() != null)
            return new PaymentProviderDtos.CheckoutResponse(order, provider.name(), existing.getFirst().redirectUrl(),
                    provider == PaymentProvider.ESEWA ? "POST" : "GET", existing.getFirst().redirectFields());

        UUID attemptId = UUID.randomUUID();
        String merchantOrderReference = "BYW-" + attemptId;
        String encryptedConfig = credentials.encryptSnapshot(config);
        try {
            jdbc.update("""
                    INSERT INTO payment_attempts(id,business_id,order_id,order_number,store_slug,provider,payment_method,
                        amount_paisa,status,merchant_order_reference,encrypted_provider_configuration)
                    VALUES(?,?,?,?,?,?,?,?,'INITIATED',?,?)
                    """, attemptId, business.getId(), order.id(), order.orderNumber(), slug, provider.name(),
                    paymentMethod.name(), amountPaisa, merchantOrderReference, encryptedConfig);
        } catch (org.springframework.dao.DuplicateKeyException concurrentCheckout) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Online checkout is already being prepared. Retry the same order in a moment.");
        }

        try {
            ProviderCheckout checkout = provider == PaymentProvider.KHALTI
                    ? initiateKhalti(config, attemptId, merchantOrderReference, order, amountPaisa)
                    : initiateEsewa(config, attemptId, merchantOrderReference, amount);
            jdbc.update("UPDATE payment_attempts SET provider_reference=?,redirect_url=?,redirect_fields=CAST(? AS jsonb),updated_at=now() WHERE id=?",
                    checkout.providerReference(), checkout.url(), mapper.writeValueAsString(checkout.fields()), attemptId);
            return new PaymentProviderDtos.CheckoutResponse(order, provider.name(), checkout.url(), checkout.method(), checkout.fields());
        } catch (Exception providerFailure) {
            jdbc.update("UPDATE payment_attempts SET status='FAILED',updated_at=now() WHERE id=?", attemptId);
            transactions.executeWithoutResult(tx -> resetPendingOrderPaymentIfNoLiveAttempt(business.getId(), order.id()));
            if (providerFailure instanceof ResponseStatusException rse) throw rse;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider could not start checkout. You can retry using the same order reference.");
        }
    }

    public URI verifyCallback(UUID attemptId, String pidx, String encodedEsewaData) {
        Map<String, Object> attempt = loadAttempt(attemptId);
        String provider = (String) attempt.get("provider");
        PaymentProviderCredentialsService.ProviderConfiguration config = credentials.decryptSnapshot(
                (String) attempt.get("encrypted_provider_configuration"));

        Verification verification;
        try {
            verification = provider.equals(PaymentProvider.KHALTI.name())
                    ? verifyKhalti(attempt, config, pidx)
                    : verifyEsewa(attempt, config, encodedEsewaData);
        } catch (Exception verificationFailure) {
            recordStatus(attemptId, "PENDING", null, false);
            verification = new Verification("PENDING", null);
        }
        if (verification.status().equals("COMPLETED")) recordStatus(attemptId, "COMPLETED", verification.reference(), true);
        else if (verification.status().equals("FAILED")) recordStatus(attemptId, "FAILED", verification.reference(), false);
        else recordStatus(attemptId, "PENDING", verification.reference(), false);

        String query = "attemptId=" + attemptId + "&slug=" + encode((String) attempt.get("store_slug"));
        return URI.create(frontendUrl + "/payment/return?" + query);
    }

    /** Rechecks payment state in its own transaction so cancellation failures cannot roll back a verified callback. */
    public PaymentProviderDtos.AttemptState stateForCancellation(String slug, UUID attemptId) {
        return reconciliationTransactions.execute(status -> state(slug, attemptId));
    }

    public PaymentProviderDtos.AttemptState state(String slug, UUID attemptId) {
        Map<String, Object> attempt;
        try { attempt = jdbc.queryForMap("SELECT * FROM payment_attempts WHERE id=? AND store_slug=?", attemptId, slug); }
        catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment attempt not found.");
        }
        if (List.of("INITIATED", "PENDING").contains(attempt.get("status")) && claimVerification(attemptId)) {
            try {
                PaymentProviderCredentialsService.ProviderConfiguration config = credentials.decryptSnapshot(
                        (String) attempt.get("encrypted_provider_configuration"));
                Verification verification = PaymentProvider.KHALTI.name().equals(attempt.get("provider"))
                        ? verifyKhalti(attempt, config, (String) attempt.get("provider_reference"))
                        : verifyEsewa(attempt, config, null);
                recordVerification(attemptId, verification);
            } catch (Exception verificationFailure) {
                recordStatus(attemptId, "PENDING", null, false);
            }
        }
        Map<String, Object> current = jdbc.queryForMap(
                "SELECT status,provider,order_number,amount_paisa,currency FROM payment_attempts WHERE id=? AND store_slug=?",
                attemptId, slug);
        return new PaymentProviderDtos.AttemptState((String) current.get("status"), (String) current.get("provider"),
                (String) current.get("order_number"), ((Number) current.get("amount_paisa")).longValue(),
                (String) current.get("currency"));
    }

    private boolean claimVerification(UUID attemptId) {
        return jdbc.update("""
                UPDATE payment_attempts SET last_verification_attempt_at=now()
                 WHERE id=? AND status IN ('INITIATED','PENDING')
                   AND (last_verification_attempt_at IS NULL OR last_verification_attempt_at < now()-interval '10 seconds')
                """, attemptId) == 1;
    }

    private void recordVerification(UUID attemptId, Verification verification) {
        if ("COMPLETED".equals(verification.status())) recordStatus(attemptId, "COMPLETED", verification.reference(), true);
        else if ("FAILED".equals(verification.status())) recordStatus(attemptId, "FAILED", verification.reference(), false);
        else recordStatus(attemptId, "PENDING", verification.reference(), false);
    }

    private ProviderCheckout initiateKhalti(PaymentProviderCredentialsService.ProviderConfiguration config,
            UUID attemptId, String merchantOrderReference, OrderDtos.OrderResponse order, long amountPaisa) {
        String base = config.sandbox() ? "https://dev.khalti.com/api/v2" : "https://khalti.com/api/v2";
        String callback = backendPublicUrl + "/api/store/payments/return?attemptId=" + attemptId;
        Map<String, Object> request = Map.of(
                "return_url", callback,
                "website_url", frontendUrl,
                "amount", Long.toString(amountPaisa),
                "purchase_order_id", merchantOrderReference,
                "purchase_order_name", "Order " + order.orderNumber(),
                "customer_info", Map.of("name", order.customerName(), "email", nullToEmpty(order.email()), "phone", order.phone()));
        Map<String, Object> response = restClient.post().uri(base + "/epayment/initiate/")
                .header("Authorization", "Key " + config.secretKey())
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(MAP_TYPE);
        String pidx = string(response, "pidx");
        String paymentUrl = string(response, "payment_url");
        if (pidx.isBlank() || !safeGatewayUrl(paymentUrl, config.sandbox() ? "test-pay.khalti.com" : "pay.khalti.com"))
            throw new IllegalStateException("Khalti returned an invalid checkout response.");
        return new ProviderCheckout(pidx, paymentUrl, "GET", Map.of());
    }

    private ProviderCheckout initiateEsewa(PaymentProviderCredentialsService.ProviderConfiguration config,
            UUID attemptId, String merchantOrderReference, BigDecimal amount) {
        String base = config.sandbox() ? "https://rc-epay.esewa.com.np" : "https://epay.esewa.com.np";
        String callback = backendPublicUrl + "/api/store/payments/return?attemptId=" + attemptId;
        String total = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", total);
        fields.put("tax_amount", "0");
        fields.put("total_amount", total);
        fields.put("transaction_uuid", merchantOrderReference);
        fields.put("product_code", config.merchantCode());
        fields.put("product_service_charge", "0");
        fields.put("product_delivery_charge", "0");
        fields.put("success_url", callback);
        fields.put("failure_url", callback);
        fields.put("signed_field_names", "total_amount,transaction_uuid,product_code");
        fields.put("signature", hmac(config.secretKey(), signatureMessage(fields, fields.get("signed_field_names"))));
        return new ProviderCheckout(merchantOrderReference, base + "/api/epay/main/v2/form", "POST", fields);
    }

    private Verification verifyKhalti(Map<String, Object> attempt,
            PaymentProviderCredentialsService.ProviderConfiguration config, String callbackPidx) {
        String storedPidx = (String) attempt.get("provider_reference");
        if (callbackPidx == null || callbackPidx.isBlank() || !constantEquals(storedPidx, callbackPidx))
            return new Verification("PENDING", null);
        String base = config.sandbox() ? "https://dev.khalti.com/api/v2" : "https://khalti.com/api/v2";
        Map<String, Object> response;
        try {
            response = restClient.post().uri(base + "/epayment/lookup/")
                    .header("Authorization", "Key " + config.secretKey()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("pidx", storedPidx)).retrieve().body(MAP_TYPE);
        } catch (RestClientResponseException providerResponse) {
            try { response = mapper.readValue(providerResponse.getResponseBodyAsString(), JSON_MAP_TYPE); }
            catch (Exception unreadable) { return new Verification("PENDING", null); }
        }
        if (response == null || !constantEquals(string(response, "pidx"), storedPidx)) return new Verification("PENDING", null);
        if (!paisaMatches(response.get("total_amount"), ((Number) attempt.get("amount_paisa")).longValue())) return new Verification("PENDING", null);
        String status = string(response, "status");
        if ("Completed".equalsIgnoreCase(status)) return new Verification("COMPLETED", string(response, "transaction_id"));
        if (List.of("Expired", "User canceled", "Canceled", "Failed", "Refunded", "Partially refunded").stream().anyMatch(s -> s.equalsIgnoreCase(status)))
            return new Verification("FAILED", string(response, "transaction_id"));
        return new Verification("PENDING", string(response, "transaction_id"));
    }

    private Verification verifyEsewa(Map<String, Object> attempt,
            PaymentProviderCredentialsService.ProviderConfiguration config, String encodedData) {
        if (encodedData != null && !encodedData.isBlank()) {
            Map<String, Object> callback;
            try {
                byte[] decoded = Base64.getDecoder().decode(encodedData);
                callback = mapper.readValue(decoded, JSON_MAP_TYPE);
            } catch (Exception malformed) {
                return new Verification("PENDING", null);
            }
            String signedNames = string(callback, "signed_field_names");
            if (!verifyEsewaResponseSignature(callback, signedNames, config.secretKey())) return new Verification("PENDING", null);
            if (!constantEquals(string(callback, "transaction_uuid"), (String) attempt.get("merchant_order_reference"))
                    || !constantEquals(string(callback, "product_code"), config.merchantCode())
                    || !amountMatches(callback.get("total_amount"), ((Number) attempt.get("amount_paisa")).longValue()))
                return new Verification("PENDING", null);
        }

        String base = config.sandbox() ? "https://uat.esewa.com.np" : "https://epay.esewa.com.np";
        Map<String, Object> status = restClient.get().uri(uri -> uri
                        .scheme("https").host(URI.create(base).getHost()).path("/api/epay/transaction/status/")
                        .queryParam("product_code", config.merchantCode())
                        .queryParam("total_amount", paisaToNpr(((Number) attempt.get("amount_paisa")).longValue()))
                        .queryParam("transaction_uuid", (String) attempt.get("merchant_order_reference")).build())
                .retrieve().body(MAP_TYPE);
        if (status == null || !constantEquals(string(status, "scd"), config.merchantCode())
                || !amountMatches(status.get("totalAmount"), ((Number) attempt.get("amount_paisa")).longValue()))
            return new Verification("PENDING", null);
        String statusText = string(status, "status");
        if ("COMPLETE".equalsIgnoreCase(statusText)) return new Verification("COMPLETED", string(status, "refId"));
        if (List.of("NOT_FOUND", "CANCELED", "FULL_REFUND", "PARTIAL_REFUND").stream().anyMatch(s -> s.equalsIgnoreCase(statusText)))
            return new Verification("FAILED", string(status, "refId"));
        return new Verification("PENDING", string(status, "refId"));
    }

    private boolean verifyEsewaResponseSignature(Map<String, Object> callback, String signedNames, String secret) {
        if (signedNames.isBlank()) return false;
        List<String> names = List.of(signedNames.split(","));
        if (!names.containsAll(List.of("transaction_code", "status", "total_amount", "transaction_uuid", "product_code", "signed_field_names"))) return false;
        String expected = hmac(secret, signatureMessage(callback, signedNames));
        return constantEquals(expected, string(callback, "signature"));
    }

    private void recordStatus(UUID attemptId, String status, String providerReference, boolean verifiedSuccess) {
        transactions.executeWithoutResult(transaction -> {
            Map<String, Object> attempt = jdbc.queryForMap("SELECT * FROM payment_attempts WHERE id=? FOR UPDATE", attemptId);
            if ("COMPLETED".equals(attempt.get("status"))) return;
            // A provider-confirmed success must win over an earlier transient failure,
            // but a later non-success callback must never downgrade a completed attempt.
            if (!"FAILED".equals(attempt.get("status")) || "COMPLETED".equals(status))
                jdbc.update("UPDATE payment_attempts SET status=?,provider_transaction_id=COALESCE(?,provider_transaction_id),updated_at=now(),completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE completed_at END WHERE id=?",
                        status, providerReference, status, attemptId);
            UUID businessId = (UUID) attempt.get("business_id");
            UUID orderId = (UUID) attempt.get("order_id");
            if (!verifiedSuccess) {
                if ("FAILED".equals(status)) resetPendingOrderPaymentIfNoLiveAttempt(businessId, orderId);
                return;
            }

            PaymentMethod method = PaymentMethod.valueOf((String) attempt.get("payment_method"));
            PaymentStatus paymentStatus = method == PaymentMethod.ONLINE ? PaymentStatus.PAID : PaymentStatus.PARTIALLY_PAID;
            String prefix = tables.prefix(businessId).orElse(null);
            if (prefix != null) {
                String orderTable = tables.table(prefix, "orders");
                Map<String, Object> order = jdbc.queryForMap("SELECT payment_method,payment_status,status,total_amount,advance_amount FROM " + orderTable + " WHERE id=? FOR UPDATE", orderId);
                if ("CANCELLED".equals(order.get("status"))) throw new IllegalStateException("A cancelled order received a payment callback and requires manual reconciliation.");
                verifyAttemptAmount(attempt, order, method);
                if (!"PAID".equals(order.get("payment_status")) && !"PARTIALLY_PAID".equals(order.get("payment_status")))
                    jdbc.update("UPDATE " + orderTable + " SET payment_status=? WHERE id=?", paymentStatus.name(), orderId);
            } else {
                CustomerOrder order = legacyOrders.findByIdAndBusinessId(orderId, businessId).orElseThrow();
                if (order.getStatus() == com.byawasaya.platform.orders.OrderStatus.CANCELLED) throw new IllegalStateException("A cancelled order received a payment callback and requires manual reconciliation.");
                long expected = (method == PaymentMethod.ONLINE ? order.getTotalAmount() : order.getAdvanceAmount())
                        .movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
                if (expected != ((Number) attempt.get("amount_paisa")).longValue()) throw new IllegalStateException("Payment amount no longer matches the order.");
                if (order.getPaymentStatus() != PaymentStatus.PAID && order.getPaymentStatus() != PaymentStatus.PARTIALLY_PAID) {
                    order.setPaymentStatus(paymentStatus);
                    legacyOrders.save(order);
                }
            }
            jdbc.update("UPDATE customer_order_history SET payment_status=? WHERE business_id=? AND order_id=?",
                    paymentStatus.name(), businessId, orderId);
        });
    }

    private void resetPendingOrderPaymentIfNoLiveAttempt(UUID businessId, UUID orderId) {
        Boolean hasLive = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM payment_attempts WHERE business_id=? AND order_id=? AND status IN ('INITIATED','PENDING'))",
                Boolean.class, businessId, orderId);
        Boolean hasCompleted = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM payment_attempts WHERE business_id=? AND order_id=? AND status='COMPLETED')",
                Boolean.class, businessId, orderId);
        if (Boolean.TRUE.equals(hasLive) || Boolean.TRUE.equals(hasCompleted)) return;

        String prefix = tables.prefix(businessId).orElse(null);
        int changed;
        if (prefix != null) {
            changed = jdbc.update("UPDATE " + tables.table(prefix, "orders") +
                    " SET payment_status='UNPAID' WHERE id=? AND payment_status='PAYMENT_PENDING' AND payment_method IN ('ONLINE','ADVANCE_COD')", orderId);
        } else {
            CustomerOrder order = legacyOrders.findByIdAndBusinessId(orderId, businessId).orElse(null);
            if (order == null || order.getPaymentStatus() != PaymentStatus.PAYMENT_PENDING
                    || (order.getPaymentMethod() != PaymentMethod.ONLINE && order.getPaymentMethod() != PaymentMethod.ADVANCE_COD)) return;
            order.setPaymentStatus(PaymentStatus.UNPAID);
            legacyOrders.save(order);
            changed = 1;
        }
        if (changed == 1) jdbc.update("UPDATE customer_order_history SET payment_status='UNPAID' WHERE business_id=? AND order_id=? AND payment_status='PAYMENT_PENDING'", businessId, orderId);
    }

    private void verifyAttemptAmount(Map<String, Object> attempt, Map<String, Object> order, PaymentMethod method) {
        if (!method.name().equals(order.get("payment_method"))) throw new IllegalStateException("Payment method does not match the order.");
        BigDecimal expectedAmount = method == PaymentMethod.ONLINE ? (BigDecimal) order.get("total_amount") : (BigDecimal) order.get("advance_amount");
        long expected = expectedAmount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        if (expected != ((Number) attempt.get("amount_paisa")).longValue()) throw new IllegalStateException("Payment amount no longer matches the order.");
    }

    private void ensureOrderUnpaid(UUID businessId, UUID orderId, PaymentMethod method) {
        String prefix = tables.prefix(businessId).orElse(null);
        String paymentStatus;
        String orderStatus;
        if (prefix != null) {
            Map<String, Object> row = jdbc.queryForMap("SELECT payment_status,status FROM " + tables.table(prefix, "orders") + " WHERE id=?", orderId);
            paymentStatus = (String) row.get("payment_status"); orderStatus = (String) row.get("status");
        } else {
            CustomerOrder order = legacyOrders.findByIdAndBusinessId(orderId, businessId).orElseThrow();
            paymentStatus = order.getPaymentStatus().name(); orderStatus = order.getStatus().name();
        }
        if ("CANCELLED".equals(orderStatus)) throw new ResponseStatusException(HttpStatus.CONFLICT, "A cancelled order cannot be paid.");
        if (method == PaymentMethod.ONLINE && "PAID".equals(paymentStatus)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has already been paid.");
        if (method == PaymentMethod.ADVANCE_COD && ("PAID".equals(paymentStatus) || "PARTIALLY_PAID".equals(paymentStatus)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The required advance for this order has already been paid.");
    }

    private Map<String, Object> loadAttempt(UUID id) {
        try { return jdbc.queryForMap("SELECT * FROM payment_attempts WHERE id=?", id); }
        catch (org.springframework.dao.EmptyResultDataAccessException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment attempt not found."); }
    }

    private String signatureMessage(Map<String, ?> values, String names) {
        return java.util.Arrays.stream(names.split(",")).map(String::trim)
                .map(name -> name + "=" + String.valueOf(values.get(name))).collect(java.util.stream.Collectors.joining(","));
    }

    private String hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("Could not create payment signature.", error); }
    }

    private boolean amountMatches(Object value, long amountPaisa) {
        if (value == null) return false;
        try { return new BigDecimal(String.valueOf(value)).movePointRight(2).compareTo(BigDecimal.valueOf(amountPaisa)) == 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    private boolean paisaMatches(Object value, long amountPaisa) {
        if (value == null) return false;
        try { return new BigDecimal(String.valueOf(value)).compareTo(BigDecimal.valueOf(amountPaisa)) == 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    private String paisaToNpr(long amountPaisa) { return BigDecimal.valueOf(amountPaisa, 2).toPlainString(); }
    private boolean constantEquals(String left, String right) { return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8)); }
    private boolean safeGatewayUrl(String value, String expectedHost) {
        try { URI uri = URI.create(value); return "https".equalsIgnoreCase(uri.getScheme()) && expectedHost.equalsIgnoreCase(uri.getHost()); }
        catch (Exception invalid) { return false; }
    }
    private static String string(Map<String, ?> values, String key) { Object value = values == null ? null : values.get(key); return value == null ? "" : String.valueOf(value); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String trimSlash(String value) { return value == null ? "" : value.replaceAll("/+$", ""); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private Map<String, String> readFields(String json) {
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ignored) { return Map.of(); }
    }

    private record ProviderCheckout(String providerReference, String url, String method, Map<String, String> fields) {}
    private record ExistingAttempt(UUID id, String providerReference, String redirectUrl, Map<String, String> redirectFields, String status) {}
    private record Verification(String status, String reference) {}
}
