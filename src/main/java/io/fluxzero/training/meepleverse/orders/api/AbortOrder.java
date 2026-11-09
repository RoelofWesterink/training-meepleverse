package io.fluxzero.training.meepleverse.orders.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.training.meepleverse.authentication.RequiresRole;
import io.fluxzero.training.meepleverse.authentication.Role;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import jakarta.validation.constraints.NotBlank;

@RequiresRole(Role.ADMIN)
public record AbortOrder(OrderId orderId, @NotBlank String reason) implements OrderUpdate {
    @Apply
    Order apply(Order order) {
        return order.withAborted(true);
    }
}
