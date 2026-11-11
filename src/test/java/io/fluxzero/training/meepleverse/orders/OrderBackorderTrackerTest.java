package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.training.meepleverse.catalog.CatalogHandler;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.SendShipment;
import io.fluxzero.training.meepleverse.supplier.BackorderItems;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderBackorderTrackerTest {
    final TestFixture fixture = TestFixture.create(CatalogHandler.class, OrderFulfillment.class, OrderBackorderTracker.class)
            .givenCommands("/catalog/register-items.json");

    @BeforeEach
    void setUp() {
        fixture.registerHandlers(new ExternalsMock());
    }

    @Test
    void trackerCreatedOnPlaceOrder() {
        fixture.whenCommand("/orders/place-order-double.json")
                .andThen()
                .whenExecuting(f -> {
                    var tracker = Fluxzero.<OrderBackorderTracker>loadEntityValue("order2");
                    assertNotNull(tracker);
                    assertEquals(new OrderId("order2"), tracker.orderId());
                    assertEquals(2, tracker.pendingItems().size());
                    assertTrue(tracker.arrivedItems().isEmpty());
                    assertTrue(tracker.paymentAccepted());
                });
    }

    @Test
    void trackerTracksArrivedItems() {
        fixture.givenCommands("/orders/place-order-double.json")
                .whenEvent(new BackorderedItemArrived(new ItemId("gloomhaven"), new OrderId("order2")))
                .expectNoCommands()
                .andThen()
                .whenExecuting(f -> {
                    var tracker = Fluxzero.<OrderBackorderTracker>loadEntityValue("order2");
                    assertNotNull(tracker);
                    assertEquals(1, tracker.arrivedItems().size());
                    assertTrue(tracker.arrivedItems().contains(new ItemId("gloomhaven")));
                });
    }

    @Test
    void trackerDeletedAfterAllItemsArrived() {
        fixture.givenCommands("/orders/place-order-double.json")
                .whenEvent(new BackorderedItemArrived(new ItemId("gloomhaven"), new OrderId("order2")))
                .andThen()
                .whenEvent(new BackorderedItemArrived(new ItemId("ark-nova"), new OrderId("order2")))
                .expectCommands(SendShipment.class)
                .andThen()
                .whenExecuting(f -> {
                    var tracker = Fluxzero.<OrderBackorderTracker>loadEntityValue("order2");
                    assertNull(tracker);
                });
    }

    @Test
    void singleItemOrderShipsImmediately() {
        fixture.givenCommands("/orders/place-order-single.json")
                .whenEvent(new BackorderedItemArrived(new ItemId("gloomhaven"), new OrderId("order1")))
                .expectCommands(SendShipment.class)
                .andThen()
                .whenExecuting(f -> {
                    var tracker = Fluxzero.<OrderBackorderTracker>loadEntityValue("order1");
                    assertNull(tracker);
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
