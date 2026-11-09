package io.fluxzero.training.setup;

import io.fluxzero.sdk.test.TestFixture;
import org.junit.jupiter.api.Test;

class WelcomeEndpointTest {
    @Test
    void showsThatTheEnvironmentWorks() {
        TestFixture.create(new WelcomeEndpoint()).whenGet("/api/status")
                .expectWebResult(response -> response.getStatus() == 200
                        && response.<String>getPayloadAs(String.class)
                        .startsWith("Your Fluxzero development environment is working."));
    }
}
