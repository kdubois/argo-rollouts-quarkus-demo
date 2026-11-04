package dev.kevindubois.demo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyString;

/**
 * Tests the promote/abort proxy endpoints. The kubernetes-agent is pointed
 * at an unreachable address so the endpoints exercise their error path:
 * a gate action that cannot be forwarded must surface a 502 with the
 * failure reason instead of a silent success.
 */
@QuarkusTest
@TestProfile(RolloutControlResourceTest.UnreachableAgentProfile.class)
class RolloutControlResourceTest {

    @Test
    void promoteReportsFailureWhenAgentIsUnreachable() {
        given()
                .when().post("/api/rollout/promote")
                .then()
                .statusCode(502)
                .body("status", is("error"))
                .body("action", is("promote"))
                .body("error", not(emptyString()));
    }

    @Test
    void abortReportsFailureWhenAgentIsUnreachable() {
        given()
                .when().post("/api/rollout/abort")
                .then()
                .statusCode(502)
                .body("status", is("error"))
                .body("action", is("abort"))
                .body("error", not(emptyString()));
    }

    public static class UnreachableAgentProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rest-client.kubernetes-agent.url", "http://localhost:9");
        }
    }
}
