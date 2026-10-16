package com.byawasaya.platform.onboarding;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "tenant_applications")
public class TenantApplication {
    @Id @GeneratedValue private UUID id;
    @Column(name="business_id", nullable=false, unique=true) private UUID businessId;
    @Column(name="owner_account_id") private UUID ownerAccountId;
    @Column(name="draft_token_hash", length=64) private String draftTokenHash;
    @Column(name="citizenship_number", length=100) private String citizenshipNumber;
    @Column(name="business_type", nullable=false, length=50) private String businessType="RETAIL";
    @Column(name="pan_vat_number", length=100) private String panVatNumber;
    @Column(name="registration_number", length=120) private String registrationNumber;
    @Column(name="registration_authority", length=180) private String registrationAuthority;
    @Column(name="registered_address", length=500) private String registeredAddress;
    @Column(length=100) private String province;
    @Column(length=100) private String district;
    @Column(length=120) private String municipality;
    @Column(name="selected_plan_code", nullable=false, length=40) private String selectedPlanCode="STARTER";
    @Column(name="selected_plan_price_npr", precision=12, scale=2) private java.math.BigDecimal selectedPlanPriceNpr;
    @Column(name="selected_template_key", nullable=false, length=40) private String selectedTemplateKey="classic";
    @Column(name="promo_code", length=60) private String promoCode;
    @Column(name="application_status", nullable=false, length=40) private String applicationStatus="DRAFT";
    @Column(name="applicant_notes", length=2000) private String applicantNotes;
    @Column(name="reviewer_notes", length=2000) private String reviewerNotes;
    @Column(name="public_review_notes", length=2000) private String publicReviewNotes;
    @Column(name="reviewer_username", length=180) private String reviewerUsername;
    @Column(name="submitted_at") private Instant submittedAt;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;

    protected TenantApplication() {}
    public TenantApplication(UUID businessId, UUID ownerAccountId, String citizenshipNumber, String businessType,
            String panVatNumber, String registrationNumber, String registrationAuthority, String registeredAddress,
            String province, String district, String municipality, String selectedPlanCode, String selectedTemplateKey, String promoCode) {
        this.businessId=businessId; this.ownerAccountId=ownerAccountId; this.citizenshipNumber=trim(citizenshipNumber);
        this.businessType=businessType; this.panVatNumber=trim(panVatNumber); this.registrationNumber=trim(registrationNumber);
        this.registrationAuthority=trim(registrationAuthority); this.registeredAddress=trim(registeredAddress); this.province=trim(province);
        this.district=trim(district); this.municipality=trim(municipality); this.selectedPlanCode=selectedPlanCode;
        this.selectedTemplateKey=selectedTemplateKey; this.promoCode=trim(promoCode)==null?null:trim(promoCode).toUpperCase(java.util.Locale.ROOT); this.applicationStatus="DRAFT";
    }
    public void setDraftTokenHash(String value){if(!"DRAFT".equals(applicationStatus)||ownerAccountId!=null)throw new IllegalStateException("Only an unclaimed draft can receive a resume token.");draftTokenHash=value;}
    public void attachOwnerAccount(UUID value){if(!"DRAFT".equals(applicationStatus)||ownerAccountId!=null)throw new IllegalStateException("This application draft is already claimed.");ownerAccountId=value;}
    public void updateLead(String plan){if(!"DRAFT".equals(applicationStatus)||ownerAccountId!=null)throw new IllegalStateException("Only an unclaimed draft can be updated.");selectedPlanCode=plan;}
    @PrePersist void create(){Instant n=Instant.now(); if(createdAt==null)createdAt=n; updatedAt=n;}
    @PreUpdate void update(){updatedAt=Instant.now();}
    public void submit(){if(!"DRAFT".equals(applicationStatus)&&!"NEEDS_CORRECTION".equals(applicationStatus))throw new IllegalStateException("Application cannot be submitted in its current state."); applicationStatus="SUBMITTED"; submittedAt=Instant.now();}
    public void review(String status,String internalNotes,String publicNotes,String reviewer){if(!java.util.Set.of("UNDER_REVIEW","NEEDS_CORRECTION","APPROVED","REJECTED").contains(status))throw new IllegalArgumentException("Unsupported review status"); if("REJECTED".equals(status)&&(publicNotes==null||publicNotes.isBlank()))throw new IllegalArgumentException("A rejection reason is required."); applicationStatus=status; reviewerNotes=trim(internalNotes); publicReviewNotes=trim(publicNotes); reviewerUsername=reviewer; reviewedAt=Instant.now();}
    public void updateDraft(String citizenshipNumber,String businessType,String panVatNumber,String registrationNumber,String registrationAuthority,String registeredAddress,String province,String district,String municipality,String plan,String template,String promoCode,String notes){if(!"DRAFT".equals(applicationStatus)&&!"NEEDS_CORRECTION".equals(applicationStatus))throw new IllegalStateException("Only draft or correction-requested applications can be edited.");this.citizenshipNumber=trim(citizenshipNumber);this.businessType=businessType;this.panVatNumber=trim(panVatNumber);this.registrationNumber=trim(registrationNumber);this.registrationAuthority=trim(registrationAuthority);this.registeredAddress=trim(registeredAddress);this.province=trim(province);this.district=trim(district);this.municipality=trim(municipality);this.selectedPlanCode=plan;this.selectedTemplateKey=template;this.promoCode=trim(promoCode)==null?null:trim(promoCode).toUpperCase(java.util.Locale.ROOT);this.applicantNotes=trim(notes);}
    private static String trim(String s){return s==null||s.isBlank()?null:s.trim();}
    public UUID getId(){return id;} public UUID getBusinessId(){return businessId;} public UUID getOwnerAccountId(){return ownerAccountId;} public String getDraftTokenHash(){return draftTokenHash;}
    public String getCitizenshipNumber(){return citizenshipNumber;} public String getBusinessType(){return businessType;} public String getPanVatNumber(){return panVatNumber;}
    public String getRegistrationNumber(){return registrationNumber;} public String getRegistrationAuthority(){return registrationAuthority;} public String getRegisteredAddress(){return registeredAddress;}
    public String getProvince(){return province;} public String getDistrict(){return district;} public String getMunicipality(){return municipality;} public String getSelectedPlanCode(){return selectedPlanCode;} public java.math.BigDecimal getSelectedPlanPriceNpr(){return selectedPlanPriceNpr;} public void setSelectedPlanPriceNpr(java.math.BigDecimal value){if(value==null||value.signum()<0)throw new IllegalArgumentException("Invalid selected plan price.");selectedPlanPriceNpr=value;}
    public String getSelectedTemplateKey(){return selectedTemplateKey;} public String getPromoCode(){return promoCode;} public String getApplicationStatus(){return applicationStatus;} public String getApplicantNotes(){return applicantNotes;}
    public String getReviewerNotes(){return reviewerNotes;} public String getPublicReviewNotes(){return publicReviewNotes;} public String getReviewerUsername(){return reviewerUsername;}
    public Instant getSubmittedAt(){return submittedAt;} public Instant getReviewedAt(){return reviewedAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
