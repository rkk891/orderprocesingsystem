package com.rkk.orderprocessing.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application-wide clock so production and tests share one time boundary.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

	/**
	 * Supplies an immutable UTC clock for IDs, timestamps, and scheduled mutations.
	 *
	 * @return the system UTC clock
	 */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
