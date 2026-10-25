package dev.kevindubois.demo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the v4.latency scenario wiring: with the latency drift flag enabled,
 * the application stays healthy and reports no errors - the regression is only
 * visible in the latency metrics.
 */
@QuarkusTest
@TestProfile(LatencyDriftScenarioTest.LatencyDriftProfile.class)
class LatencyDriftScenarioTest {

    @Inject
    DemoScenarioService scenarioService;

    @Test
    void latencyDriftFlagIsWired() {
        assertTrue(scenarioService.isLatencyDriftEnabled());
    }

    @Test
    void statusStaysHealthyWithLatencyDriftEnabled() {
        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("status", is("healthy"));
    }

    @Test
    void scenarioEndpointReportsLatencyDrift() {
        given()
                .when().get("/api/scenario")
                .then()
                .statusCode(200)
                .body("latencyDriftEnabled", is(true));
    }

    public static class LatencyDriftProfile implements io.quarkus.test.junit.QuarkusTestProfile {

        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                    "enable.latency.drift", "true",
                    "load.generator.enabled", "false");
        }
    }
}
