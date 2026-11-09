package io.fluxzero.training.meepleverse.supplier;

import io.fluxzero.training.meepleverse.orders.api.model.OrderId;

public record BackorderFailed(OrderId orderId) {
}
