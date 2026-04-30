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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Path("/api/agent")
public class AgentActivityResource {

    private static final int MAX_CACHED_EVENTS = 100;

    @Inject
    @RestClient
    AgentEventsClient agentEventsClient;

    private final BroadcastProcessor<ActivityEvent> processor = BroadcastProcessor.create();
    private final List<ActivityEvent> cachedEvents = new ArrayList<>();
    private long lastPolledId = 0;

    @GET
    @Path("/events")
    @Produces(MediaType.APPLICATION_JSON)
    public synchronized List<ActivityEvent> getEvents(@QueryParam("since") Long sinceId) {
        if (sinceId != null && sinceId > 0) {
            return cachedEvents.stream()
                .filter(e -> e.id() > sinceId)
                .toList();
        }
        return Collections.unmodifiableList(new ArrayList<>(cachedEvents));
    }

    @GET
    @Path("/events/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ActivityEvent> streamEvents() {
        return processor;
    }

    @Scheduled(every = "0.5s")
    synchronized void pollAndBroadcast() {
        try {
            Long sinceId = lastPolledId > 0 ? lastPolledId : null;
            List<ActivityEvent> events = agentEventsClient.getEvents(sinceId);
            for (ActivityEvent event : events) {
                cachedEvents.add(event);
                processor.onNext(event);
                if (event.id() > lastPolledId) {
                    lastPolledId = event.id();
                }
            }
            while (cachedEvents.size() > MAX_CACHED_EVENTS) {
                cachedEvents.remove(0);
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
