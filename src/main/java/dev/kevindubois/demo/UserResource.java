package dev.kevindubois.demo;

import dev.kevindubois.demo.model.User;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/api/user")
public class UserResource {
    
    private static final Logger LOG = Logger.getLogger(UserResource.class);
    
    @GET
    @Path("/{id}")
    public Response getUser(@PathParam("id") String userId) {
        LOG.debug("Fetching user: " + userId);
        
        User user = findUser(userId);
        if (user == null) {
            return Response.status(404)
                .entity(Map.of("error", "User not found"))
                .build();
        }
        return Response.ok()
            .entity(Map.of("id", userId, "name", user.getName()))
            .build();
    }
    
    private User findUser(String userId) {
        // Returns null for 20% of requests (simulating unknown users)
        if (Math.random() < 0.8) {
            return new User(userId, "User " + userId);
        }
        return null;
    }
}

