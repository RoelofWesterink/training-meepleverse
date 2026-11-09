package io.fluxzero.training.meepleverse.supplier;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.sdk.web.PathParam;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import org.springframework.stereotype.Component;

@Component
@Path("/supply")
public class SupplyEndpoint {

    @HandlePost("/arrival/{orderId}/{itemId}")
    void registerArrival(@PathParam OrderId orderId, @PathParam ItemId itemId) {
        Fluxzero.publishEvent(new BackorderedItemArrived(itemId, orderId));
    }

}
