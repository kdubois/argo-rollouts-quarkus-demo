package dev.kevindubois.demo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Verifies the null pointer bug fix: with the bug flag enabled, the status
 * endpoint must respond successfully instead of throwing a
 * NullPointerException from MetricsResource.getStatus.
 */
@QuarkusTest
@TestProfile(NullPointerBugScenarioTest.NullPointerBugProfile.class)
class NullPointerBugScenarioTest {

    @Test
    void statusDoesNotThrowNullPointerWhenBugFlagEnabled() {
        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("status", is("healthy"));
    }

    @Test
    void scenarioEndpointReportsNullPointerBugEnabled() {
        given()
                .when().get("/api/scenario")
                .then()
                .statusCode(200);
    }

    public static class NullPointerBugProfile implements io.quarkus.test.junit.QuarkusTestProfile {

        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                    "enable.null.pointer.bug", "true",
                    "load.generator.enabled", "false");
        }
    }
}
