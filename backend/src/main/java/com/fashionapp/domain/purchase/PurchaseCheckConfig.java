package com.fashionapp.domain.purchase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Verdict thresholds from configuration (application.yml wearon.purchase-check.*). */
@Configuration
public class PurchaseCheckConfig {

    @Bean
    public PurchaseCheckRules purchaseCheckRules(
            @Value("${wearon.purchase-check.similar-threshold:0.85}") double similarThreshold,
            @Value("${wearon.purchase-check.buy-min-plus:5}") int buyMinPlus) {
        return new PurchaseCheckRules(similarThreshold, buyMinPlus);
    }
}
