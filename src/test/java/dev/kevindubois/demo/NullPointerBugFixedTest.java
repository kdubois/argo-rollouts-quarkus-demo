package dev.kevindubois.demo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Verifies the fix for the canary rollback: with the null pointer bug flag
 * enabled (as in the v2.nullpointer scenario image), the status endpoint
 * stays healthy and no longer throws a NullPointerException from
 * MetricsResource.getStatus.
 */
@QuarkusTest
@TestProfile(NullPointerBugFixedTest.NullPointerBugProfile.class)
class NullPointerBugFixedTest {

    @Test
    void statusStaysHealthyWithNullPointerBugFlagEnabled() {
        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("status", is("healthy"));
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
