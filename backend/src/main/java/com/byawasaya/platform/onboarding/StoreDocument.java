package com.byawasaya.platform.onboarding;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="store_documents")
public class StoreDocument {
 @Id @GeneratedValue private UUID id;
 @Column(name="application_id",nullable=false) private UUID applicationId;
 @Column(name="business_id",nullable=false) private UUID businessId;
 @Column(name="document_type",nullable=false,length=40) private String documentType;
 @Column(name="object_key",nullable=false,unique=true,length=500) private String objectKey;
 @Column(name="original_filename",nullable=false,length=255) private String originalFilename;
 @Column(name="content_type",nullable=false,length=120) private String contentType;
 @Column(name="size_bytes",nullable=false) private long sizeBytes;
 @Column(name="sha256_hex",nullable=false,length=64) private String sha256Hex;
 @Column(name="verification_status",nullable=false,length=30) private String verificationStatus="PENDING";
 @Column(name="reviewer_note",length=1000) private String reviewerNote;
 @Column(name="uploaded_at",nullable=false) private Instant uploadedAt=Instant.now();
 @Column(name="reviewed_at") private Instant reviewedAt;
 @Column(name="reviewed_by",length=180) private String reviewedBy;
 protected StoreDocument(){}
 public StoreDocument(UUID app,UUID business,String type,String key,String filename,String mime,long size,String hash){applicationId=app;businessId=business;documentType=type;objectKey=key;originalFilename=filename;contentType=mime;sizeBytes=size;sha256Hex=hash;}
 public UUID getId(){return id;} public UUID getApplicationId(){return applicationId;} public UUID getBusinessId(){return businessId;} public String getDocumentType(){return documentType;} public String getObjectKey(){return objectKey;} public String getOriginalFilename(){return originalFilename;} public String getContentType(){return contentType;} public long getSizeBytes(){return sizeBytes;} public String getSha256Hex(){return sha256Hex;} public String getVerificationStatus(){return verificationStatus;} public String getReviewerNote(){return reviewerNote;} public Instant getUploadedAt(){return uploadedAt;}
 public void review(String status,String note,String reviewer){verificationStatus=status;reviewerNote=note;reviewedBy=reviewer;reviewedAt=Instant.now();} public void supersede(){verificationStatus="SUPERSEDED";reviewerNote="Replaced by a newer upload.";reviewedAt=Instant.now();}
}
