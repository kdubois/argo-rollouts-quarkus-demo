import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("/load")
public class LoadGeneratorService {
    @Inject
    private MetricsResource metricsResource;

    @GET
    @Path("/generate")
    public Response generateLoad(@QueryParam("count") Integer count) {
        if (count == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Count parameter is required").build();
        }
        try {
            // Generate load logic here
            String result = "Generated load for " + count + " requests";
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error generating load: " + e.getMessage()).build();
        }
    }
}