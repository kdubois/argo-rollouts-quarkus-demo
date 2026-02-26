package dev.kevindubois;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class MyLivenessCheck implements HealthCheck {

    @ConfigProperty(name = "scenario.mode", defaultValue = "happy")
    String scenarioMode;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("liveness")
                .up()
                .withData("scenario", scenarioMode)
                .build();
    }
}