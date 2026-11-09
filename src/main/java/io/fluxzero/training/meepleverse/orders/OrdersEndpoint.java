package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.OrderDetails;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import org.springframework.stereotype.Component;

@Component
@Path("/orders")
public class OrdersEndpoint {

    @HandlePost
    void placeOrder(OrderDetails details) {
        Fluxzero.sendCommandAndWait(new PlaceOrder(Fluxzero.generateId(OrderId.class), details));
    }

}
