package com.byawasaya.platform.business;
import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.JdbcTemplate;
class TenantTableNameResolverTest {
 private final TenantTableNameResolver resolver=new TenantTableNameResolver(null);
 @Test void onlyGeneratedPrefixShapeIsAccepted(){assertEquals("t_a13f09bd",resolver.validate("t_a13f09bd"));assertThrows(IllegalStateException.class,()->resolver.validate("hs"));assertThrows(IllegalStateException.class,()->resolver.validate("t_a13f09bd;DROP_TABLE"));}
 @Test void onlyKnownSuffixesCanBeResolved(){assertEquals("t_a13f09bd_products",resolver.table("t_a13f09bd","products"));assertThrows(IllegalArgumentException.class,()->resolver.table("t_a13f09bd","users; DROP TABLE users"));}
}
