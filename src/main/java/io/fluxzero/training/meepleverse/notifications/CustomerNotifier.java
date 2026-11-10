package io.fluxzero.training.meepleverse.notifications;

import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static io.fluxzero.sdk.Fluxzero.sendAndForgetCommand;

@Component
@Slf4j
public class CustomerNotifier {

    @HandleEvent
    void handle(PlaceOrder event, Order order) {
        sendAndForgetCommand(new SendEmail(order.details().addressee().email(), "We're processing your order"));
        log.info("Order {} placed for {}", order.orderId(), order.details().addressee().fullName());
    }

    @HandleEvent
    void handle(ShipmentSent event, Shipment shipment) {
        sendAndForgetCommand(new SendEmail(shipment.details().addressee().email(), "We've sent an item"));
        log.info("Shipment {} sent for order {}, item {}", shipment.shipmentId(), shipment.details().orderId(),
                 shipment.details().itemId());
    }

    @HandleEvent
    void handle(AbortOrder event, Order order) {
        sendAndForgetCommand(new SendEmail(order.details().addressee().email(), "Your order was aborted"));
        log.info("Order {} aborted", order.orderId());
    }

}
