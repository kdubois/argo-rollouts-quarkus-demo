package dev.kevindubois.demo.model;

public record AnalysisInfo(
    String phase,
    String message,
    Boolean successful,
    String errorLog
) {
    public static AnalysisInfo notStarted() {
        return new AnalysisInfo(
            "Pending",
            "Analysis has not started yet",
            null,
            null
        );
    }
}
