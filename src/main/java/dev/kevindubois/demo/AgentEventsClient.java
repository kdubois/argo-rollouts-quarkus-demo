package dev.kevindubois.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/a2a/events")
@RegisterRestClient(configKey = "kubernetes-agent")
public interface AgentEventsClient {

    @GET
    List<AgentActivityResource.ActivityEvent> getEvents(@QueryParam("since") Long sinceId);
}
