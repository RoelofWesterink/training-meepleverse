package io.fluxzero.training.setup;

import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.ServeStatic;
import io.fluxzero.sdk.web.WebResponse;
import org.springframework.stereotype.Component;

@Component
@ServeStatic(value = "/", resourcePath = "classpath:/welcome", ignorePaths = {"/api/*"}, maxAgeSeconds = 0)
public class WelcomeEndpoint {
    @HandleGet("/api/status")
    WebResponse status() {
        return WebResponse.builder()
                .header("Content-Type", "text/plain; charset=utf-8")
                .payload("Your Fluxzero development environment is working.")
                .build();
    }
}
