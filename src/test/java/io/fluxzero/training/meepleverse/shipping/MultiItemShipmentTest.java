package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.training.meepleverse.catalog.CatalogHandler;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.OrderFulfillment;
import io.fluxzero.training.meepleverse.orders.api.model.Addressee;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.orders.api.model.OrderDetails;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import io.fluxzero.training.meepleverse.shipping.api.SendShipment;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.supplier.BackorderItems;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiItemShipmentTest {

    final OrderId orderId = new OrderId("multi-item-order");
    final ItemId item1 = new ItemId("gloomhaven");
    final ItemId item2 = new ItemId("ark-nova");
    final ItemId item3 = new ItemId("wingspan");
    final PaymentId paymentId = new PaymentId("payment-multi");
    final Addressee addressee = new Addressee("Bob Example", "Game Street", "7", "54321",
            "Boardgametown", "Netherlands", "bob@example.com", "");

    TestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = TestFixture.create(CatalogHandler.class, OrderFulfillment.class)
                .givenCommands("/catalog/register-items.json")
                .registerHandlers(new ExternalsMock());
    }

    @Test
    void singleItemOrderShipsImmediately() {
        var singleItemOrder = new PlaceOrder(orderId, 
                new OrderDetails(List.of(item1), paymentId, addressee));
        
        fixture.givenCommands(singleItemOrder)
                .whenCommand(new BackorderedItemArrived(orderId, item1))
                .expectEvents(BackorderedItemArrived.class, ShipmentSent.class);
    }

    @Test
    void twoItemOrderWaitsForBothItems() {
        var twoItemOrder = new PlaceOrder(orderId,
                new OrderDetails(List.of(item1, item2), paymentId, addressee));

        fixture.givenCommands(twoItemOrder)
                // First item arrives - no shipment yet
                .whenCommand(new BackorderedItemArrived(orderId, item1))
                .expectEvents(BackorderedItemArrived.class)
                .andThen()
                // Second item arrives - now ship with both items
                .whenCommand(new BackorderedItemArrived(orderId, item2))
                .expectEvents(BackorderedItemArrived.class, ShipmentSent.class)
                .expectCommands(SendShipment.class);
    }

    @Test
    void threeItemOrderWaitsForAllItems() {
        var threeItemOrder = new PlaceOrder(orderId,
                new OrderDetails(List.of(item1, item2, item3), paymentId, addressee));

        fixture.givenCommands(threeItemOrder)
                // First item
                .whenCommand(new BackorderedItemArrived(orderId, item1))
                .expectEvents(BackorderedItemArrived.class)
                .andThen()
                // Second item - still waiting
                .whenCommand(new BackorderedItemArrived(orderId, item2))
                .expectEvents(BackorderedItemArrived.class)
                .andThen()
                // Third item - now ship
                .whenCommand(new BackorderedItemArrived(orderId, item3))
                .expectEvents(BackorderedItemArrived.class, ShipmentSent.class);
    }

    @Test
    void orderTracksArrivedItems() {
        var twoItemOrder = new PlaceOrder(orderId,
                new OrderDetails(List.of(item1, item2), paymentId, addressee));

        fixture.givenCommands(twoItemOrder)
                .whenCommand(new BackorderedItemArrived(orderId, item1))
                .expectNoErrors()
                .andThen()
                .whenExecuting(f -> {
                    Order order = Fluxzero.loadAggregate(orderId).get();
                    assertEquals(List.of(item1), order.arrivedItemIds());
                    assertFalse(order.allItemsArrived());
                })
                .andThen()
                .whenCommand(new BackorderedItemArrived(orderId, item2))
                .andThen()
                .whenExecuting(f -> {
                    Order order = Fluxzero.loadAggregate(orderId).get();
                    assertEquals(2, order.arrivedItemIds().size());
                    assertTrue(order.arrivedItemIds().containsAll(List.of(item1, item2)));
                    assertTrue(order.allItemsArrived());
                });
    }

    static class ExternalsMock {
        @HandlePost("/psp/validate/{paymentId}")
        String validate(String paymentId) {
            return JsonUtils.asJson(Map.of("accepted", true));
        }

        @HandlePost("/supplier/order")
        void backorder(BackorderItems body) {
        }

        @HandlePost("/shipper/send")
        void ship(SendShipment body) {
        }
    }
}
