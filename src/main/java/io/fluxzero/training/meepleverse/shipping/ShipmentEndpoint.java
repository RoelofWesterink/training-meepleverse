package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.sdk.web.QueryParam;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@NoUserRequired
@Path("/shipments")
public class ShipmentEndpoint {

    @HandleGet
    List<Shipment> findByItem(@QueryParam ItemId itemId) {
        if (itemId == null) return List.of();
        return Fluxzero.search(Shipment.class)
                .match(itemId, "details/itemIds")
                .sortByTimestamp(true)
                .fetch(100);
    }
}
