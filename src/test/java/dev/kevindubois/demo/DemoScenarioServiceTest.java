package dev.kevindubois.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the latency drift delay trajectory used by the v4.latency scenario.
 *
 * The design goal: no added delay during the grace period (an early canary analysis
 * passes), a linear ramp afterwards (a later analysis fails the p95 threshold),
 * and a hard cap so responses stay slow but successful.
 */
class DemoScenarioServiceTest {

    @Test
    void noDelayDuringGracePeriod() {
        assertEquals(0, DemoScenarioService.computeLatencyDriftDelayMs(0));
        assertEquals(0, DemoScenarioService.computeLatencyDriftDelayMs(10));
        assertEquals(0, DemoScenarioService.computeLatencyDriftDelayMs(DemoScenarioService.LATENCY_DRIFT_GRACE_SECONDS));
    }

    @Test
    void delayGrowsLinearlyAfterGracePeriod() {
        assertEquals(25, DemoScenarioService.computeLatencyDriftDelayMs(25));
        assertEquals(50, DemoScenarioService.computeLatencyDriftDelayMs(30));
        assertEquals(125, DemoScenarioService.computeLatencyDriftDelayMs(45));
        assertEquals(200, DemoScenarioService.computeLatencyDriftDelayMs(60));
    }

    @Test
    void delayIsCapped() {
        assertEquals(DemoScenarioService.LATENCY_DRIFT_MAX_DELAY_MS,
                DemoScenarioService.computeLatencyDriftDelayMs(70));
        assertEquals(DemoScenarioService.LATENCY_DRIFT_MAX_DELAY_MS,
                DemoScenarioService.computeLatencyDriftDelayMs(600));
    }

    @Test
    void delayNeverDecreases() {
        long previous = 0;
        for (long seconds = 0; seconds <= 120; seconds++) {
            long current = DemoScenarioService.computeLatencyDriftDelayMs(seconds);
            assertTrue(current >= previous, "delay decreased at " + seconds + "s");
            previous = current;
        }
    }

    @Test
    void capKeepsResponsesSlowButSuccessful() {
        // The cap plus the baseline latency (10-50ms) must stay well below
        // typical HTTP client timeouts so the load generator keeps producing traffic.
        assertTrue(DemoScenarioService.LATENCY_DRIFT_MAX_DELAY_MS + 50 < 1000);
    }
}
