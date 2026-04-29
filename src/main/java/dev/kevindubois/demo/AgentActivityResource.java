package dev.kevindubois.demo;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Instant;
import java.util.List;

@Path("/api/agent")
public class AgentActivityResource {

    @Inject
    @RestClient
    AgentEventsClient agentEventsClient;

    private final BroadcastProcessor<ActivityEvent> processor = BroadcastProcessor.create();
    private long lastEventId = 0;

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

    @GET
    @Path("/events/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ActivityEvent> streamEvents() {
        return processor;
    }

    @Scheduled(every = "0.5s")
    void pollAndBroadcast() {
        try {
            Long sinceId = lastEventId > 0 ? lastEventId : null;
            List<ActivityEvent> events = agentEventsClient.getEvents(sinceId);
            for (ActivityEvent event : events) {
                processor.onNext(event);
                if (event.id() > lastEventId) {
                    lastEventId = event.id();
                }
            }
        } catch (Exception e) {
            Log.trace("Could not reach kubernetes-agent for SSE bridge: " + e.getMessage());
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
