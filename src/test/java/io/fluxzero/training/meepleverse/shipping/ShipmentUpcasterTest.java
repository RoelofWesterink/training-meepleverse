package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import org.junit.jupiter.api.Test;

import java.util.List;

class ShipmentUpcasterTest {

    final TestFixture testFixture = TestFixture.create().registerCasters(ShipmentUpcaster.class);

    @Test
    void upcastShipmentSentRev0() {
        testFixture.whenUpcasting("/shipping/shipment-sent-rev0.json")
                .<ShipmentSent>expectResult(r -> r.details().itemIds().equals(List.of(new ItemId("ark-nova"))));
    }
}
