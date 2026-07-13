package com.rkk.orderprocessing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling unless the process explicitly disables order jobs.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "orders.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
