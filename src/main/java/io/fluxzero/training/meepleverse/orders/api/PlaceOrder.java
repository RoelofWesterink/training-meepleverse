package io.fluxzero.training.meepleverse.orders.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.orders.api.model.OrderDetails;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@NoUserRequired
public record PlaceOrder(OrderId orderId, @NotNull @Valid OrderDetails details, String customerId) implements OrderUpdate {

    public PlaceOrder(OrderId orderId, OrderDetails details) { this(orderId, details, null); }

    @Apply
    Order apply() {
        return new Order(orderId, details, customerId);
    }

}
