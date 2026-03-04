package dev.kevindubois.demo;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
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

    @ConfigProperty(name = "app.version", defaultValue = "1.0.0")
    String appVersion;

    @ConfigProperty(name = "scenario.mode", defaultValue = "happy")
    String scenarioMode;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        return dashboard
                .data("version", appVersion)
                .data("scenario", scenarioMode);
    }
}

