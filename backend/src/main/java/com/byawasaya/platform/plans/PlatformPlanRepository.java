package com.byawasaya.platform.plans;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface PlatformPlanRepository extends JpaRepository<PlatformPlan,UUID>{Optional<PlatformPlan> findByCodeIgnoreCase(String code);List<PlatformPlan> findAllByOrderByMonthlyPriceNprAsc();List<PlatformPlan> findByPublishedTrueOrderByMonthlyPriceNprAsc();}
