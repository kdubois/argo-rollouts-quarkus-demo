package dev.kevindubois.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the canary rollback caused by latency drift:
 * even with the latency drift flag enabled and the service running in
 * 'latency-drift' mode, request processing must not report any latency
 * beyond the regular happy-path latency (10-50ms).
 */
class NoLatencyDriftTest {

    @Test
    void latencyDriftModeDoesNotAddLatency() {
        DemoScenarioService service = new DemoScenarioService();
        service.scenarioMode = "latency-drift";
        service.latencyDriftEnabled = true;

        for (int i = 0; i < 100; i++) {
            DemoScenarioService.ScenarioResult result = service.processRequest();
            long latency = result.getLatencyMs();
            assertTrue(latency >= 10 && latency <= 50,
                    "latency drift still applied: " + latency + "ms");
        }
    }
}
