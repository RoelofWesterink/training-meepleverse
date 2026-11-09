package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.payments.api.PaymentRejected;
import io.fluxzero.training.meepleverse.payments.api.ValidatePayment;
import io.fluxzero.training.meepleverse.shipping.api.SendShipment;
import io.fluxzero.training.meepleverse.shipping.api.ShippingFailed;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import io.fluxzero.training.meepleverse.supplier.BackorderFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderItems;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import org.springframework.stereotype.Component;

@Component
public class OrderFulfillment {

    @HandleEvent
    void handle(PlaceOrder event) {
        Fluxzero.sendAndForgetCommand(new ValidatePayment(event.details().paymentId(), event.orderId().toString()));
    }

    @HandleEvent
    void handle(PaymentAccepted event) {
        var order = Fluxzero.<Order>loadEntityValue(event.reference());
        Fluxzero.sendAndForgetCommand(new BackorderItems(order.details().itemIds(), order.orderId(),
                                                         order.details().addressee().fullName()));
    }

    @HandleEvent
    void handle(PaymentRejected event) {
        var order = Fluxzero.<Order>loadEntityValue(event.reference());
        Fluxzero.sendAndForgetCommand(new AbortOrder(order.orderId(), "Payment rejected"));
    }

    @HandleEvent
    void handle(BackorderFailed event) {
        Fluxzero.sendAndForgetCommand(new AbortOrder(event.orderId(), "Backorder failed"));
    }

    @HandleEvent
    void handle(BackorderedItemArrived event) {
        var order = Fluxzero.loadAggregate(event.orderId()).get();
        Fluxzero.sendAndForgetCommand(new SendShipment(Fluxzero.generateId(ShipmentId.class),
                                                      new ShipmentDetails(event.itemId(), order.orderId(),
                                                                       order.details().addressee())));
    }

    @HandleEvent
    void handle(ShippingFailed event) {
        Fluxzero.sendAndForgetCommand(new AbortOrder(event.orderId(), "Shipment failed"));
    }

}
