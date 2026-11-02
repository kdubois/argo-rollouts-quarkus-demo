package dev.kevindubois.demo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the canary weight derivation from the live rollout status. The weight
 * comes only from what the Argo Rollouts controller reports; when it reports
 * nothing, no percentage is invented.
 */
class RolloutStatusResourceTest {

    @Test
    void liveCanaryWeightIsUsed() {
        var status = Map.of(
                "phase", "Progressing",
                "canary", Map.of("weights", Map.of(
                        "canary", Map.of("weight", 30),
                        "stable", Map.of("weight", 70))));
        assertEquals(30, RolloutStatusResource.canaryWeightFrom(status));
    }

    @Test
    void directNumberWeightIsUsed() {
        var status = Map.of(
                "phase", "Progressing",
                "canary", Map.of("weights", Map.of("canary", 60)));
        assertEquals(60, RolloutStatusResource.canaryWeightFrom(status));
    }

    @Test
    void degradedRolloutHasNoCanaryTraffic() {
        var status = Map.of(
                "phase", "Degraded",
                "canary", Map.of("weights", Map.of("canary", Map.of("weight", 60))));
        assertEquals(0, RolloutStatusResource.canaryWeightFrom(status));
    }

    @Test
    void abortedRolloutHasNoCanaryTraffic() {
        assertEquals(0, RolloutStatusResource.canaryWeightFrom(Map.of("phase", "Aborted")));
    }

    @Test
    void fullyPromotedHealthyRolloutHasNoCanaryTraffic() {
        var status = Map.<String, Object>of(
                "phase", "Healthy",
                "stableRS", "abc123",
                "canaryRS", "abc123");
        assertEquals(0, RolloutStatusResource.canaryWeightFrom(status));
    }

    @Test
    void healthyRolloutStillMigratingKeepsLiveWeight() {
        var status = Map.of(
                "phase", "Healthy",
                "stableRS", "abc123",
                "canaryRS", "def456",
                "canary", Map.of("weights", Map.of("canary", Map.of("weight", 100))));
        assertEquals(100, RolloutStatusResource.canaryWeightFrom(status));
    }

    @Test
    void missingWeightIsNullEvenWithStepIndex() {
        // The controller has not published weights yet; the step index alone
        // must not produce a percentage.
        var status = Map.<String, Object>of(
                "phase", "Progressing",
                "currentStepIndex", 4);
        assertNull(RolloutStatusResource.canaryWeightFrom(status));
    }

    @Test
    void emptyStatusIsNull() {
        assertNull(RolloutStatusResource.canaryWeightFrom(Map.of()));
        assertNull(RolloutStatusResource.canaryWeightFrom(Map.of("phase", "Progressing")));
    }
}
