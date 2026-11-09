package io.fluxzero.training.meepleverse.orders.api.model;

import io.fluxzero.sdk.modeling.Id;

public class OrderId extends Id<Order> {
    public OrderId(String functionalId) {
        super(functionalId);
    }
}
