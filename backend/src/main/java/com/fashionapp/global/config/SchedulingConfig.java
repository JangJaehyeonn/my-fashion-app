package com.fashionapp.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (FR-12 temporary photo cleanup, PurchaseTempPhotoCleaner). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
