package io.fluxzero.training.meepleverse.orders.view;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.payments.api.PaymentRejected;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.shipping.api.ShippingFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static io.fluxzero.training.meepleverse.orders.view.OrderObservation.Kind.*;

@Component
public class OrderProgressProjection {
    @HandleEvent
    void on(PlaceOrder event, Instant timestamp) {
        store(new OrderObservation(event.orderId(), ORDER_RECEIVED, event.details().itemIds(),
                                   null, null, timestamp), "order");
    }

    @HandleEvent
    void on(PaymentAccepted event, Instant timestamp) {
        store(new OrderObservation(new OrderId(event.reference()), PAYMENT_ACCEPTED, List.of(),
                                   null, null, timestamp), event.paymentId().toString());
    }

    @HandleEvent
    void on(PaymentRejected event, Instant timestamp) {
        store(new OrderObservation(new OrderId(event.reference()), PAYMENT_REJECTED, List.of(),
                                   null, null, timestamp), event.paymentId().toString());
    }

    @HandleEvent
    void on(BackorderedItemArrived event, Instant timestamp) {
        store(new OrderObservation(event.orderId(), ITEM_ARRIVED, List.of(event.itemId()),
                                   null, null, timestamp), event.itemId().toString());
    }

    @HandleEvent
    void on(BackorderFailed event, Instant timestamp) {
        store(new OrderObservation(event.orderId(), BACKORDER_FAILED, List.of(),
                                   null, "Backorder failed", timestamp), "backorder");
    }

    @HandleEvent
    void on(ShipmentSent event, Instant timestamp) {
        // ShipmentSent is recorded before the carrier is called.
        store(new OrderObservation(event.details().orderId(), SHIPMENT_RECORDED,
                                   event.details().itemIds(), event.shipmentId(), null,
                                   timestamp), event.shipmentId().toString());
    }

    @HandleEvent
    void on(ShippingFailed event, Instant timestamp) {
        store(new OrderObservation(event.orderId(), SHIPPING_FAILED, List.of(), event.shipmentId(),
                                   "Shipment failed", timestamp), event.shipmentId().toString());
    }

    @HandleEvent
    void on(AbortOrder event, Instant timestamp) {
        store(new OrderObservation(event.orderId(), ORDER_ABORTED, List.of(), null,
                                   event.reason(), timestamp), "abort");
    }

    private void store(OrderObservation observation, String subject) {
        Fluxzero.prepareIndex(observation)
                .id(observation.orderId() + ":" + observation.kind() + ":" + subject)
                .collection(OrderObservation.class).indexAndWait();
    }
}
