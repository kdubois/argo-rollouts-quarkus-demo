package dev.kevindubois.demo.model;

import java.time.Instant;

public class DeploymentStatus {
    private String version;
    private String scenario;
    private double successRate;
    private long requestCount;
    private Instant timestamp;
    private String status;

    public DeploymentStatus() {
        this.timestamp = Instant.now();
    }

    public DeploymentStatus(String version, String scenario, double successRate, long requestCount, String status) {
        this.version = version;
        this.scenario = scenario;
        this.successRate = successRate;
        this.requestCount = requestCount;
        this.status = status;
        this.timestamp = Instant.now();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

// Made with Bob
