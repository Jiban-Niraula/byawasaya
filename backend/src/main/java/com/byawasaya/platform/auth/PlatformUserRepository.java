package com.byawasaya.platform.auth;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface PlatformUserRepository extends JpaRepository<PlatformUser,UUID>{Optional<PlatformUser> findByUsernameIgnoreCase(String username);boolean existsByUsernameIgnoreCase(String username);List<PlatformUser> findAllByOrderByCreatedAtDesc();}
