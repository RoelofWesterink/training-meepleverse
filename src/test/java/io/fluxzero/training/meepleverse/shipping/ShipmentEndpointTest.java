package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.Addressee;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentEndpointTest {

    final Addressee addressee = new Addressee("Alice", "Main St", "1", "12345",
            "Meepleville", "Netherlands", "alice@example.test", "");
    final ItemId gloomhaven = new ItemId("gloomhaven");
    final ItemId arkNova = new ItemId("ark-nova");
    final ItemId wingspan = new ItemId("wingspan");

    final TestFixture fixture = TestFixture.create(new ShipmentEndpoint());

    @Test
    void findShipmentContainingItem() {
        var shipmentId1 = new ShipmentId("shipment-1");
        var shipmentId2 = new ShipmentId("shipment-2");
        var shipment1 = new ShipmentSent(shipmentId1,
                new ShipmentDetails(List.of(gloomhaven, arkNova), new OrderId("order-1"), addressee));
        var shipment2 = new ShipmentSent(shipmentId2,
                new ShipmentDetails(List.of(wingspan), new OrderId("order-2"), addressee));

        fixture.givenAppliedEvents(shipmentId1, shipment1)
                .givenAppliedEvents(shipmentId2, shipment2)
                .whenGet("/shipments?itemId=gloomhaven")
                .expectWebResult(response -> {
                    Shipment[] results = response.getPayloadAs(Shipment[].class);
                    assertEquals(1, results.length);
                    assertEquals("shipment-1", results[0].shipmentId().toString());
                    assertTrue(results[0].details().itemIds().contains(gloomhaven));
                    return true;
                });
    }

    @Test
    void noResultsWhenItemNotShipped() {
        var shipmentId = new ShipmentId("shipment-1");
        var shipment = new ShipmentSent(shipmentId,
                new ShipmentDetails(List.of(gloomhaven), new OrderId("order-1"), addressee));

        fixture.givenAppliedEvents(shipmentId, shipment)
                .whenGet("/shipments?itemId=wingspan")
                .expectWebResult(response -> {
                    Shipment[] results = response.getPayloadAs(Shipment[].class);
                    assertEquals(0, results.length);
                    return true;
                });
    }

    @Test
    void findsMultipleShipmentsContainingSameItem() {
        var shipmentId1 = new ShipmentId("shipment-1");
        var shipmentId2 = new ShipmentId("shipment-2");
        var shipment1 = new ShipmentSent(shipmentId1,
                new ShipmentDetails(List.of(gloomhaven), new OrderId("order-1"), addressee));
        var shipment2 = new ShipmentSent(shipmentId2,
                new ShipmentDetails(List.of(gloomhaven, arkNova), new OrderId("order-2"), addressee));

        fixture.givenAppliedEvents(shipmentId1, shipment1)
                .givenAppliedEvents(shipmentId2, shipment2)
                .whenGet("/shipments?itemId=gloomhaven")
                .expectWebResult(response -> {
                    Shipment[] results = response.getPayloadAs(Shipment[].class);
                    assertEquals(2, results.length);
                    return true;
                });
    }
}
