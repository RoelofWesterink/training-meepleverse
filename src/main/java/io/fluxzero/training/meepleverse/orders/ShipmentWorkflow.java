package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.Association;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.sdk.tracking.handling.Stateful;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.shipping.api.SendShipment;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;

import java.util.HashSet;
import java.util.Set;

@Stateful
@Consumer(name = "order-shipments")
public record ShipmentWorkflow(@EntityId @Association OrderId orderId, Set<ItemId> missing, boolean paid) {
    @HandleEvent
    static ShipmentWorkflow start(PlaceOrder event) {
        return new ShipmentWorkflow(event.orderId(), Set.copyOf(event.details().itemIds()), false);
    }

    @HandleEvent
    @Association(value = "reference", path = "orderId")
    ShipmentWorkflow on(PaymentAccepted event) {
        return new ShipmentWorkflow(orderId, missing, true).shipIfReady();
    }

    @HandleEvent
    ShipmentWorkflow on(BackorderedItemArrived event) {
        if (!missing.contains(event.itemId())) {
            return this;
        }
        var remaining = new HashSet<>(missing);
        remaining.remove(event.itemId());
        return new ShipmentWorkflow(orderId, remaining, paid).shipIfReady();
    }

    @HandleEvent
    ShipmentWorkflow on(AbortOrder event) {
        return null;
    }

    private ShipmentWorkflow shipIfReady() {
        if (!paid || !missing.isEmpty()) {
            return this;
        }
        Order order = Fluxzero.loadEntityValue(orderId);
        if (!order.aborted()) {
            var details = new ShipmentDetails(order.details().itemIds(), orderId, order.details().addressee());
            Fluxzero.sendAndForgetCommand(new SendShipment(new ShipmentId(orderId + "-shipment"), details));
        }
        return null;
    }
}
