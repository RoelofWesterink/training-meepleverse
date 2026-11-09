package io.fluxzero.training.meepleverse.shop;

import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.ServeStatic;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.WebResponse;
import org.springframework.stereotype.Component;

@Component
@NoUserRequired
@ServeStatic(value = "/shop", resourcePath = "classpath:/shop", maxAgeSeconds = 0)
public class ShopEndpoint {
    @HandleGet("/")
    WebResponse home() {
        return WebResponse.builder().status(302).header("Location", "/shop/").build();
    }
}
