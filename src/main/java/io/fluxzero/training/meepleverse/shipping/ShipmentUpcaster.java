package io.fluxzero.training.meepleverse.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.common.serialization.casting.Upcast;
import org.springframework.stereotype.Component;

@Component
public class ShipmentUpcaster {

    @Upcast(type = "io.fluxzero.training.meepleverse.shipping.api.ShipmentSent", revision = 0)
    JsonNode upcastShipmentSent(ObjectNode payload) {
        ObjectNode details = payload.withObject("details");
        details.putArray("itemIds").add(details.remove("itemId"));
        return payload;
    }

}
