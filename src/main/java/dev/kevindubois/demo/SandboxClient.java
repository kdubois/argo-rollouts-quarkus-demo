package dev.kevindubois.demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

/**
 * Client for the remediation-agent's sandbox view: which sandbox is
 * currently running and its classified agent log.
 */
@Path("/sandbox")
@RegisterRestClient(configKey = "remediation-agent")
public interface SandboxClient {

    @GET
    @Path("/active")
    Map<String, String> getActiveSandbox();

    @GET
    @Path("/{name}/log")
    Map<String, Object> getLog(@PathParam("name") String name, @QueryParam("after") int after);
}
