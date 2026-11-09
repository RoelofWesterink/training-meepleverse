package io.fluxzero.training.meepleverse.shipping.api.model;

import io.fluxzero.sdk.modeling.Id;

public class ShipmentId extends Id<Shipment> {
    public ShipmentId(String functionalId) {
        super(functionalId);
    }
}
