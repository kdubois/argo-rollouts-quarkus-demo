package dev.kevindubois.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/a2a/rollout")
@RegisterRestClient(configKey = "kubernetes-agent")
public interface KubernetesRolloutClient {

    @GET
    @Path("/summary")
    RolloutSummaryResponse getSummary(@QueryParam("namespace") String namespace, @QueryParam("name") String name);

    record RolloutSummaryResponse(
            String namespace,
            String name,
            String phase,
            int canaryWeight,
            int stableWeight,
            long stablePodCount,
            long canaryPodCount,
            boolean available,
            String error
    ) {}
}
