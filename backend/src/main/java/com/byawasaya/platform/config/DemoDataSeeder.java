package com.byawasaya.platform.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.byawasaya.platform.auth.UserAccount;
import com.byawasaya.platform.auth.UserAccountRepository;
import com.byawasaya.platform.business.Business;
import com.byawasaya.platform.business.BusinessPlan;
import com.byawasaya.platform.business.BusinessRepository;
import com.byawasaya.platform.catalog.PaymentPolicy;
import com.byawasaya.platform.catalog.Product;
import com.byawasaya.platform.catalog.ProductRepository;

@Configuration
public class DemoDataSeeder {
    @Bean ApplicationRunner demoDataRunner(
            @Value("${app.demo.seed:false}") boolean enabled,
            BusinessRepository businesses,
            UserAccountRepository accounts,
            ProductRepository products,
            PasswordEncoder encoder) {
        return args -> {
            if (!enabled || businesses.existsBySlugIgnoreCase("himalayan-goods")) return;
            Business store = new Business("Himalayan Goods", "Sanjana Rai", BusinessPlan.PLUS);
            store.setSlug("himalayan-goods");
            store.setOwnerEmail("demo@byawasaya.local");
            store.setPhone("9800000000");
            store.setDescription("Thoughtful everyday essentials from Nepali makers and small businesses.");
            store.setDeliveryFee(new BigDecimal("120.00"));
            store.setAdvancePaymentPercent(30);
            store = businesses.save(store);
            accounts.save(new UserAccount("demo@byawasaya.local", encoder.encode("DemoStore123!"), "STORE_OWNER", store.getId(), "9800000000"));
            List<Product> samples = List.of(
                new Product(store.getId(), "Handwoven Dhaka Tote", "handwoven-dhaka-tote", "HG-001", "Accessories", "A durable handwoven tote inspired by traditional Nepali Dhaka patterns.", new BigDecimal("1450.00"), new BigDecimal("1120.00"), 5, "", PaymentPolicy.COD_ALLOWED, 30),
                new Product(store.getId(), "Lokta Paper Notebook Set", "lokta-notebook-set", "HG-002", "Stationery", "Three handmade Lokta paper notebooks for notes, sketches and journals.", new BigDecimal("680.00"), new BigDecimal("520.00"), 5, "", PaymentPolicy.COD_ALLOWED, 30),
                new Product(store.getId(), "Himalayan Tea Gift Box", "himalayan-tea-gift-box", "HG-003", "Food & drink", "A curated gift box of locally sourced Himalayan teas.", new BigDecimal("1850.00"), new BigDecimal("1490.00"), 4, "", PaymentPolicy.ADVANCE_REQUIRED, 30),
                new Product(store.getId(), "Artisan Ceramic Mug", "artisan-ceramic-mug", "HG-004", "Home & living", "A hand-finished ceramic mug with a comfortable, everyday shape.", new BigDecimal("890.00"), new BigDecimal("690.00"), 6, "", PaymentPolicy.ONLINE_ONLY, 0),
                new Product(store.getId(), "Wool Blend Scarf", "wool-blend-scarf", "HG-005", "Clothing", "A warm lightweight scarf designed for cool Kathmandu mornings.", new BigDecimal("2200.00"), new BigDecimal("1750.00"), 4, "", PaymentPolicy.COD_ALLOWED, 30),
                new Product(store.getId(), "Bamboo Desk Organizer", "bamboo-desk-organizer", "HG-006", "Home & living", "A simple organizer made to keep a work desk calm and tidy.", new BigDecimal("1250.00"), new BigDecimal("980.00"), 5, "", PaymentPolicy.COD_ALLOWED, 30)
            );
            products.saveAll(samples);
        };
    }
}
