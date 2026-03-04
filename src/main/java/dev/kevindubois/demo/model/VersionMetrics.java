package dev.kevindubois.demo.model;

public record VersionMetrics(
    double stableSuccessRate,
    double canarySuccessRate,
    long stableRequestCount,
    long canaryRequestCount
) {
    public static VersionMetrics unavailable() {
        return new VersionMetrics(0.0, 0.0, 0, 0);
    }
}

