package io.fluxzero.training.meepleverse.shipping.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.TrackSelf;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import jakarta.validation.constraints.NotNull;

@TrackSelf
public interface ShipmentUpdate {
    @NotNull
    ShipmentId shipmentId();

    @HandleCommand
    default void handle() {
        Fluxzero.loadAggregate(shipmentId()).assertAndApply(this);
    }
}
