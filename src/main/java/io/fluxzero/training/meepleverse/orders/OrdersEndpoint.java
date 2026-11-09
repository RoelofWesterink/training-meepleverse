package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.sdk.web.PathParam;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.OrderDetails;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.orders.view.OrderStatus;
import org.springframework.stereotype.Component;

@Component
@Path("/orders")
public class OrdersEndpoint {

    @HandlePost
    OrderReceipt placeOrder(OrderDetails details) {
        var orderId = Fluxzero.generateId(OrderId.class);
        Fluxzero.sendCommandAndWait(new PlaceOrder(orderId, details));
        return new OrderReceipt(orderId);
    }

    @HandleGet("/{orderId}")
    WebResponse getOrder(@PathParam OrderId orderId) {
        var order = Fluxzero.loadAggregate(orderId).get();
        if (order == null || order.customerId() != null) {
            return WebResponse.builder().status(404).header("Cache-Control", "no-store").payload("Order not found").build();
        }
        return WebResponse.builder().status(200)
                .header("Cache-Control", "no-store")
                .payload(OrderStatus.from(order)).build();
    }

    public record OrderReceipt(OrderId orderId) {}
}
