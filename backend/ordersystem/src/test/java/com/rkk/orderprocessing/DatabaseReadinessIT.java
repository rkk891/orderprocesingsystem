package com.rkk.orderprocessing;

import static org.assertj.core.api.Assertions.assertThat;

import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Proves that the readiness probe follows database availability without exposing details. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.hikari.connection-timeout=1000")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseReadinessIT {

    @LocalServerPort
    private int port;

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Test
    void readinessTurnsUnavailableWhenPostgreSqlStops() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                .GET()
                .build();

        HttpResponse<String> healthy = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(healthy.statusCode()).isEqualTo(200);
        assertThat(healthy.body()).contains("\"status\":\"UP\"");

        postgresContainer.stop();

        HttpResponse<String> unavailable =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(unavailable.statusCode()).isEqualTo(503);
        assertThat(unavailable.body())
                .contains("\"status\":\"DOWN\"")
                .doesNotContain("jdbc:", "postgres", "password", "127.0.0.1");
    }
}
