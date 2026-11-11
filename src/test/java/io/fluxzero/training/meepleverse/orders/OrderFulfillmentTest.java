package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.training.meepleverse.catalog.CatalogHandler;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.payments.api.PaymentRejected;
import io.fluxzero.training.meepleverse.payments.api.ValidatePayment;
import io.fluxzero.training.meepleverse.shipping.api.SendShipment;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.shipping.api.ShippingFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderItems;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

class OrderFulfillmentTest {
    final TestFixture fixture = TestFixture.create(CatalogHandler.class, OrderFulfillment.class)
            .givenCommands("/catalog/register-items.json");

    @Nested
    class SuccessScenarios {

        @BeforeEach
        void setUp() {
            fixture.registerHandlers(new ExternalsMock());
        }

        @Test
        void placeOrderSingle() {
            fixture.whenCommand("/orders/place-order-single.json")
                    .expectEvents("/orders/place-order-single.json")
                    .expectCommands(ValidatePayment.class)
                    .expectEvents(PaymentAccepted.class)
                    .expectCommands("/supply/backorder-order1.json");
        }

        @Test
        void shipAfterBackorderArrival() {
            // Single item order - shipment should happen after the one item arrives
            fixture.givenCommands("/orders/place-order-single.json")
                    .whenCommand(new BackorderedItemArrived(new OrderId("order1"), new ItemId("gloomhaven")))
                    .expectEvents(BackorderedItemArrived.class, ShipmentSent.class);
        }

        @Test
        void shipTwoItemsOnlyAfterBothArrive() {
            // Two item order - shipment should only happen after BOTH items arrive
            var orderId = new OrderId("order2");
            var gloomhaven = new ItemId("gloomhaven");
            var arkNova = new ItemId("ark-nova");
            
            fixture.givenCommands("/orders/place-order-double.json")
                    // First item arrives - no shipment yet
                    .whenCommand(new BackorderedItemArrived(orderId, gloomhaven))
                    .expectEvents(BackorderedItemArrived.class)
                    .andThen()
                    // Second item arrives - now shipment with both items
                    .whenCommand(new BackorderedItemArrived(orderId, arkNova))
                    .expectEvents(BackorderedItemArrived.class, ShipmentSent.class);
        }
    }

    @Test
    void invalidPayment() {
        fixture.registerHandlers(new ExternalsMock() {
            @Override
            String validate(String paymentId) {
                return JsonUtils.asJson(Map.of("accepted", false));
            }
        }).whenCommand("/orders/place-order-single.json")
                .expectEvents(PaymentRejected.class, AbortOrder.class);
    }

    @Test
    void failingSupplier() {
        fixture.registerHandlers(new ExternalsMock() {
                    @Override
                    void backorder(BackorderItems body) {
                        throw new IllegalCommandException("Item not found");
                    }
                }).whenCommand("/orders/place-order-single.json")
                .expectEvents(PaymentAccepted.class, BackorderFailed.class, AbortOrder.class);
    }

    @Test
    void failingShipper() {
        fixture.registerHandlers(new ExternalsMock() {
                    @Override
                    void ship(SendShipment body) {
                        throw new IllegalCommandException("I went bankrupt");
                    }
                }).givenCommands("/orders/place-order-single.json")
                .whenCommand(new BackorderedItemArrived(new OrderId("order1"), new ItemId("gloomhaven")))
                .expectEvents(BackorderedItemArrived.class, ShipmentSent.class, ShippingFailed.class)
                .expectCommands(AbortOrder.class);
    }

    static class ExternalsMock {
        @HandlePost("/psp/validate/{paymentId}")
        String validate(String paymentId) {
            return JsonUtils.asJson(Map.of("accepted", true));
        }

        @HandlePost("/supplier/order")
        void backorder(BackorderItems body) {
            //no op
        }

        @HandlePost("/shipper/send")
        void ship(SendShipment body) {
            //no op
        }
    }

}