import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("/metrics")
public class MetricsResource {
    @GET
    @Path("/status")
    public Response getStatus() {
        try {
            // Simulate fetching metrics status
            String status = fetchMetricsStatus();
            return Response.ok(status).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error fetching metrics status: " + e.getMessage()).build();
        }
    }

    private String fetchMetricsStatus() {
        // Logic to fetch and return the metrics status
        return "OK";
    }
}