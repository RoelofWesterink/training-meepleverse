package io.fluxzero.training.meepleverse.shipping.api.model;

import io.fluxzero.common.serialization.Revision;
import io.fluxzero.sdk.modeling.Aggregate;

import static io.fluxzero.sdk.modeling.EventPublication.IF_MODIFIED;

@Revision(1)
@Aggregate(searchable = true, eventPublication = IF_MODIFIED)
public record Shipment(ShipmentId shipmentId, ShipmentDetails details) {
}
