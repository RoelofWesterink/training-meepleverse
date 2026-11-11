package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;

@Consumer(name = "ShipmentReindexer", minIndex = 0)
public class ShipmentReindexer {

    @HandleDocument
    Shipment reindex(Shipment shipment) {
        return shipment;
    }
}
