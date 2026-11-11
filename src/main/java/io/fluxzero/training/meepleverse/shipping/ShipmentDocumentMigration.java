package io.fluxzero.training.meepleverse.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import org.springframework.stereotype.Component;

/**
 * One-time migration: transforms old Shipment documents from
 * {@code details.itemId} (singular) to {@code details.itemIds} (list).
 * 
 * SDK 1.x aggregate documents lack type/revision metadata, so upcasters
 * don't run. We transform the raw JSON directly instead.
 * 
 * Remove this class after the migration completes.
 */
@Consumer(name = "shipment-schema-migration-4", minIndex = 0)
@Component
public class ShipmentDocumentMigration {

    @HandleDocument("Shipment")
    ObjectNode migrate(ObjectNode document) {
        ObjectNode details = (ObjectNode) document.get("details");
        if (details != null && details.has("itemId") && !details.has("itemIds")) {
            JsonNode itemId = details.remove("itemId");
            ArrayNode itemIds = JsonNodeFactory.instance.arrayNode();
            itemIds.add(itemId);
            details.set("itemIds", itemIds);
            return document;
        }
        return null; // Already migrated, skip
    }
}
