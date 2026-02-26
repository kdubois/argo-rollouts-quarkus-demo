package dev.kevindubois.demo;

import dev.kevindubois.demo.model.AnalysisInfo;
import dev.kevindubois.demo.model.RolloutInfo;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@Path("/api/rollout")
public class RolloutStatusResource {

    private static final Logger LOG = Logger.getLogger(RolloutStatusResource.class);

    @Inject
    KubernetesClient kubernetesClient;

    @ConfigProperty(name = "rollout.name", defaultValue = "argo-rollouts-quarkus-demo")
    String rolloutName;

    @ConfigProperty(name = "rollout.namespace", defaultValue = "argo-rollouts-quarkus-demo")
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
                LOG.warn("Rollout not found: " + rolloutName + " in namespace " + rolloutNamespace);
                return RolloutInfo.notFound();
            }

            RolloutInfo info = extractRolloutInfo(rollout);
            LOG.debugf("Rollout status: phase=%s, canaryWeight=%d, stableWeight=%d",
                    info.phase(), info.canaryWeight(), info.stableWeight());
            return info;

        } catch (Exception e) {
            LOG.error("Error fetching rollout status", e);
            return new RolloutInfo(
                    rolloutName,
                    "Error",
                    0,
                    100,
                    "Error fetching rollout: " + e.getMessage(),
                    null
            );
        }
    }

    private RolloutInfo extractRolloutInfo(GenericKubernetesResource rollout) {
        Map<String, Object> status = getStatus(rollout);
        Map<String, Object> spec = getSpec(rollout);

        String phase = getPhase(status);
        String message = getMessage(status);
        Integer canaryWeight = getCanaryWeight(status);
        Integer stableWeight = 100 - canaryWeight;

        AnalysisInfo analysisInfo = getAnalysisInfo(status);

        return new RolloutInfo(
                rolloutName,
                phase,
                canaryWeight,
                stableWeight,
                message,
                analysisInfo
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getStatus(GenericKubernetesResource resource) {
        Object status = resource.getAdditionalProperties().get("status");
        return status instanceof Map ? (Map<String, Object>) status : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSpec(GenericKubernetesResource resource) {
        Object spec = resource.getAdditionalProperties().get("spec");
        return spec instanceof Map ? (Map<String, Object>) spec : Map.of();
    }

    private String getPhase(Map<String, Object> status) {
        Object phase = status.get("phase");
        return phase != null ? phase.toString() : "Unknown";
    }

    private String getMessage(Map<String, Object> status) {
        Object message = status.get("message");
        return message != null ? message.toString() : "";
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
            LOG.debug("Could not extract canary weight", e);
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
                LOG.debugf("No AnalysisRuns found for rollout %s", rolloutName);
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
            
            Map<String, Object> metadata = (Map<String, Object>) latestAnalysisRun.getAdditionalProperties().get("metadata");
            String analysisRunName = metadata != null ? (String) metadata.get("name") : "unknown";
            LOG.debugf("Found AnalysisRun %s: phase=%s, message=%s, successful=%s", analysisRunName, phase, message, successful);
            return new AnalysisInfo(phase, message, successful);

        } catch (Exception e) {
            LOG.error("Error fetching analysis info", e);
            return new AnalysisInfo("Error", "Error fetching analysis: " + e.getMessage(), null);
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
            LOG.error("Error fetching deployment scenarios", e);
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
                LOG.debug("No pods found with role: " + roleLabel);
                return "none";
            }
            
            // Get the first pod and extract SCENARIO_MODE env var
            Pod pod = pods.get(0);
            LOG.debug("Found pod: " + pod.getMetadata().getName() + " with role: " + roleLabel);
            
            if (pod.getSpec() != null &&
                pod.getSpec().getContainers() != null &&
                !pod.getSpec().getContainers().isEmpty()) {
                
                List<EnvVar> envVars = pod.getSpec().getContainers().get(0).getEnv();
                if (envVars != null) {
                    for (EnvVar envVar : envVars) {
                        if ("SCENARIO_MODE".equals(envVar.getName())) {
                            String value = envVar.getValue() != null ? envVar.getValue() : "unknown";
                            LOG.debug("Found SCENARIO_MODE=" + value + " for role: " + roleLabel);
                            return value;
                        }
                    }
                }
            }
            
            LOG.debug("SCENARIO_MODE not found in pod env vars for role: " + roleLabel);
            return "unknown";
        } catch (Exception e) {
            LOG.error("Could not fetch scenario from pods with role " + roleLabel + ": " + e.getMessage(), e);
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
}

// Made with Bob
