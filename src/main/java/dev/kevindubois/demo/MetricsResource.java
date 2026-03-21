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

/**
 * REST resource that exposes metrics and status endpoints for the demo application.
 * This resource tracks HTTP request metrics and delegates scenario simulation to DemoScenarioService.
 */
@Path("/api")
@ApplicationScoped
public class MetricsResource {

    private static final Logger LOG = Logger.getLogger(MetricsResource.class);

    @Inject
    MeterRegistry registry;

    @Inject
    DemoScenarioService scenarioService;

    @ConfigProperty(name = "app.version", defaultValue = "1.0.0")
    String appVersion;

    @ConfigProperty(name = "enable.null.pointer.bug", defaultValue = "false")
    boolean enableNullPointerBug;

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
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
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
                .tag("scenario", scenarioService.getScenarioMode())
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
        recordRequest();

        double successRate = calculateSuccessRate();
        String status = "healthy";
        
        String currentScenario = scenarioService.getScenarioMode();
        if ("failure".equals(currentScenario)) {
            status = successRate < 0.8 ? "degraded" : "recovering";
        }

        // Add bug scenario indicators to status
        if (scenarioService.isMemoryLeakEnabled() ||
            scenarioService.isConnectionLeakEnabled() ||
            scenarioService.isCpuSpikeEnabled()) {
            status = "buggy-" + status;
        }

        // BUG: Null pointer exception - missing null check (only when flag is enabled)
        String versionUpper = appVersion.toUpperCase();
        int length = versionUpper.length();
        
        // Intentionally dereference null to cause NPE (only for scenario 2)
        if (enableNullPointerBug) {
            try {
                String nullString = null;
                length = nullString.length();  // NullPointerException here!
            } catch (NullPointerException e) {
                // Log the full stack trace so the AI agent can identify the file and line
                LOG.error("NullPointerException in getStatus method", e);
                throw e;  // Re-throw to maintain the error behavior
            }
        }

        return new DeploymentStatus(
                appVersion,
                currentScenario,
                successRate * 100,
                totalRequests.get(),
                status
        );
    }

    @GET
    @Path("/scenario")
    @Produces(MediaType.APPLICATION_JSON)
    public ScenarioInfo getScenarioInfo() {
        return new ScenarioInfo(
            scenarioService.getScenarioMode(),
            appVersion,
            scenarioService.isMemoryLeakEnabled(),
            scenarioService.isConnectionLeakEnabled(),
            scenarioService.isCpuSpikeEnabled()
        );
    }

    public static class ScenarioInfo {
        public String scenario;
        public String version;
        public boolean memoryLeakEnabled;
        public boolean connectionLeakEnabled;
        public boolean cpuSpikeEnabled;

        public ScenarioInfo(String scenario, String version, boolean memoryLeakEnabled,
                           boolean connectionLeakEnabled, boolean cpuSpikeEnabled) {
            this.scenario = scenario;
            this.version = version;
            this.memoryLeakEnabled = memoryLeakEnabled;
            this.connectionLeakEnabled = connectionLeakEnabled;
            this.cpuSpikeEnabled = cpuSpikeEnabled;
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

        // Delegate scenario processing to DemoScenarioService
        DemoScenarioService.ScenarioResult result = scenarioService.processRequest();
        
        boolean isSuccess = result.isSuccess();
        long latencyMs = result.getLatencyMs();

        // Record metrics
        requestTimer.record(Duration.ofMillis(latencyMs));

        if (isSuccess) {
            successCounter.increment();
            successfulRequests.incrementAndGet();
        } else {
            errorCounter.increment();
        }

        // Log alerts for failure scenario
        if ("failure".equals(scenarioService.getScenarioMode()) && totalRequests.get() % 10 == 0) {
            double currentSuccessRate = calculateSuccessRate();
            if (currentSuccessRate < 0.8) {
                LOG.error("ALERT: Success rate dropped to " + String.format("%.1f%%", currentSuccessRate * 100) +
                    " - CANARY DEPLOYMENT IS FAILING");
            }
        }
    }
}
