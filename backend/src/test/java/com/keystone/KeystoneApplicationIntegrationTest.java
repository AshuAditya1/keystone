package com.keystone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class KeystoneApplicationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flywayMigratesAndHibernateValidationAllowsStartup() {
    String metadataValue =
        jdbcTemplate.queryForObject(
            "SELECT metadata_value FROM keystone_schema_metadata WHERE metadata_key = ?",
            String.class,
            "schema_baseline");

    assertThat(metadataValue).isEqualTo("day-1");
  }

  @Test
  void publicHealthAndOpenApiEndpointsAreAvailable() {
    ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
    ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);

    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(health.getBody()).contains("\"status\":\"UP\"");
    assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void allOtherRoutesRemainDeniedDuringDayOne() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/not-implemented", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
