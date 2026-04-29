package dev.kevindubois.demo;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.List;

@Path("/api/agent")
public class AgentActivityResource {

    @Inject
    @RestClient
    AgentEventsClient agentEventsClient;

    @GET
    @Path("/events")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ActivityEvent> getEvents(@QueryParam("since") Long sinceId) {
        try {
            return agentEventsClient.getEvents(sinceId);
        } catch (Exception e) {
            Log.debug("Could not reach kubernetes-agent for events: " + e.getMessage());
            return List.of();
        }
    }

    public record ActivityEvent(
        long id,
        Instant timestamp,
        String type,
        String message,
        String details
    ) {}
}
