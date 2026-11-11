package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.Message;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.*;
import io.fluxzero.training.meepleverse.orders.view.OrderProgressProjection;
import io.fluxzero.training.meepleverse.orders.view.OrderStatus;
import io.fluxzero.training.meepleverse.orders.view.OrderObservation;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.shipping.api.ShippingFailed;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import io.fluxzero.training.meepleverse.shop.ShopEndpoint;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShopOrderTest {
    final OrderId orderId = new OrderId("shop-order");
    final ItemId first = new ItemId("ark-nova"), second = new ItemId("wingspan");
    final PaymentId paymentId = new PaymentId("shop-payment");
    final Addressee addressee = new Addressee("Alice Example", "Main Street", "42A", "12345",
            "Meepleville", "Netherlands", "alice@example.com", "");
    final OrderDetails details = new OrderDetails(List.of(first, second), paymentId, addressee);
    final PlaceOrder placed = new PlaceOrder(orderId, details);

    TestFixture fixture() {
        return TestFixture.create(new OrdersEndpoint(), new OrderProgressProjection());
    }

    @Test
    void receiptIdentifiesTheOrderWithAllRequestDetails() {
        fixture().whenPost("/orders", details).expectWebResult(response -> {
            assertEquals(200, response.getStatus());
            var receipt = response.<OrdersEndpoint.OrderReceipt>getPayloadAs(OrdersEndpoint.OrderReceipt.class);
            assertNotNull(receipt.orderId());
            assertEquals(details, Fluxzero.loadAggregate(receipt.orderId()).get().details());
            return true;
        });
    }

    @Test
    void unknownOrderIs404() {
        fixture().whenGet("/orders/missing").expectWebResult(response -> response.getStatus() == 404);
    }

    @Test
    void existingOrderWithoutProjectionIsPending() {
        TestFixture.create(new OrdersEndpoint()).givenCommands(placed)
                .whenGet("/orders/shop-order").expectWebResult(response -> {
                    var status = response.<OrderStatus>getPayloadAs(OrderStatus.class);
                    assertEquals(orderId, status.orderId());
                    assertTrue(status.projectionPending());
                    assertEquals("PENDING", status.paymentStatus());
                    assertFalse(status.aborted());
                    assertTrue(status.shipments().isEmpty());
                    return true;
                });
    }

    @Test
    void paymentUsesActualPaymentStateNotUnusedOrderPaidFlag() {
        fixture().givenCommands(placed)
                .givenAppliedEvents(paymentId, new PaymentAccepted(paymentId, orderId.toString()))
                .whenGet("/orders/shop-order").expectWebResult(response -> {
                    assertEquals("ACCEPTED", response.<OrderStatus>getPayloadAs(OrderStatus.class).paymentStatus());
                    assertFalse(Fluxzero.loadAggregate(orderId).get().paid());
                    return true;
                });
    }

    @Test
    void repeatedArrivalAndFullHistoryReplayDoNotDuplicateProgress() {
        var arrival = new BackorderedItemArrived(first, orderId);
        var shipment = new ShipmentSent(new ShipmentId("shipment-a"), new ShipmentDetails(List.of(first), orderId, addressee));
        fixture().givenCommands(placed).givenEvents(arrival, shipment)
                .whenExecuting(f -> {
                    Fluxzero.publishEvent(placed);
                    Fluxzero.publishEvent(arrival);
                    Fluxzero.publishEvent(shipment);
                })
                .expectNoCommands().expectNoErrors().andThen()
                .whenGet("/orders/shop-order").expectWebResult(response -> {
                    var status = response.<OrderStatus>getPayloadAs(OrderStatus.class);
                    assertEquals(3, status.observations().size());
                    assertEquals(1, status.shipments().size());
                    assertEquals(List.of(first), status.shipments().getFirst().itemIds());
                    assertEquals("RECORDED", status.shipments().getFirst().status());
                    assertFalse(status.projectionPending());
                    return true;
                });
    }

    @Test
    void shippingFailureAndAbortReasonRemainVisible() {
        var shipmentId = new ShipmentId("shipment-b");
        fixture().givenCommands(placed)
                .givenEvents(new ShipmentSent(shipmentId, new ShipmentDetails(List.of(first), orderId, addressee)),
                             new ShippingFailed(shipmentId, orderId))
                .givenCommands(new AbortOrder(orderId, "Shipment failed"))
                .whenGet("/orders/shop-order").expectWebResult(response -> {
                    var status = response.<OrderStatus>getPayloadAs(OrderStatus.class);
                    assertTrue(status.aborted());
                    assertEquals("Shipment failed", status.abortReason());
                    assertEquals("FAILED", status.shipments().getFirst().status());
                    assertEquals(List.of(first, second), status.itemIds());
                    return true;
                });
    }

    @Test
    void oneOrdersObservationsCannotLeakIntoAnother() {
        fixture().givenCommands(placed).givenEvents(new BackorderedItemArrived(first, new OrderId("another-order")))
                .whenGet("/orders/shop-order").expectWebResult(response -> {
                    assertTrue(response.<OrderStatus>getPayloadAs(OrderStatus.class).observations()
                            .stream().allMatch(o -> o.orderId().equals(orderId)));
                    return true;
                });
    }

    @Test
    void shipmentFailureRemainsVisibleWhenTheCommandRolledBackShipmentSent() {
        var shipmentId = new ShipmentId("failed-without-record");
        fixture().givenCommands(placed).givenEvents(new ShippingFailed(shipmentId, orderId))
                .whenGet("/orders/shop-order").expectWebResult(response -> {
                    var shipment = response.<OrderStatus>getPayloadAs(OrderStatus.class).shipments().getFirst();
                    assertEquals(shipmentId, shipment.shipmentId());
                    assertEquals("FAILED", shipment.status());
                    assertTrue(shipment.itemIds().isEmpty());
                    return true;
                });
    }

    @Test
    void servesShopAndStillRoutesTheOrderApi() {
        TestFixture.create(new ShopEndpoint(), new OrdersEndpoint())
                .whenGet("/").expectWebResult(response -> response.getStatus() == 302
                        && response.getHeaders().get("Location").equals(List.of("/shop/")))
                .andThen()
                .whenGet("/shop/").expectWebResult(response -> response.getStatus() == 200)
                .andThen().whenGet("/orders/missing").expectWebResult(response -> response.getStatus() == 404);
    }

    @Test
    void timelineUsesTheOriginalEventTimestamp() {
        var timestamp = Instant.parse("2025-10-23T09:15:00Z");
        var arrival = new Message(new BackorderedItemArrived(first, orderId)).withTimestamp(timestamp);
        fixture().givenCommands(placed).givenEvents(arrival)
                .whenGet("/orders/shop-order").expectWebResult(response -> {
                    var status = response.<OrderStatus>getPayloadAs(OrderStatus.class);
                    var observation = status.observations().stream()
                            .filter(o -> o.kind() == OrderObservation.Kind.ITEM_ARRIVED).findFirst().orElseThrow();
                    assertEquals(timestamp, observation.at());
                    return true;
                });
    }
}
