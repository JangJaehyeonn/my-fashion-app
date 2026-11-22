package com.fashionapp.domain.combo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Builds the combo engine from the rules file. The location can point at another file
 * (e.g. {@code file:/etc/wearon/combo-rules.json}) to tune rules without a code change.
 */
@Configuration
public class ComboConfig {

    @Bean
    public ComboEngine comboEngine(ResourceLoader resourceLoader,
                                   @Value("${wearon.combo.rules-location:classpath:rules/combo-rules.json}")
                                   String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new ComboEngine(ComboRulesLoader.load(in));
        }
    }
}
