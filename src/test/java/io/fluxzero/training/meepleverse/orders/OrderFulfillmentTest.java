package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.training.meepleverse.catalog.CatalogHandler;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.payments.api.PaymentRejected;
import io.fluxzero.training.meepleverse.payments.api.ValidatePayment;
import io.fluxzero.training.meepleverse.shipping.api.SendShipment;
import io.fluxzero.training.meepleverse.shipping.api.ShippingFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderItems;
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
            fixture.givenCommands("/orders/place-order-single.json")
                    .whenEvent("/supply/backordered-item-arrived-order1.json")
                    .expectEvents("/shipping/order1-shipped.json");
        }

        @Test
        void shipTwoItems() {
            fixture.givenCommands("/orders/place-order-double.json")
                    .whenEvent("/supply/backorder-arrived-order2-item-gloomhaven.json")
                    .expectEvents("/shipping/order2-shipped-gloomhaven.json")
                    .andThen()
                    .whenEvent("/supply/backorder-arrived-order2-item-ark-nova.json")
                    .expectEvents("/shipping/order2-shipped-ark-nova.json");
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
                .whenEvent("/supply/backordered-item-arrived-order1.json")
                .expectEvents(ShippingFailed.class, AbortOrder.class);
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