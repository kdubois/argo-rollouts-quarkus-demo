package dev.kevindubois.demo;

import dev.kevindubois.demo.model.AnalysisInfo;
import dev.kevindubois.demo.model.RolloutInfo;
import dev.kevindubois.demo.model.VersionMetrics;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

@Path("/api/rollout")
public class RolloutStatusResource {

    @Inject
    KubernetesClient kubernetesClient;

    @ConfigProperty(name = "rollout.name", defaultValue = "quarkus-demo")
    String rolloutName;

    @ConfigProperty(name = "rollout.namespace", defaultValue = "quarkus-demo")
    String rolloutNamespace;

    private static final ResourceDefinitionContext ROLLOUT_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("argoproj.io")
            .withVersion("v1alpha1")
            .withKind("Rollout")
            .withPlural("rollouts")
            .withNamespaced(true)
            .build();

    private static final ResourceDefinitionContext ANALYSIS_RUN_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("argoproj.io")
            .withVersion("v1alpha1")
            .withKind("AnalysisRun")
            .withPlural("analysisruns")
            .withNamespaced(true)
            .build();

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public RolloutInfo getRolloutStatus() {
        try {
            GenericKubernetesResource rollout = kubernetesClient
                    .genericKubernetesResources(ROLLOUT_CONTEXT)
                    .inNamespace(rolloutNamespace)
                    .withName(rolloutName)
                    .get();

            if (rollout == null) {
                Log.warn("Rollout not found: " + rolloutName + " in namespace " + rolloutNamespace);
                return RolloutInfo.notFound();
            }

            RolloutInfo info = extractRolloutInfo(rollout);
            Log.debugf("Rollout status: phase=%s, canaryWeight=%d, stableWeight=%d",
                    info.phase(), info.canaryWeight(), info.stableWeight());
            return info;

        } catch (Exception e) {
            Log.error("Error fetching rollout status", e);
            return new RolloutInfo(
                    rolloutName,
                    "Error",
                    0,
                    100,
                    "Error fetching rollout: " + e.getMessage(),
                    null,
                    null
            );
        }
    }

    private RolloutInfo extractRolloutInfo(GenericKubernetesResource rollout) {
        Map<String, Object> status = getStatus(rollout);

        String phase = getPhase(status);
        String message = getMessage(status);
        Integer canaryWeight = getCanaryWeight(status);
        Integer stableWeight = 100 - canaryWeight;
        Integer currentStepIndex = getCurrentStepIndex(status);

        AnalysisInfo analysisInfo = getAnalysisInfo(status);

        return new RolloutInfo(
                rolloutName,
                phase,
                canaryWeight,
                stableWeight,
                message,
                analysisInfo,
                currentStepIndex
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getStatus(GenericKubernetesResource resource) {
        Object status = resource.getAdditionalProperties().get("status");
        return status instanceof Map ? (Map<String, Object>) status : Map.of();
    }

    private String getPhase(Map<String, Object> status) {
        Object phase = status.get("phase");
        return phase != null ? phase.toString() : "Unknown";
    }

    private String getMessage(Map<String, Object> status) {
        Object message = status.get("message");
        return message != null ? message.toString() : "";
    }

    private Integer getCurrentStepIndex(Map<String, Object> status) {
        try {
            Object currentStepIndex = status.get("currentStepIndex");
            if (currentStepIndex instanceof Number) {
                return ((Number) currentStepIndex).intValue();
            }
        } catch (Exception e) {
            Log.debug("Could not extract current step index", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Integer getCanaryWeight(Map<String, Object> status) {
        try {
            String phase = getPhase(status);
            
            // If rollout is degraded or aborted, canary weight should be 0
            if ("Degraded".equals(phase) || "Aborted".equals(phase)) {
                return 0;
            }
            
            // Check if rollout is healthy and fully promoted
            Object stableRS = status.get("stableRS");
            Object canaryRS = status.get("canaryRS");
            if ("Healthy".equals(phase) && stableRS != null && canaryRS != null && stableRS.equals(canaryRS)) {
                return 100; // Fully rolled out and promoted
            }
            
            // Try to get actual weight from canary.weights.canary.weight first (most accurate)
            Object canaryStatus = status.get("canary");
            if (canaryStatus instanceof Map) {
                Map<String, Object> canaryMap = (Map<String, Object>) canaryStatus;
                Object weights = canaryMap.get("weights");
                if (weights instanceof Map) {
                    Map<String, Object> weightsMap = (Map<String, Object>) weights;
                    Object canary = weightsMap.get("canary");
                    if (canary instanceof Map) {
                        // Canary is an object with weight field
                        Map<String, Object> canaryWeightMap = (Map<String, Object>) canary;
                        Object weightObj = canaryWeightMap.get("weight");
                        if (weightObj instanceof Number) {
                            int weight = ((Number) weightObj).intValue();
                            return weight;
                        }
                    } else if (canary instanceof Number) {
                        // Fallback: canary might be a direct number in some configurations
                        int weight = ((Number) canary).intValue();
                        return weight;
                    }
                }
            }
            
            // Fallback: use current step index
            Object currentStepIndex = status.get("currentStepIndex");
            if (currentStepIndex instanceof Number) {
                int stepIndex = ((Number) currentStepIndex).intValue();
                // Map step index to weight based on rollout configuration
                // Steps: 0=10%, 1=pause, 2=10%, 3=pause, 4=60%, 5=pause, 6=100%
                int[] weights = {10, 10, 30, 30, 60, 60, 100};
                if (stepIndex >= 0 && stepIndex < weights.length) {
                    return weights[stepIndex];
                }
            }
        } catch (Exception e) {
            Log.debug("Could not extract canary weight", e);
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private AnalysisInfo getAnalysisInfo(Map<String, Object> status) {
        try {
            // List all AnalysisRuns for this rollout using label selector
            var analysisRuns = kubernetesClient
                    .genericKubernetesResources(ANALYSIS_RUN_CONTEXT)
                    .inNamespace(rolloutNamespace)
                    .withLabel("app", rolloutName)
                    .list()
                    .getItems();
            
            if (analysisRuns.isEmpty()) {
                Log.debugf("No AnalysisRuns found for rollout %s", rolloutName);
                return AnalysisInfo.notStarted();
            }
            
            // Find the most recent AnalysisRun
            GenericKubernetesResource latestAnalysisRun = null;
            String latestCreationTime = null;
            
            for (GenericKubernetesResource ar : analysisRuns) {
                Map<String, Object> metadata = (Map<String, Object>) ar.getAdditionalProperties().get("metadata");
                if (metadata != null) {
                    String creationTimestamp = (String) metadata.get("creationTimestamp");
                    if (latestCreationTime == null || (creationTimestamp != null && creationTimestamp.compareTo(latestCreationTime) > 0)) {
                        latestCreationTime = creationTimestamp;
                        latestAnalysisRun = ar;
                    }
                }
            }
            
            if (latestAnalysisRun == null) {
                return AnalysisInfo.notStarted();
            }

            Map<String, Object> analysisStatus = getStatus(latestAnalysisRun);
            String phase = getPhase(analysisStatus);
            String message = getMessage(analysisStatus);
            
            // Determine if successful based on phase
            Boolean successful = null;
            if ("Successful".equals(phase)) {
                successful = true;
            } else if ("Failed".equals(phase) || "Error".equals(phase) || "Degraded".equals(phase)) {
                successful = false;
            }
            
            // Extract error Logs from failed metrics
            String errorLog = extractErrorLog(analysisStatus);
            
            Map<String, Object> metadata = (Map<String, Object>) latestAnalysisRun.getAdditionalProperties().get("metadata");
            String analysisRunName = metadata != null ? (String) metadata.get("name") : "unknown";
            Log.debugf("Found AnalysisRun %s: phase=%s, message=%s, successful=%s", analysisRunName, phase, message, successful);
            return new AnalysisInfo(phase, message, successful, errorLog);

        } catch (Exception e) {
            Log.error("Error fetching analysis info", e);
            return new AnalysisInfo("Error", "Error fetching analysis: " + e.getMessage(), null, null);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractErrorLog(Map<String, Object> analysisStatus) {
        try {
            Object metricResultsObj = analysisStatus.get("metricResults");
            if (!(metricResultsObj instanceof List)) {
                return null;
            }
            
            List<Map<String, Object>> metricResults = (List<Map<String, Object>>) metricResultsObj;
            StringBuilder errorLog = new StringBuilder();
            
            for (Map<String, Object> metricResult : metricResults) {
                String phase = metricResult.get("phase") != null ? metricResult.get("phase").toString() : "";
                
                if ("Failed".equals(phase) || "Error".equals(phase)) {
                    String metricName = metricResult.get("name") != null ? metricResult.get("name").toString() : "unknown";
                    String metricMessage = metricResult.get("message") != null ? metricResult.get("message").toString() : "";
                    
                    if (errorLog.length() > 0) {
                        errorLog.append("\n\n");
                    }
                    
                    errorLog.append("Metric: ").append(metricName).append("\n");
                    errorLog.append("Status: ").append(phase).append("\n");
                    
                    if (!metricMessage.isEmpty()) {
                        errorLog.append("Message: ").append(metricMessage).append("\n");
                    }
                    
                    // Extract measurements if available
                    Object measurementsObj = metricResult.get("measurements");
                    if (measurementsObj instanceof List) {
                        List<Map<String, Object>> measurements = (List<Map<String, Object>>) measurementsObj;
                        if (!measurements.isEmpty()) {
                            errorLog.append("Measurements:\n");
                            for (Map<String, Object> measurement : measurements) {
                                String value = measurement.get("value") != null ? measurement.get("value").toString() : "N/A";
                                String measurementPhase = measurement.get("phase") != null ? measurement.get("phase").toString() : "";
                                String measurementMessage = measurement.get("message") != null ? measurement.get("message").toString() : "";
                                
                                errorLog.append("  - Value: ").append(value);
                                if (!measurementPhase.isEmpty()) {
                                    errorLog.append(", Phase: ").append(measurementPhase);
                                }
                                if (!measurementMessage.isEmpty()) {
                                    errorLog.append(", Message: ").append(measurementMessage);
                                }
                                errorLog.append("\n");
                            }
                        }
                    }
                }
            }
            
            return errorLog.length() > 0 ? errorLog.toString() : null;
        } catch (Exception e) {
            Log.debug("Could not extract error Log from analysis status", e);
            return null;
        }
    }

    @GET
    @Path("/scenarios")
    @Produces(MediaType.APPLICATION_JSON)
    public DeploymentScenarios getDeploymentScenarios() {
        try {
            String stableScenario = fetchScenarioFromPods("stable");
            String canaryScenario = fetchScenarioFromPods("canary");
            
            return new DeploymentScenarios(stableScenario, canaryScenario);
        } catch (Exception e) {
            Log.error("Error fetching deployment scenarios", e);
            return new DeploymentScenarios("unknown", "unknown");
        }
    }

    private String fetchScenarioFromPods(String roleLabel) {
        try {
            List<Pod> pods = kubernetesClient.pods()
                .inNamespace(rolloutNamespace)
                .withLabel("app", rolloutName)
                .withLabel("role", roleLabel)
                .list()
                .getItems();            
            
            if (pods.isEmpty()) {
                Log.debug("No pods found with role: " + roleLabel);
                return "none";
            }
            
            // Get the first pod and extract SCENARIO_MODE env var
            Pod pod = pods.get(0);
            Log.debug("Found pod: " + pod.getMetadata().getName() + " with role: " + roleLabel);
            
            if (pod.getSpec() != null &&
                pod.getSpec().getContainers() != null &&
                !pod.getSpec().getContainers().isEmpty()) {
                
                List<EnvVar> envVars = pod.getSpec().getContainers().get(0).getEnv();
                if (envVars != null) {
                    for (EnvVar envVar : envVars) {
                        if ("SCENARIO_MODE".equals(envVar.getName())) {
                            String value = envVar.getValue() != null ? envVar.getValue() : "unknown";
                            Log.debug("Found SCENARIO_MODE=" + value + " for role: " + roleLabel);
                            return value;
                        }
                    }
                }
            }
            
            Log.debug("SCENARIO_MODE not found in pod env vars for role: " + roleLabel);
            return "unknown";
        } catch (Exception e) {
            Log.error("Could not fetch scenario from pods with role " + roleLabel + ": " + e.getMessage(), e);
            return "unknown";
        }
    }

    public static class DeploymentScenarios {
        public String stable;
        public String canary;

        public DeploymentScenarios(String stable, String canary) {
            this.stable = stable;
            this.canary = canary;
        }
    }

    @GET
    @Path("/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public VersionMetrics getVersionMetrics() {
        Log.debug("=== getVersionMetrics called ===");
        try {
            Log.debug("Fetching stable metrics...");
            PodMetrics stableMetrics = fetchMetricsFromPods("stable");
            Log.debug("Stable metrics: successRate=" + stableMetrics.successRate + ", requests=" + stableMetrics.requestCount);
            
            Log.debug("Fetching canary metrics...");
            PodMetrics canaryMetrics = fetchMetricsFromPods("canary");
            Log.debug("Canary metrics: successRate=" + canaryMetrics.successRate + ", requests=" + canaryMetrics.requestCount);
            
            return new VersionMetrics(
                stableMetrics.successRate,
                canaryMetrics.successRate,
                stableMetrics.requestCount,
                canaryMetrics.requestCount
            );
        } catch (Exception e) {
            Log.error("Error fetching version metrics", e);
            return VersionMetrics.unavailable();
        }
    }

    private PodMetrics fetchMetricsFromPods(String roleLabel) {
        Log.debug("fetchMetricsFromPods called for role: " + roleLabel);
        try {
            List<Pod> pods = kubernetesClient.pods()
                .inNamespace(rolloutNamespace)
                .withLabel("app", rolloutName)
                .withLabel("role", roleLabel)
                .list()
                .getItems();
            
            Log.debug("Found " + pods.size() + " pods with role: " + roleLabel);
            
            if (pods.isEmpty()) {
                Log.warn("No pods found with role: " + roleLabel);
                return new PodMetrics(0.0, 0);
            }
            
            double totalSuccessRate = 0.0;
            long totalRequests = 0;
            int successfulPods = 0;
            
            for (Pod pod : pods) {
                try {
                    String podIp = pod.getStatus().getPodIP();
                    if (podIp == null) {
                        Log.warn("Pod has no IP: " + pod.getMetadata().getName());
                        continue;
                    }
                    
                    String podUrl = "http://" + podIp + ":8080/api/status";
                    Log.debug("Fetching metrics from pod " + pod.getMetadata().getName() + " at " + podUrl);
                    URL url = new URL(podUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    
                    int responseCode = conn.getResponseCode();
                    Log.debug("Response code from pod " + pod.getMetadata().getName() + ": " + responseCode);
                    if (responseCode == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();
                        
                        String jsonResponse = response.toString();
                        Log.debug("Got response from pod: " + jsonResponse);
                        
                        int successRateIndex = jsonResponse.indexOf("\"successRate\":");
                        if (successRateIndex != -1) {
                            String afterSuccessRate = jsonResponse.substring(successRateIndex + 14);
                            int commaIndex = afterSuccessRate.indexOf(",");
                            int braceIndex = afterSuccessRate.indexOf("}");
                            int endIndex = commaIndex != -1 ? Math.min(commaIndex, braceIndex) : braceIndex;
                            String successRateStr = afterSuccessRate.substring(0, endIndex).trim();
                            totalSuccessRate += Double.parseDouble(successRateStr);
                        }
                        
                        int requestCountIndex = jsonResponse.indexOf("\"requestCount\":");
                        if (requestCountIndex != -1) {
                            String afterRequestCount = jsonResponse.substring(requestCountIndex + 15);
                            int commaIndex = afterRequestCount.indexOf(",");
                            int braceIndex = afterRequestCount.indexOf("}");
                            int endIndex = commaIndex != -1 ? Math.min(commaIndex, braceIndex) : braceIndex;
                            String requestCountStr = afterRequestCount.substring(0, endIndex).trim();
                            totalRequests += Long.parseLong(requestCountStr);
                        }
                        
                        successfulPods++;
                    }
                } catch (java.net.SocketTimeoutException e) {
                    Log.debug("Pod not reachable (timeout): " + pod.getMetadata().getName());
                } catch (Exception e) {
                    Log.debug("Could not fetch metrics from pod " + pod.getMetadata().getName() + ": " + e.getMessage());
                }
            }
            
            double avgSuccessRate = successfulPods > 0 ? totalSuccessRate / successfulPods : 0.0;
            return new PodMetrics(avgSuccessRate, totalRequests);
            
        } catch (Exception e) {
            Log.debug("Error fetching metrics from pods with role " + roleLabel + ": " + e.getMessage());
            return new PodMetrics(0.0, 0);
        }
    }
    
    private static class PodMetrics {
        final double successRate;
        final long requestCount;
        
        PodMetrics(double successRate, long requestCount) {
            this.successRate = successRate;
            this.requestCount = requestCount;
        }
    }
}
