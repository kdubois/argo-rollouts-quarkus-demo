package dev.kevindubois.demo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the null-pointer fix: with the null pointer bug flag enabled
 * (the configuration the v2.nullpointer canary image is built with),
 * the application must not throw NullPointerExceptions.
 *
 * /api/status must stay healthy and unknown users must be reported as 404
 * instead of causing a 500 (findUser returns null for ~20% of requests).
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

    @Test
    void userEndpointNeverReturnsServerError() {
        for (int i = 0; i < 50; i++) {
            int statusCode = given()
                    .when().get("/api/user/1")
                    .then()
                    .extract().statusCode();
            assertTrue(statusCode == 200 || statusCode == 404,
                    "unexpected status code " + statusCode);
        }
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
