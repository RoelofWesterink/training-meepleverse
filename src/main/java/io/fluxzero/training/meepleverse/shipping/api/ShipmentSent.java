package io.fluxzero.training.meepleverse.shipping.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ShipmentSent(ShipmentId shipmentId, @Valid @NotNull ShipmentDetails details) implements ShipmentUpdate {

    @Apply
    Shipment apply() {
        return new Shipment(shipmentId, details);
    }
}
