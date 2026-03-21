package dev.kevindubois.demo;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Built-in load generator that continuously generates traffic to the application's own endpoints.
 * This enables fast and accurate analysis during canary deployments by ensuring consistent traffic.
 * 
 * The load generator:
 * - Starts automatically when the application starts (after a configurable delay)
 * - Generates configurable requests per second (default: 50 req/sec)
 * - Hits the /api/status endpoint
 * - Runs asynchronously without blocking the main application
 * - Can be disabled via configuration for local development
 */
@Startup
@ApplicationScoped
public class LoadGeneratorService {
    
    private static final Logger LOG = Logger.getLogger(LoadGeneratorService.class);
    
    @Inject
    MetricsResource metricsResource;
    
    @Inject
    UserResource userResource;
    
    @ConfigProperty(name = "load.generator.enabled", defaultValue = "true")
    boolean enabled;?
r?
r?
r?   }n}
n