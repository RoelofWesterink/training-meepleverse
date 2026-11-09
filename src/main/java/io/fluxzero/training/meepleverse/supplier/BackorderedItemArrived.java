package io.fluxzero.training.meepleverse.supplier;

import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;

public record BackorderedItemArrived(ItemId itemId, OrderId orderId) {
}
