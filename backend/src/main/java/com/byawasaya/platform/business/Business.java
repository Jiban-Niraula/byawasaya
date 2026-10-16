package com.byawasaya.platform.business;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "businesses", uniqueConstraints = @UniqueConstraint(name = "uk_business_slug", columnNames = "slug"))
public class Business {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 80) private String slug;
    @Column(nullable = false, length = 12, unique = true) private String tenantTag;
    @Column(nullable = false, length = 120) private String ownerName;
    @Column(length = 180) private String ownerEmail;
    @Column(length = 30) private String phone;
    @Column(length = 1000) private String description;
    @Column(nullable = false, length = 40) private String plan;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private BusinessStatus status;
    @Column(nullable = false, length = 40) private String templateKey = "classic";
    @Column(nullable = false, length = 20) private String primaryColor = "#2f6f5e";
    @Column(nullable = false) private boolean codEnabled = true;
    @Column(nullable = false) private boolean onlinePaymentEnabled = false;
    @Column(nullable = false) private int advancePaymentPercent = 30;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal deliveryFee = new BigDecimal("150.00");
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal minimumOrderAmount = BigDecimal.ZERO;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(name="trial_starts_at") private Instant trialStartsAt;
    @Column(name="trial_ends_at") private Instant trialEndsAt;
    @Column(name="approved_at") private Instant approvedAt;
    @Column(name="approved_by", length=180) private String approvedBy;
    @Column(name="review_notes", length=2000) private String reviewNotes;
    @Column(name="logo_asset_id") private UUID logoAssetId;

    protected Business() {}

    public Business(String name, String ownerName, BusinessPlan plan) {
        this.name = name == null ? null : name.trim();
        this.ownerName = ownerName == null ? null : ownerName.trim();
        this.plan = (plan == null ? BusinessPlan.STARTER : plan).name();
        this.status = BusinessStatus.ACTIVE;
        String normalized = (name == null ? "store" : name.toLowerCase()).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        this.slug = (normalized.isBlank() ? "store" : normalized) + "-" + UUID.randomUUID().toString().substring(0, 4);
        this.tenantTag = createTenantTag(name);
    }

    public Business(String name, String ownerName, String planCode) {
        this(name, ownerName, BusinessPlan.STARTER);
        this.plan = planCode == null || planCode.isBlank() ? BusinessPlan.STARTER.name() : planCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Immutable generated store code; never derive physical identifiers from a user-controlled name. */
    private static String createTenantTag(String value) {
        return "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @PrePersist void setCreatedAt() { if (createdAt == null) createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getTenantTag() { return tenantTag; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getPhone() { return phone; }
    public String getDescription() { return description; }
    public BusinessPlan getPlan() { try { return BusinessPlan.valueOf(plan); } catch (IllegalArgumentException ex) { return BusinessPlan.STARTER; } }
    public String getPlanCode() { return plan; }
    public void setPlanCode(String value) { if (value == null || !value.matches("[A-Z][A-Z0-9_]{2,39}")) throw new IllegalArgumentException("Invalid plan code."); this.plan = value; }
    public BusinessStatus getStatus() { return status; }
    public String getTemplateKey() { return templateKey; }
    public String getPrimaryColor() { return primaryColor; }
    public boolean isCodEnabled() { return codEnabled; }
    public boolean isOnlinePaymentEnabled() { return onlinePaymentEnabled; }
    public int getAdvancePaymentPercent() { return advancePaymentPercent; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getMinimumOrderAmount() { return minimumOrderAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getTrialStartsAt() { return trialStartsAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovedBy() { return approvedBy; }
    public String getReviewNotes() { return reviewNotes; }
    public UUID getLogoAssetId() { return logoAssetId; }
    public boolean isOperational() { return status == BusinessStatus.ACTIVE && (trialEndsAt == null || trialEndsAt.isAfter(Instant.now())); }
    public void setLogoAssetId(UUID value) { this.logoAssetId = value; }
    public void setTrialStartsAt(Instant value) { this.trialStartsAt = value; }
    public void setTrialEndsAt(Instant value) { this.trialEndsAt = value; }
    public void setApprovedAt(Instant value) { this.approvedAt = value; }
    public void setApprovedBy(String value) { this.approvedBy = value; }
    public void setReviewNotes(String value) { this.reviewNotes = value; }

    public void setSlug(String slug) { this.slug = slug; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public void setTenantTag(String tenantTag) { this.tenantTag = tenantTag; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setDescription(String description) { this.description = description; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public void setCodEnabled(boolean codEnabled) { this.codEnabled = codEnabled; }
    public void setOnlinePaymentEnabled(boolean onlinePaymentEnabled) { this.onlinePaymentEnabled = onlinePaymentEnabled; }
    public void setAdvancePaymentPercent(int value) { this.advancePaymentPercent = value; }
    public void setDeliveryFee(BigDecimal value) { this.deliveryFee = value; }
    public void setMinimumOrderAmount(BigDecimal value) { this.minimumOrderAmount = value; }
    public void setStatus(BusinessStatus status) { this.status = status; }
    public void setName(String name) { this.name = name; }
}
