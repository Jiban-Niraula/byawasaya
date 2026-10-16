package com.byawasaya.platform.onboarding;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantApplicationTest {
 @Test void applicationMustBeSubmittedBeforeApprovalCanBeRecorded(){
  TenantApplication app=new TenantApplication(UUID.randomUUID(),UUID.randomUUID(),"ABC123","RETAIL",null,"REG-1","OCR","Itahari","Koshi","Sunsari","Itahari","STARTER","classic",null);
  assertEquals("DRAFT",app.getApplicationStatus());
  app.submit();
  assertEquals("SUBMITTED",app.getApplicationStatus());
  app.review("UNDER_REVIEW","checking docs","We are reviewing your application","admin@example.com");
  assertEquals("UNDER_REVIEW",app.getApplicationStatus());
  assertThrows(IllegalStateException.class,app::submit);
 }
 @Test void rejectedApplicationRequiresApplicantFacingReason(){
  TenantApplication app=new TenantApplication(UUID.randomUUID(),UUID.randomUUID(),"ABC123","RETAIL",null,"REG-1","OCR","Itahari","Koshi","Sunsari","Itahari","STARTER","classic",null);
  app.submit();
  assertThrows(IllegalArgumentException.class,()->app.review("REJECTED","internal note"," ","admin@example.com"));
 }
 @Test void planAndPromoCodesAreNormalizedOnCreation(){
  TenantApplication app=new TenantApplication(UUID.randomUUID(),UUID.randomUUID(),"ABC123","RETAIL",null,"REG-1","OCR","Itahari","Koshi","Sunsari","Itahari","STARTER","classic"," welcome10 ");
  assertEquals("WELCOME10",app.getPromoCode());
 }
}
