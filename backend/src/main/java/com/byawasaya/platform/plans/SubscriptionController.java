package com.byawasaya.platform.plans;

import com.byawasaya.platform.audit.AuditService;
import com.byawasaya.platform.auth.TokenService;
import com.byawasaya.platform.business.Business;
import com.byawasaya.platform.business.BusinessRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/platform/subscriptions")
public class SubscriptionController {
    private final JdbcTemplate jdbc;
    private final PlatformPlanRepository plans;
    private final BusinessRepository businesses;
    private final AuditService audit;
    private final TokenService sessions;

    public SubscriptionController(JdbcTemplate jdbc, PlatformPlanRepository plans, BusinessRepository businesses,
            AuditService audit, TokenService sessions) {
        this.jdbc = jdbc;
        this.plans = plans;
        this.businesses = businesses;
        this.audit = audit;
        this.sessions = sessions;
    }

    public record UpdateRequest(
            @Pattern(regexp = "TRIAL|ACTIVE|PAST_DUE|SUSPENDED|CANCELLED") String status,
            @Size(max = 40) String planCode,
            Instant trialEndsAt) {}

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT s.id,s.business_id AS "businessId",b.name AS "businessName",b.tenant_tag AS "storeCode",
                       s.plan_code AS "planCode",s.plan_name_snapshot AS "planName",
                       s.monthly_price_snapshot AS "monthlyPriceNpr",s.annual_price_snapshot AS "annualPriceNpr",
                       s.discount_amount_npr AS "discountAmountNpr",s.status,
                       s.trial_starts_at AS "trialStartsAt",s.trial_ends_at AS "trialEndsAt",
                       s.current_period_starts_at AS "periodStartsAt",s.current_period_ends_at AS "periodEndsAt",
                       s.created_at AS "createdAt"
                FROM subscriptions s JOIN businesses b ON b.id=s.business_id
                ORDER BY s.created_at DESC
                """);
    }

    @PatchMapping("/{id}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "adminDashboard", key = "'overview'")
    public Map<String, Object> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request, Authentication auth) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("SELECT business_id,plan_code,status FROM subscriptions WHERE id=? FOR UPDATE", id);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found.");
        }
        if ("ACTIVE".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Record a verified invoice payment to activate a paid subscription.");
        }

        UUID businessId = (UUID) row.get("business_id");
        String code = request.planCode() == null ? (String) row.get("plan_code") : request.planCode().toUpperCase(Locale.ROOT);
        PlatformPlan plan = plans.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan not found."));
        String currentStatus = (String) row.get("status");
        String status = request.status() == null ? currentStatus : request.status();
        Instant trialEnd = request.trialEndsAt();
        if ("TRIAL".equals(status) && trialEnd != null && !trialEnd.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trial end must be in the future.");
        }
        if ("PAST_DUE".equals(status) && trialEnd == null) trialEnd = Instant.now().plus(java.time.Duration.ofDays(7));
        if ("TRIAL".equals(status) && trialEnd == null) {
            Object existing = jdbc.queryForObject("SELECT trial_ends_at FROM subscriptions WHERE id=?", Object.class, id);
            if (existing instanceof java.time.OffsetDateTime offset) trialEnd = offset.toInstant();
            else if (existing instanceof java.sql.Timestamp timestamp) trialEnd = timestamp.toInstant();
            if (trialEnd == null) trialEnd = Instant.now().plusSeconds(plan.getTrialDays() * 86400L);
        }

        jdbc.update("""
                UPDATE subscriptions
                SET plan_code=?,plan_name_snapshot=?,monthly_price_snapshot=?,annual_price_snapshot=?,
                    discount_amount_npr=CASE WHEN plan_code<>? THEN 0 ELSE discount_amount_npr END,
                    promo_code_id=CASE WHEN plan_code<>? THEN NULL ELSE promo_code_id END,
                    status=?,trial_ends_at=?,updated_at=now()
                WHERE id=?
                """, plan.getCode(), plan.getName(), plan.getMonthlyPriceNpr(), plan.getAnnualPriceNpr(),
                plan.getCode(), plan.getCode(), status, trialEnd, id);

        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found."));
        business.setPlanCode(plan.getCode());
        if (request.status() != null) {
            if ("TRIAL".equals(status)) {
                business.setStatus(com.byawasaya.platform.business.BusinessStatus.ACTIVE);
                business.setTrialStartsAt(Instant.now());
                business.setTrialEndsAt(trialEnd);
            } else if ("PAST_DUE".equals(status)) {
                business.setTrialEndsAt(trialEnd);
            } else if ("SUSPENDED".equals(status) || "CANCELLED".equals(status)) {
                business.setStatus(com.byawasaya.platform.business.BusinessStatus.SUSPENDED);
                sessions.revokeBusiness(businessId);
            }
        }
        audit.record(auth.getName(), "SUPERADMIN", businessId, "SUBSCRIPTION_UPDATED", "SUBSCRIPTION", id.toString(),
                "SUCCESS", "{\"status\":\"" + status + "\",\"planCode\":\"" + plan.getCode() + "\"}");
        return jdbc.queryForMap("""
                SELECT id,business_id AS "businessId",plan_code AS "planCode",plan_name_snapshot AS "planName",
                       monthly_price_snapshot AS "monthlyPriceNpr",annual_price_snapshot AS "annualPriceNpr",
                       discount_amount_npr AS "discountAmountNpr",status,trial_starts_at AS "trialStartsAt",
                       trial_ends_at AS "trialEndsAt"
                FROM subscriptions WHERE id=?
                """, id);
    }
}
