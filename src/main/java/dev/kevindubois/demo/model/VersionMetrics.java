package dev.kevindubois.demo.model;

public record VersionMetrics(
    double stableSuccessRate,
    double canarySuccessRate,
    long stableRequestCount,
    long canaryRequestCount,
    long stablePodCount,
    long canaryPodCount
) {
    public static VersionMetrics unavailable() {
        return new VersionMetrics(0.0, 0.0, 0, 0, 0, 0);
    }
}

