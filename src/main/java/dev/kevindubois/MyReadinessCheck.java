package dev.kevindubois;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class MyReadinessCheck implements HealthCheck {

    @ConfigProperty(name = "scenario.mode", defaultValue = "happy")
    String scenarioMode;

    @Override
    public HealthCheckResponse call() {
        // In failure scenario, simulate intermittent readiness issues
        boolean isReady = !"failure".equals(scenarioMode) || Math.random() > 0.3;
        
        if (isReady) {
            return HealthCheckResponse.named("readiness")
                    .up()
                    .withData("scenario", scenarioMode)
                    .withData("ready", true)
                    .build();
        } else {
            return HealthCheckResponse.named("readiness")
                    .down()
                    .withData("scenario", scenarioMode)
                    .withData("ready", false)
                    .build();
        }
    }
}

// Made with Bob
