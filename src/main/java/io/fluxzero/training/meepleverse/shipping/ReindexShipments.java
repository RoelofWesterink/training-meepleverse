package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;
import org.springframework.stereotype.Component;

@Component
@Consumer(name = "shipment-item-lists", minIndex = 0)
public class ReindexShipments {
    @HandleDocument
    Shipment migrate(Shipment shipment) {
        return shipment;
    }
}
