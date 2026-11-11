package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.tracking.handling.Association;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.sdk.tracking.handling.Stateful;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.Addressee;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.shipping.api.SendShipment;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import io.fluxzero.training.meepleverse.shipping.api.ShippingFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import lombok.With;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Stateful
@With
public record OrderBackorderTracker(
        @EntityId @Association OrderId orderId,
        Set<ItemId> pendingItems,
        Set<ItemId> arrivedItems,
        Addressee addressee,
        boolean paymentAccepted
) {

    @HandleEvent
    static OrderBackorderTracker on(PlaceOrder event) {
        return new OrderBackorderTracker(
                event.orderId(),
                new HashSet<>(event.details().itemIds()),
                new HashSet<>(),
                event.details().addressee(),
                false
        );
    }

    @HandleEvent
    @Association(value = "reference", path = "orderId")
    OrderBackorderTracker on(PaymentAccepted event) {
        return checkAndShip(withPaymentAccepted(true));
    }

    @HandleEvent
    OrderBackorderTracker on(BackorderedItemArrived event) {
        Set<ItemId> newArrived = new HashSet<>(arrivedItems);
        newArrived.add(event.itemId());
        return checkAndShip(withArrivedItems(newArrived));
    }

    private OrderBackorderTracker checkAndShip(OrderBackorderTracker updated) {
        if (updated.paymentAccepted && updated.arrivedItems.containsAll(updated.pendingItems)) {
            Fluxzero.sendAndForgetCommand(new SendShipment(
                    Fluxzero.generateId(ShipmentId.class),
                    new ShipmentDetails(List.copyOf(updated.pendingItems), orderId, addressee)
            ));
            return null;
        }
        return updated;
    }

    @HandleEvent
    OrderBackorderTracker on(ShippingFailed event) {
        Fluxzero.sendAndForgetCommand(new AbortOrder(orderId, "Shipment failed"));
        return null;
    }
}
