package com.rkk.orderprocessing.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Defines the disposable PostgreSQL service shared by Spring integration-test contexts.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17.6-alpine");

	/**
	 * Starts PostgreSQL and publishes JDBC and Flyway connection details to Spring Boot.
	 *
	 * @return a context-managed PostgreSQL container
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE);
	}

}
