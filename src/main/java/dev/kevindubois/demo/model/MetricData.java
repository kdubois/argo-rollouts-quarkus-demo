package dev.kevindubois.demo.model;

import java.time.Instant;

public class MetricData {
    private String metricName;
    private double value;
    private Instant timestamp;
    private String unit;

    public MetricData() {
        this.timestamp = Instant.now();
    }

    public MetricData(String metricName, double value, String unit) {
        this.metricName = metricName;
        this.value = value;
        this.unit = unit;
        this.timestamp = Instant.now();
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}

