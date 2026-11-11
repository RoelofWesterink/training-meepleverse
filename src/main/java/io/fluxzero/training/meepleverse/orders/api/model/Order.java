package io.fluxzero.training.meepleverse.orders.api.model;

import io.fluxzero.sdk.modeling.Aggregate;
import io.fluxzero.common.serialization.Revision;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import lombok.With;

import java.util.List;

import static io.fluxzero.sdk.modeling.EventPublication.IF_MODIFIED;

@Aggregate(searchable = true, eventPublication = IF_MODIFIED)
@Revision(1)
@With
public record Order(OrderId orderId, OrderDetails details, boolean paid, boolean aborted, String customerId,
                    List<ItemId> arrivedItemIds) {

    public Order(OrderId orderId, OrderDetails details, String customerId) {
        this(orderId, details, false, false, customerId, List.of());
    }

    public boolean allItemsArrived() {
        return arrivedItemIds != null && arrivedItemIds.containsAll(details.itemIds());
    }
}
