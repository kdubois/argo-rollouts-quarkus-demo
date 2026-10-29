package dev.kevindubois.demo;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

/**
 * Proxies the remediation-agent's sandbox view for the dashboard: the
 * UI polls {@code /api/sandbox/active} and, while a remediation runs,
 * fetches the coding agent's classified log lines to stream in place of
 * the live-requests visualization.
 */
@Path("/api/sandbox")
public class SandboxProxyResource {

    @Inject
    @RestClient
    SandboxClient sandboxClient;

    @GET
    @Path("/active")
    @Produces(MediaType.APPLICATION_JSON)
    public Response active() {
        try {
            return Response.ok(sandboxClient.getActiveSandbox()).build();
        } catch (Exception e) {
            Log.debugf("No active sandbox: %s", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/{name}/log")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> log(@PathParam("name") String name, @QueryParam("after") int after) {
        return sandboxClient.getLog(name, after);
    }
}
