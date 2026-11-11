package io.fluxzero.training.meepleverse.supplier;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.OrderUpdate;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;

import java.util.ArrayList;
import java.util.List;

public record BackorderedItemArrived(OrderId orderId, ItemId itemId) implements OrderUpdate {

    @Apply
    Order apply(Order order) {
        List<ItemId> current = order.arrivedItemIds() != null ? order.arrivedItemIds() : List.of();
        if (current.contains(itemId)) {
            return order;  // Idempotent - no change, no event (IF_MODIFIED)
        }
        List<ItemId> arrived = new ArrayList<>(current);
        arrived.add(itemId);
        return order.withArrivedItemIds(List.copyOf(arrived));
    }
}
