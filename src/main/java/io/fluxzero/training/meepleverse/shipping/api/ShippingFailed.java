package io.fluxzero.training.meepleverse.shipping.api;

import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;

public record ShippingFailed(ShipmentId shipmentId, OrderId orderId) {
}
