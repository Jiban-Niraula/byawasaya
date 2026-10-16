package com.byawasaya.platform.plans;

import com.byawasaya.platform.audit.AuditService;
import com.byawasaya.platform.auth.TokenService;
import com.byawasaya.platform.business.Business;
import com.byawasaya.platform.business.BusinessRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Manual Byawasaya subscription invoices and administrator-verified payment records. */
@RestController
public class SubscriptionInvoiceController {
    private static final ZoneId NEPAL = ZoneId.of("Asia/Kathmandu");
    private static final DateTimeFormatter INVOICE_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbc;
    private final BusinessRepository businesses;
    private final AuditService audit;

    public SubscriptionInvoiceController(JdbcTemplate jdbc, BusinessRepository businesses, AuditService audit) {
        this.jdbc = jdbc;
        this.businesses = businesses;
        this.audit = audit;
    }

    public record IssueRequest(
            @NotBlank @Pattern(regexp = "MONTHLY|ANNUAL") String billingCycle,
            @NotNull LocalDate dueOn) {}

    public record PaymentRequest(
            @NotBlank @Pattern(regexp = "BANK_TRANSFER|CASH|ESEWA|KHALTI|OTHER") String paymentMethod,
            @NotBlank @Size(max = 120) String paymentReference,
            @Size(max = 500) String notes,
            @AssertTrue boolean verified) {}

    public record VoidRequest(@NotBlank @Size(max = 500) String reason) {}

    @GetMapping("/api/platform/subscription-invoices")
    public List<Map<String, Object>> platformInvoices() {
        return jdbc.queryForList(invoiceSelect() + " ORDER BY i.issued_at DESC");
    }

    @GetMapping("/api/tenant-billing/invoices")
    public List<Map<String, Object>> tenantInvoices(Authentication auth) {
        TokenService.TokenPrincipal principal = tenantPrincipal(auth);
        return jdbc.queryForList(invoiceSelect() + " WHERE i.business_id=? ORDER BY i.issued_at DESC", principal.businessId());
    }

    @PostMapping("/api/platform/subscriptions/{subscriptionId}/invoices")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> issue(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody IssueRequest request,
            Authentication auth) {
        String cycle = request.billingCycle().toUpperCase(Locale.ROOT);
        Map<String, Object> subscription;
        try {
            subscription = jdbc.queryForMap("""
                    SELECT s.id,s.business_id AS "businessId",s.plan_code AS "planCode",
                           s.plan_name_snapshot AS "planName",s.monthly_price_snapshot AS "monthlyPrice",
                           s.annual_price_snapshot AS "annualPrice",s.discount_amount_npr AS "discount",
                           s.promo_code_id AS "promoCodeId",s.status,s.current_period_ends_at AS "periodEndsAt",
                           b.name AS "businessName",b.owner_name AS "ownerName",b.owner_email AS "ownerEmail",
                           b.tenant_tag AS "storeCode"
                    FROM subscriptions s JOIN businesses b ON b.id=s.business_id
                    WHERE s.id=? FOR UPDATE OF s
                    """, subscriptionId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found.");
        }

        if ("CANCELLED".equals(subscription.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A cancelled subscription cannot receive a new invoice.");
        }
        Long openInvoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_invoices WHERE subscription_id=? AND status='OPEN'",
                Long.class, subscriptionId);
        if (openInvoices != null && openInvoices > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Record or void the existing open invoice before issuing another.");
        }

        LocalDate today = LocalDate.now(NEPAL);
        if (request.dueOn().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The invoice due date cannot be in the past.");
        }
        LocalDate periodStart = nextPeriodStart(subscription, today);
        LocalDate periodEnd = "ANNUAL".equals(cycle)
                ? periodStart.plusYears(1).minusDays(1)
                : periodStart.plusMonths(1).minusDays(1);
        BigDecimal subtotal = ((BigDecimal) subscription.get("ANNUAL".equals(cycle) ? "annualPrice" : "monthlyPrice"))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        Long priorInvoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_invoices WHERE subscription_id=? AND status<>'VOID'",
                Long.class, subscriptionId);
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        if (priorInvoices != null && priorInvoices == 0L && subscription.get("promoCodeId") != null) {
            BigDecimal applied = (BigDecimal) subscription.get("discount");
            if (applied != null) discount = applied.min(subtotal).max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal amountDue = subtotal.subtract(discount).setScale(2, java.math.RoundingMode.HALF_UP);
        if (amountDue.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This plan or discount has no amount to collect; issue an invoice only when payment is due.");
        }

        Long serial = jdbc.queryForObject("SELECT nextval('subscription_invoice_number_seq')", Long.class);
        String invoiceNumber = "BYA-" + today.format(INVOICE_MONTH) + "-" + String.format(Locale.ROOT, "%08d", serial);
        UUID invoiceId = UUID.randomUUID();
        UUID businessId = (UUID) subscription.get("businessId");
        jdbc.update("""
                INSERT INTO subscription_invoices (
                    id,invoice_number,business_id,subscription_id,business_name_snapshot,owner_name_snapshot,
                    owner_email_snapshot,store_code_snapshot,plan_code_snapshot,plan_name_snapshot,billing_cycle,
                    service_period_start,service_period_end,due_on,subtotal_npr,discount_npr,amount_due_npr,
                    status,issued_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'OPEN',?)
                """, invoiceId, invoiceNumber, businessId, subscriptionId,
                subscription.get("businessName"), subscription.get("ownerName"), subscription.get("ownerEmail"),
                subscription.get("storeCode"), subscription.get("planCode"), subscription.get("planName"), cycle,
                periodStart, periodEnd, request.dueOn(), subtotal, discount, amountDue, auth.getName());
        audit.record(auth.getName(), role(auth), businessId, "SUBSCRIPTION_INVOICE_ISSUED", "SUBSCRIPTION_INVOICE",
                invoiceId.toString(), "SUCCESS", "{\"invoiceNumber\":\"" + invoiceNumber + "\",\"amountNpr\":" + amountDue + "}");
        return invoice(invoiceId);
    }

    @PostMapping("/api/platform/subscription-invoices/{invoiceId}/payment")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "adminDashboard", key = "'overview'")
    public Map<String, Object> recordPayment(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody PaymentRequest request,
            Authentication auth) {
        Map<String, Object> invoice = lockInvoice(invoiceId);
        if (!"OPEN".equals(invoice.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an open invoice can receive a payment.");
        }
        UUID businessId = (UUID) invoice.get("businessId");
        BigDecimal amount = (BigDecimal) invoice.get("amountDueNpr");
        String method = request.paymentMethod().toUpperCase(Locale.ROOT);
        String subscriptionStatus = jdbc.queryForObject("SELECT status FROM subscriptions WHERE id=? FOR UPDATE",
                String.class, invoice.get("subscriptionId"));
        jdbc.update("""
                INSERT INTO subscription_invoice_payments
                    (id,invoice_id,amount_npr,payment_method,payment_reference,notes,recorded_by)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), invoiceId, amount, method, request.paymentReference().trim(),
                blankToNull(request.notes()), auth.getName());
        jdbc.update("UPDATE subscription_invoices SET status='PAID' WHERE id=?", invoiceId);

        LocalDate periodStart = localDate(invoice.get("servicePeriodStart"));
        LocalDate periodEnd = localDate(invoice.get("servicePeriodEnd"));
        if (!"CANCELLED".equals(subscriptionStatus)) {
            jdbc.update("""
                    UPDATE subscriptions
                    SET status='ACTIVE',trial_ends_at=NULL,current_period_starts_at=?,current_period_ends_at=?,updated_at=now()
                    WHERE id=?
                    """, java.sql.Timestamp.from(periodStart.atStartOfDay(NEPAL).toInstant()),
                    java.sql.Timestamp.from(periodEnd.plusDays(1).atStartOfDay(NEPAL).toInstant()), invoice.get("subscriptionId"));
            Business business = businesses.findById(businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The invoiced business no longer exists."));
            business.setTrialEndsAt(null);
        }

        audit.record(auth.getName(), role(auth), businessId, "SUBSCRIPTION_INVOICE_PAYMENT_RECORDED", "SUBSCRIPTION_INVOICE",
                invoiceId.toString(), "SUCCESS", "{\"invoiceNumber\":\"" + invoice.get("invoiceNumber")
                        + "\",\"amountNpr\":" + amount + ",\"method\":\"" + method + "\"}");
        return invoice(invoiceId);
    }

    @PostMapping("/api/platform/subscription-invoices/{invoiceId}/void")
    @Transactional
    public Map<String, Object> voidInvoice(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody VoidRequest request,
            Authentication auth) {
        Map<String, Object> invoice = lockInvoice(invoiceId);
        if (!"OPEN".equals(invoice.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an open invoice can be voided.");
        }
        String reason = request.reason().trim();
        jdbc.update("UPDATE subscription_invoices SET status='VOID',voided_at=now(),voided_by=?,void_reason=? WHERE id=?",
                auth.getName(), reason, invoiceId);
        audit.record(auth.getName(), role(auth), (UUID) invoice.get("businessId"), "SUBSCRIPTION_INVOICE_VOIDED",
                "SUBSCRIPTION_INVOICE", invoiceId.toString(), "SUCCESS",
                "{\"invoiceNumber\":\"" + invoice.get("invoiceNumber") + "\"}");
        return invoice(invoiceId);
    }

    private Map<String, Object> lockInvoice(UUID invoiceId) {
        try {
            return jdbc.queryForMap(invoiceSelect() + " WHERE i.id=? FOR UPDATE OF i", invoiceId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found.");
        }
    }

    private Map<String, Object> invoice(UUID invoiceId) {
        try {
            return jdbc.queryForMap(invoiceSelect() + " WHERE i.id=?", invoiceId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found.");
        }
    }

    private static String invoiceSelect() {
        return """
                SELECT i.id,i.invoice_number AS "invoiceNumber",i.business_id AS "businessId",
                       i.subscription_id AS "subscriptionId",i.business_name_snapshot AS "businessName",
                       i.owner_name_snapshot AS "ownerName",i.owner_email_snapshot AS "ownerEmail",
                       i.store_code_snapshot AS "storeCode",i.plan_code_snapshot AS "planCode",
                       i.plan_name_snapshot AS "planName",i.billing_cycle AS "billingCycle",s.status AS "subscriptionStatus",
                       i.service_period_start::text AS "servicePeriodStart",i.service_period_end::text AS "servicePeriodEnd",
                       i.due_on::text AS "dueOn",i.subtotal_npr AS "subtotalNpr",i.discount_npr AS "discountNpr",
                       i.amount_due_npr AS "amountDueNpr",
                       CASE WHEN i.status='OPEN' AND i.due_on<(now() AT TIME ZONE 'Asia/Kathmandu')::date THEN 'OVERDUE' ELSE i.status END AS status,
                       i.issued_at AS "issuedAt",i.issued_by AS "issuedBy",i.voided_at AS "voidedAt",
                       i.voided_by AS "voidedBy",i.void_reason AS "voidReason",
                       p.amount_npr AS "paidAmountNpr",p.payment_method AS "paymentMethod",
                       p.payment_reference AS "paymentReference",p.notes AS "paymentNotes",
                       p.received_at AS "receivedAt",p.recorded_by AS "recordedBy"
                FROM subscription_invoices i
                JOIN subscriptions s ON s.id=i.subscription_id
                LEFT JOIN subscription_invoice_payments p ON p.invoice_id=i.id
                """;
    }

    private static LocalDate nextPeriodStart(Map<String, Object> subscription, LocalDate today) {
        Object rawEnd = subscription.get("periodEndsAt");
        LocalDate start = today;
        if (rawEnd instanceof java.time.OffsetDateTime odt) {
            var kathmanduEnd = odt.toInstant().atZone(NEPAL);
            LocalDate endDate = kathmanduEnd.toLocalDate();
            if (!kathmanduEnd.toLocalTime().equals(LocalTime.MIDNIGHT)) endDate = endDate.plusDays(1);
            if (endDate.isAfter(start)) start = endDate;
        } else if (rawEnd instanceof java.sql.Timestamp timestamp) {
            var kathmanduEnd = timestamp.toInstant().atZone(NEPAL);
            LocalDate endDate = kathmanduEnd.toLocalDate();
            if (!kathmanduEnd.toLocalTime().equals(LocalTime.MIDNIGHT)) endDate = endDate.plusDays(1);
            if (endDate.isAfter(start)) start = endDate;
        } else if (rawEnd instanceof Instant instant) {
            var kathmanduEnd = instant.atZone(NEPAL);
            LocalDate endDate = kathmanduEnd.toLocalDate();
            if (!kathmanduEnd.toLocalTime().equals(LocalTime.MIDNIGHT)) endDate = endDate.plusDays(1);
            if (endDate.isAfter(start)) start = endDate;
        }
        return start;
    }

    private static TokenService.TokenPrincipal tenantPrincipal(Authentication auth) {
        if (auth == null || !(auth.getDetails() instanceof TokenService.TokenPrincipal principal)
                || principal.businessId() == null || !"STORE_OWNER".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant owner context required.");
        }
        return principal;
    }

    private static String role(Authentication auth) {
        return auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).findFirst().orElse("SUPERADMIN");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }
}
