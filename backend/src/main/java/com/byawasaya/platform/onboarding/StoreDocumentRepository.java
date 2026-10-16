package com.byawasaya.platform.onboarding;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface StoreDocumentRepository extends JpaRepository<StoreDocument,UUID>{List<StoreDocument> findByApplicationIdOrderByUploadedAtAsc(UUID applicationId);boolean existsByApplicationIdAndDocumentType(UUID applicationId,String documentType);List<StoreDocument> findByApplicationIdAndDocumentType(UUID applicationId,String documentType);}
