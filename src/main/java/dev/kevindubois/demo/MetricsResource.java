package dev.kevindubois.demo;

import dev.kevindubois.demo.model.DeploymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Path("/api")
@ApplicationScoped
public class MetricsResource {

    private static final Logger LOG = Logger.getLogger(MetricsResource.class);

    @Inject
    MeterRegistry registry;

    @ConfigProperty(name = "scenario.mode", defaultValue = "happy")
    String scenarioMode;

    @ConfigProperty(name = "app.version", defaultValue = "1.0.0")
    String appVersion;

    private Counter requestCounter;
    private Counter successCounter;
    private Counter errorCounter;
    private Timer requestTimer;
    private AtomicLong totalRequests = new AtomicLong(0);
    private AtomicLong successfulRequests = new AtomicLong(0);

    @PostConstruct
    void init() {
        // Register custom metrics
        requestCounter = Counter.builder("http_requests_total")
                .description("Total HTTP requests")
                .tag("app", "demo")
                .tag("version", appVersion)
                .register(registry);

        successCounter = Counter.builder("http_requests_success_total")
                .description("Successful HTTP requests")
                .tag("app", "demo")
                .register(registry);

        errorCounter = Counter.builder("http_requests_error_total")
                .description("Failed HTTP requests")
                .tag("app", "demo")
                .register(registry);

        requestTimer = Timer.builder("http_request_duration_seconds")
                .description("HTTP request duration")
                .tag("app", "demo")
                .register(registry);

        // Success rate gauge
        Gauge.builder("http_requests_success_rate", this, MetricsResource::calculateSuccessRate)
                .description("HTTP request success rate")
                .tag("app", "demo")
                .register(registry);

        // App version info metric
        Gauge.builder("app_version_info", () -> 1.0)
                .description("Application version information")
                .tag("version", appVersion)
                .tag("scenario", scenarioMode)
                .register(registry);
    }

    private double calculateSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) return 1.0;
        return (double) successfulRequests.get() / total;
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public DeploymentStatus getStatus() {
        // Simulate request processing
        recordRequest();

        double successRate = calculateSuccessRate();
        String status = "healthy";
        
        if ("failure".equals(scenarioMode)) {
            status = successRate < 0.8 ? "degraded" : "recovering";
        }

        return new DeploymentStatus(
                appVersion,
                scenarioMode,
                successRate * 100,
                totalRequests.get(),
                status
        );
    }

    @GET
    @Path("/scenario")
    @Produces(MediaType.APPLICATION_JSON)
    public ScenarioInfo getScenarioInfo() {
        return new ScenarioInfo(scenarioMode, appVersion);
    }

    public static class ScenarioInfo {
        public String scenario;
        public String version;

        public ScenarioInfo(String scenario, String version) {
            this.scenario = scenario;
            this.version = version;
        }
    }

    @POST
    @Path("/scenario")
    @Produces(MediaType.APPLICATION_JSON)
    public DeploymentStatus switchScenario(String newScenario) {
        // In a real app, this would update the config
        // For demo purposes, we'll just return current status
        // The scenario is controlled via environment variable
        return getStatus();
    }

    private void recordRequest() {
        requestCounter.increment();
        totalRequests.incrementAndGet();

        // Simulate request processing time and success/failure
        boolean isSuccess;
        long latencyMs;

        if ("failure".equals(scenarioMode)) {
            // Failure scenario: 70% success rate, higher latency
            isSuccess = Math.random() > 0.3;
            latencyMs = (long) (100 + Math.random() * 400); // 100-500ms
            
            // Log errors so AI agent can detect them - make them very obvious
            if (!isSuccess) {
                LOG.error("CRITICAL ERROR: Request failed with database connection timeout after " + latencyMs + "ms",
                    new RuntimeException("Database connection pool exhausted - unable to acquire connection"));
            } else if (latencyMs > 300) {
                LOG.error("PERFORMANCE DEGRADATION: High latency detected: " + latencyMs + "ms - service is struggling");
            }
            
            // Log periodic summary of failures
            if (totalRequests.get() % 10 == 0) {
                double currentSuccessRate = calculateSuccessRate();
                if (currentSuccessRate < 0.8) {
                    LOG.error("ALERT: Success rate dropped to " + String.format("%.1f%%", currentSuccessRate * 100) +
                        " - CANARY DEPLOYMENT IS FAILING");
                }
            }
        } else {
            // Happy scenario: 99% success rate, low latency
            isSuccess = Math.random() > 0.01;
            latencyMs = (long) (10 + Math.random() * 40); // 10-50ms
            
            if (!isSuccess) {
                LOG.warn("Occasional request failure: " + latencyMs + "ms");
            }
        }

        requestTimer.record(Duration.ofMillis(latencyMs));

        if (isSuccess) {
            successCounter.increment();
            successfulRequests.incrementAndGet();
        } else {
            errorCounter.increment();
        }
    }
}

// Made with Bob
