package io.fluxzero.training.meepleverse.orders.api.model;

import io.fluxzero.sdk.modeling.Aggregate;
import lombok.With;

import static io.fluxzero.sdk.modeling.EventPublication.IF_MODIFIED;

@Aggregate(searchable = true, eventPublication = IF_MODIFIED)
@With
public record Order(OrderId orderId, OrderDetails details, boolean paid, boolean aborted) {

    public Order(OrderId orderId, OrderDetails details) {
        this(orderId, details, false, false);
    }
}
