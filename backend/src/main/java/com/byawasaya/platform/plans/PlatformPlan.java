package com.byawasaya.platform.plans;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="platform_plans")
public class PlatformPlan {
 @Id @GeneratedValue private UUID id;
 @Column(nullable=false,unique=true,length=40) private String code;
 @Column(nullable=false,length=100) private String name;
 @Column(length=1000) private String description;
 @Column(name="monthly_price_npr",nullable=false,precision=12,scale=2) private BigDecimal monthlyPriceNpr;
 @Column(name="annual_price_npr",nullable=false,precision=12,scale=2) private BigDecimal annualPriceNpr;
 @Column(name="trial_days",nullable=false) private int trialDays=14;
 @Column(name="max_products",nullable=false) private int maxProducts=100;
 @Column(name="max_staff",nullable=false) private int maxStaff=2;
 @Column(nullable=false) private boolean published=true;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected PlatformPlan(){}
 public PlatformPlan(String code){this.code=code;}
 @PrePersist void create(){Instant n=Instant.now();if(createdAt==null)createdAt=n;updatedAt=n;} @PreUpdate void update(){updatedAt=Instant.now();}
 public void update(String name,String description,BigDecimal monthly,BigDecimal annual,int trialDays,int maxProducts,int maxStaff,boolean published){this.name=name;this.description=description;this.monthlyPriceNpr=monthly;this.annualPriceNpr=annual;this.trialDays=trialDays;this.maxProducts=maxProducts;this.maxStaff=maxStaff;this.published=published;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getDescription(){return description;} public BigDecimal getMonthlyPriceNpr(){return monthlyPriceNpr;} public BigDecimal getAnnualPriceNpr(){return annualPriceNpr;} public int getTrialDays(){return trialDays;} public int getMaxProducts(){return maxProducts;} public int getMaxStaff(){return maxStaff;} public boolean isPublished(){return published;} public Instant getCreatedAt(){return createdAt;}
}
