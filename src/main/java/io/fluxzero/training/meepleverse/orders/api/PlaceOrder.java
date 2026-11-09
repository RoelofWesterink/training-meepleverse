package io.fluxzero.training.meepleverse.orders.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.orders.api.model.OrderDetails;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@NoUserRequired
public record PlaceOrder(OrderId orderId, @NotNull @Valid OrderDetails details) implements OrderUpdate {

    @Apply
    Order apply() {
        return new Order(orderId, details);
    }

}
