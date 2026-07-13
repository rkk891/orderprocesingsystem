package com.rkk.orderprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the order-processing service and defines its component-scan root.
 */
@SpringBootApplication
public class OrderProcessingApplication {

	/**
	 * Launches the Spring Boot application.
	 *
	 * @param args command-line arguments forwarded to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(OrderProcessingApplication.class, args);
	}

}
