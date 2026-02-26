package dev.kevindubois.demo.model;

public record RolloutInfo(
    String name,
    String phase,
    Integer canaryWeight,
    Integer stableWeight,
    String message,
    AnalysisInfo analysis
) {
    public static RolloutInfo notFound() {
        return new RolloutInfo(
            "N/A",
            "NotFound",
            0,
            100,
            "No active rollout found",
            null
        );
    }
}

// Made with Bob
