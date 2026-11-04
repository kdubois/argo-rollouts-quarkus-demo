package dev.kevindubois.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/rollout")
@RegisterRestClient(configKey = "kubernetes-agent")
public interface KubernetesRolloutClient {

    @GET
    @Path("/summary")
    RolloutSummaryResponse getSummary(@QueryParam("namespace") String namespace, @QueryParam("name") String name);

    @POST
    @Path("/promote")
    Response promote(@QueryParam("namespace") String namespace, @QueryParam("name") String name);

    @POST
    @Path("/abort")
    Response abort(@QueryParam("namespace") String namespace, @QueryParam("name") String name);

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
