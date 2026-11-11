package io.fluxzero.training.meepleverse.shipping;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.common.serialization.casting.Upcast;

import java.util.List;

public class ShipmentUpcaster {

    @Upcast(type = "ShipmentSent", revision = 0)
    ObjectNode upcastShipmentSent(ObjectNode node) {
        return upcastDetails(node);
    }

    @Upcast(type = "Shipment", revision = 0)
    ObjectNode upcastShipment(ObjectNode node) {
        return upcastDetails(node);
    }

    private ObjectNode upcastDetails(ObjectNode node) {
        ObjectNode details = (ObjectNode) node.get("details");
        if (details != null && details.has("itemId")) {
            ArrayNode itemIds = details.putArray("itemIds");
            itemIds.add(details.remove("itemId"));
        }
        return node;
    }
}
