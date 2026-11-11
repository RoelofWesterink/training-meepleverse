package io.fluxzero.training.meepleverse.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.common.serialization.casting.Upcast;
import org.springframework.stereotype.Component;

/**
 * Upcasts old shipment schema from {@code itemId} (singular) to {@code itemIds} (list).
 * 
 * Upcasters only run on top-level types, so we need one for each:
 * - {@code Shipment} (the searchable aggregate document)
 * - {@code ShipmentSent} (the event)
 */
@Component
public class ShipmentUpcaster {

    @Upcast(type = "io.fluxzero.training.meepleverse.shipping.api.model.Shipment", revision = 0)
    ObjectNode upcastShipment(ObjectNode payload) {
        upcastDetails(payload.withObject("/details"));
        return payload;
    }

    @Upcast(type = "io.fluxzero.training.meepleverse.shipping.api.ShipmentSent", revision = 0)
    ObjectNode upcastShipmentSent(ObjectNode payload) {
        upcastDetails(payload.withObject("/details"));
        return payload;
    }

    private void upcastDetails(ObjectNode details) {
        JsonNode itemId = details.remove("itemId");
        if (itemId != null) {
            ArrayNode itemIds = JsonNodeFactory.instance.arrayNode();
            itemIds.add(itemId);
            details.set("itemIds", itemIds);
        }
    }
}
