package io.fluxzero.training.meepleverse.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.common.serialization.casting.Upcast;
import org.springframework.stereotype.Component;

@Component
public class ShipmentDetailsUpcaster {

    @Upcast(type = "io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails", revision = 0)
    ObjectNode upcastShipmentDetails(ObjectNode payload) {
        JsonNode itemId = payload.remove("itemId");
        if (itemId != null) {
            ArrayNode itemIds = JsonNodeFactory.instance.arrayNode();
            itemIds.add(itemId);
            payload.set("itemIds", itemIds);
        }
        return payload;
    }
}
