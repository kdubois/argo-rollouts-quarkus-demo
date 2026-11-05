package dev.kevindubois.demo;

import io.quarkus.scheduler.Scheduled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The load generator must start producing traffic immediately when the application
 * starts. The first canary analysis fires at the 10% traffic step, right after the
 * pod becomes Ready, so a startup delay on the load generator leaves a window where
 * the canary has served 0 requests and its health cannot be verified.
 */
class LoadGeneratorServiceTest {

    @Test
    void loadGenerationStartsWithoutStartupDelay() throws Exception {
        Method generateLoad = LoadGeneratorService.class.getDeclaredMethod("generateLoad");
        Scheduled scheduled = generateLoad.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "generateLoad must be a @Scheduled method");
        assertEquals(0L, scheduled.delay(),
                "generateLoad must not delay its first run: the canary analysis at the 10% "
                        + "traffic step runs as soon as the pod is Ready and needs traffic to verify canary health");
    }
}
