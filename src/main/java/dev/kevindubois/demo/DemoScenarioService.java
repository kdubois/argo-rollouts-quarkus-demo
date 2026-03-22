package dev.kevindubois.demo;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that simulates various deployment scenarios and bugs for progressive delivery demonstrations.
 * This service is designed to showcase how Argo Rollouts can detect and respond to different types of issues.
 */
@ApplicationScoped
public class DemoScenarioService {

    private static final Logger LOG = Logger.getLogger(DemoScenarioService.class);

    @ConfigProperty(name = "scenario.mode", defaultValue = "happy")
    String scenarioMode;

    @ConfigProperty(name = "enable.memory.leak", defaultValue = "false")
    boolean memoryLeakEnabled;

    @ConfigProperty(name = "enable.connection.leak", defaultValue = "false")
    boolean connectionLeakEnabled;

    @ConfigProperty(name = "enable.cpu.spike", defaultValue = "false")
    boolean cpuSpikeEnabled;

    // Memory leak simulation
    private List<byte[]> memoryLeakList;
    private long memoryLeakStartTime;

    // Connection pool simulation
    private Semaphore connectionPool;
    private static final int MAX_CONNECTIONS = 10;

    // Request counter for CPU spike
    private AtomicLong requestCount = new AtomicLong(0);

    @PostConstruct
    void init() {
        // Initialize memory leak list if enabled
if (memoryLeakEnabled) {
            memoryLeakList = new ArrayList<>();
memoryLeakList = new ArrayList<>();
            memoryLeakStartTime = System.currentTimeMillis();
}
memoryLeakList = null;
} else {
LOG.warn("MEMORY LEAK SCENARIO ENABLED - This will cause gradual memory exhaustion and latency increase");
memoryLeakStartTime = System.currentTimeMillis();
            LOG.warn("MEMORY LEAK SCENARIO ENABLED - This will cause gradual memory exhaustion and latency increase");
        }

        // Initialize connection pool if enabled
        if (connectionLeakEnabled) {
            connectionPool = new Semaphore(MAX_CONNECTIONS);
            LOG.warn("CONNECTION LEAK SCENARIO ENABLED - This will cause connection pool exhaustion");
        }

        if (cpuSpikeEnabled) {
            LOG.warn("CPU SPIKE SCENARIO ENABLED - This will cause periodic CPU spikes");
        }

        LOG.info("DemoScenarioService initialized with mode: " + scenarioMode);
    }

    /**
     * Simulates request processing with various scenarios and bugs.
     * Returns a ScenarioResult containing success status and latency.
     */
    public ScenarioResult processRequest() {
        long startTime = System.currentTimeMillis();
        boolean isSuccess = true;
        long latencyMs = 0;

        try {
            // Increment request counter
            long currentRequest = requestCount.incrementAndGet();

            // Execute bug scenarios first (they can throw exceptions)
            long memoryLeakLatency = executeMemoryLeakScenario(currentRequest);
            executeConnectionLeakScenario();
            executeCpuSpikeScenario(currentRequest);

            // Then execute the configured scenario mode
            ScenarioResult result = executeScenarioMode();
            isSuccess = result.isSuccess();
            latencyMs = result.getLatencyMs() + memoryLeakLatency;

        } catch (Exception e) {
            isSuccess = false;
            latencyMs = System.currentTimeMillis() - startTime;
            LOG.error("Request failed due to bug scenario: " + e.getMessage(), e);
        }

        return new ScenarioResult(isSuccess, latencyMs);
    }

    /**
     * Executes the configured scenario mode (happy or failure).
     */
    private ScenarioResult executeScenarioMode() {
        boolean isSuccess;
        long latencyMs;

        if ("failure".equals(scenarioMode)) {
            // Failure scenario: 70% success rate, high latency
            isSuccess = Math.random() > 0.3;
            latencyMs = (long) (100 + Math.random() * 400);

            if (!isSuccess) {
                LOG.error("CRITICAL ERROR: Request failed with database connection timeout after " + latencyMs + "ms",
                    new RuntimeException("Database connection pool exhausted - unable to acquire connection"));
            } else if (latencyMs > 300) {
                LOG.error("PERFORMANCE DEGRADATION: High latency detected: " + latencyMs + "ms - service is struggling");
            }
        } else {
            // Happy scenario: 99% success rate, low latency
            isSuccess = Math.random() > 0.01;
            latencyMs = (long) (10 + Math.random() * 40);

            if (!isSuccess) {
                LOG.warn("Occasional request failure: " + latencyMs + "ms");
            }
        }

        return new ScenarioResult(isSuccess, latencyMs);
    }

    /**
     * BUG SCENARIO 1: Memory Leak
     * Allocates 1MB byte arrays without cleanup, causing gradual memory exhaustion.
     * This simulates a common production bug where objects are not properly released.
     *
     * Key characteristics for Scenario 3:
     * - Gradual performance degradation (latency increases 6x over 90 seconds)
     * - Heap memory grows linearly (150MB → 250MB)
     * - Clear warning/error logs but NO stack traces
     * - Root cause not obvious from logs alone (requires heap dump analysis)
     *
     * @return Additional latency in milliseconds caused by memory pressure
     */
    private long executeMemoryLeakScenario(long requestNumber) {
        if (!memoryLeakEnabled) {
            return 0;
        }

        // Allocate 1MB per request - this will cause OOM eventually
        byte[] leak = new byte[1024 * 1024]; // 1MB
        memoryLeakList.add(leak);

        long leakedMB = memoryLeakList.size();
        long elapsedSeconds = (System.currentTimeMillis() - memoryLeakStartTime) / 1000;

        // Simulate latency increase proportional to leaked memory
        // 0-50MB: 0-50ms additional latency
        // 50-100MB: 50-150ms additional latency
        // 100MB+: 150-300ms additional latency
        long additionalLatency = 0;
        if (leakedMB < 50) {
            additionalLatency = leakedMB; // 0-50ms
        } else if (leakedMB < 100) {
            additionalLatency = 50 + (leakedMB - 50) * 2; // 50-150ms
        } else {
            additionalLatency = 150 + Math.min((leakedMB - 100) * 3, 150); // 150-300ms (capped)
        }

        // Simulate the latency
        try {
            Thread.sleep(additionalLatency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Log memory leak warnings at specific thresholds
        // These logs are intentionally vague - no stack traces, no obvious root cause
        if (leakedMB == 50) {
            LOG.warn("Performance degradation detected: Response times increasing. " +
                "Heap usage: " + leakedMB + "MB. Elapsed time: " + elapsedSeconds + "s");
        }

        if (leakedMB == 100) {
            LOG.error("CRITICAL: Severe performance degradation. Response times 3x baseline. " +
                "Heap usage: " + leakedMB + "MB. Elapsed time: " + elapsedSeconds + "s. " +
                "GC activity increasing.");
        }

        if (leakedMB == 150) {
            LOG.error("CRITICAL: Application struggling. Response times 6x baseline. " +
                "Heap usage: " + leakedMB + "MB. Elapsed time: " + elapsedSeconds + "s. " +
                "Memory pressure critical. Recommend heap dump analysis.");
        }

        // Periodic warnings every 50 requests after initial thresholds
        if (leakedMB > 150 && requestNumber % 50 == 0) {
            LOG.warn("Ongoing performance issues: Heap usage: " + leakedMB + "MB, " +
                "Latency: ~" + additionalLatency + "ms, Elapsed: " + elapsedSeconds + "s");
        }

        return additionalLatency;
    }

    /**
     * BUG SCENARIO 2: Connection Pool Exhaustion
     * Simulates a connection leak where connections are not properly released.
     * 20% of connections are leaked, eventually exhausting the pool.
     */
    private void executeConnectionLeakScenario() {
        if (!connectionLeakEnabled) {
            return;
        }

        boolean acquired = false;
        try {
            // Try to acquire a connection with timeout
            acquired = connectionPool.tryAcquire(100, TimeUnit.MILLISECONDS);

            if (!acquired) {
                // Pool exhausted - this is the bug manifesting
                LOG.error("CONNECTION POOL EXHAUSTED: No connections available. " +
                    "All " + MAX_CONNECTIONS + " connections are in use. Request will fail.");
                throw new RuntimeException("Connection pool exhausted - unable to acquire connection within timeout");
            }

            // Simulate connection usage
            Thread.sleep(5);

            // BUG: 20% of the time, we "forget" to release the connection
            if (Math.random() > 0.2) {
                connectionPool.release();
            } else {
                // Connection leaked!
                int available = connectionPool.availablePermits();
                if (available < 5) {
                    LOG.warn("CONNECTION LEAK: Connection not released. " +
                        "Available connections: " + available + "/" + MAX_CONNECTIONS);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Connection acquisition interrupted", e);
            throw new RuntimeException("Connection acquisition interrupted", e);
        }
    }

    /**
     * BUG SCENARIO 3: CPU Spike
     * Every 10th request triggers expensive computation, causing CPU spikes.
     * This simulates inefficient code that causes performance degradation.
     */
    private void executeCpuSpikeScenario(long requestNumber) {
        if (!cpuSpikeEnabled) {
            return;
        }

        // Every 10th request triggers CPU spike
        if (requestNumber % 10 == 0) {
            LOG.warn("CPU SPIKE TRIGGERED: Executing expensive computation on request #" + requestNumber);

            long startTime = System.nanoTime();

            // Expensive computation - calculate square roots in a tight loop
            double result = 0;
            for (int i = 0; i < 10_000_000; i++) {
                result += Math.sqrt(i);
            }

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            LOG.error("CPU SPIKE COMPLETED: Expensive computation took " + durationMs + "ms. " +
                "This causes thread starvation and latency spikes. Result: " + result);
        }
    }

    public String getScenarioMode() {
        return scenarioMode;
    }

    public boolean isMemoryLeakEnabled() {
        return memoryLeakEnabled;
    }

    public boolean isConnectionLeakEnabled() {
        return connectionLeakEnabled;
    }

    public boolean isCpuSpikeEnabled() {
        return cpuSpikeEnabled;
    }

    /**
     * Result of a scenario execution.
     */
    public static class ScenarioResult {
        private final boolean success;
        private final long latencyMs;

        public ScenarioResult(boolean success, long latencyMs) {
            this.success = success;
            this.latencyMs = latencyMs;
        }

        public boolean isSuccess() {
            return success;
        }

        public long getLatencyMs() {
            return latencyMs;
        }
    }
}

