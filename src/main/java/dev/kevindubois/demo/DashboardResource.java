package dev.kevindubois.demo;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/")
public class DashboardResource {

    @Inject
    Template dashboard;

    @Inject
    RoutingContext routingContext;

    @ConfigProperty(name = "app.version", defaultValue = "1.0.0")
    String appVersion;

    @ConfigProperty(name = "scenario.mode", defaultValue = "happy")
    String scenarioMode;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        // the bundles are content-hashed, but the HTML itself must always be
        // fetched fresh so a new deploy is visible without a hard refresh
        routingContext.response().putHeader("Cache-Control", "no-cache");
        return dashboard
                .data("version", appVersion)
                .data("scenario", scenarioMode);
    }
}
