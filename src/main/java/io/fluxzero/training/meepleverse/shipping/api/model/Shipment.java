package io.fluxzero.training.meepleverse.shipping.api.model;

import io.fluxzero.common.serialization.Revision;
import io.fluxzero.sdk.modeling.Aggregate;

import static io.fluxzero.sdk.modeling.EventPublication.IF_MODIFIED;

@Aggregate(searchable = true, eventPublication = IF_MODIFIED)
@Revision(1)
public record Shipment(ShipmentId shipmentId, ShipmentDetails details) {
}
