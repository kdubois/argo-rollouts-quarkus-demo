package dev.kevindubois.demo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.oneOf;

/**
 * Verifies the fix for the v2.nullpointer scenario: even with the null pointer
 * bug flag enabled, the application stays healthy and unknown users are handled
 * with a 404 instead of a NullPointerException.
 */
@QuarkusTest
@TestProfile(NullPointerScenarioTest.NullPointerProfile.class)
class NullPointerScenarioTest {

    @Test
    void statusStaysHealthyWithNullPointerBugEnabled() {
        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("status", is("healthy"));
    }

    @Test
    void userLookupHandlesUnknownUsersWithoutErrors() {
        // findUser returns null for ~20% of requests; those must map to a 404,
        // never to a NullPointerException (which would surface as a 500).
        for (int i = 0; i < 50; i++) {
            given()
                    .when().get("/api/user/1")
                    .then()
                    .statusCode(oneOf(200, 404));
        }
    }

    public static class NullPointerProfile implements io.quarkus.test.junit.QuarkusTestProfile {

        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                    "enable.null.pointer.bug", "true",
                    "load.generator.enabled", "false");
        }
    }
}
