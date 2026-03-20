package dev.kevindubois.demo;

import dev.kevindubois.demo.model.User;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/api/user")
public class UserResource {
    
    private static final Logger LOG = Logger.getLogger(UserResource.class);
    
    @ConfigProperty(name = "enable.null.pointer.bug", defaultValue = "false")
    boolean nullPointerBugEnabled;
    
    @GET
    @Path("/{id}")
    public Response getUser(@PathParam("id") String userId) {
        LOG.info("Fetching user: " + userId);
        
        if (!nullPointerBugEnabled) {
            // Safe implementation when bug is disabled
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
        
        // BUG: No null check when bug is enabled
        User user = findUser(userId);
        String userName = user.getName(); // Line 39 - NPE HERE when user is null!
        
        return Response.ok()
            .entity(Map.of("id", userId, "name", userName))
            .build();
    }
    
    private User findUser(String userId) {
        // Returns null for 20% of requests (simulating unknown users)
        if (Math.random() < 0.8) {
            return new User(userId, "User " + userId);
        }
        return null; // BUG: Returns null
    }
}

