package com.byawasaya.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    ObjectMapper legacySecurityObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
