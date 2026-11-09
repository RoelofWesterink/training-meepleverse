package io.fluxzero.training.meepleverse.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.common.Guarantee;
import io.fluxzero.common.api.search.SerializedDocument;
import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReindexShipmentsTest {
    @Test
    void rewritesTheExistingShipmentInPlace() {
        Instant timestamp = Instant.parse("2025-10-24T10:00:00Z");
        JsonNode fixture = JsonUtils.fromFile("/shipping/shipment-rev0.json", JsonNode.class);
        TestFixture.create(new ReindexShipments()).registerCasters(ShipmentUpcaster.class).whenExecuting(f -> {
            var old = f.documentStore().getSerializer()
                    .toDocument(fixture.get("value"), "1", "Shipment", timestamp, timestamp)
                    .deserializeDocument().toBuilder().type(Shipment.class.getName()).revision(0).build();
            f.client().getSearchClient().index(List.of(new SerializedDocument(old)), Guarantee.STORED, false).join();
        }).expectNoErrors().expectThat(f -> {
            var hits = Fluxzero.search(Shipment.class).match("ark-nova", "details/itemIds")
                    .streamHits(SerializedDocument.class).toList();
            assertEquals(1, hits.size());
            var stored = hits.getFirst().getValue();
            assertEquals("1", stored.getId());
            assertEquals(1, stored.getDocument().getRevision());
            assertEquals(timestamp, stored.deserializeDocument().getTimestamp());
            Shipment shipment = f.documentStore().getSerializer().fromDocument(stored);
            assertEquals(List.of(new ItemId("ark-nova")), shipment.details().itemIds());
            assertEquals("order2", shipment.details().orderId().toString());
        });
    }
}
