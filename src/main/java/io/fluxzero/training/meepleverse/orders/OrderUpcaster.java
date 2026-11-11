package io.fluxzero.training.meepleverse.orders;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.common.serialization.casting.Upcast;
import org.springframework.stereotype.Component;

@Component
public class OrderUpcaster {

    @Upcast(type = "io.fluxzero.training.meepleverse.orders.api.model.Order", revision = 0)
    ObjectNode upcastOrder(ObjectNode payload) {
        if (!payload.has("arrivedItemIds")) {
            ArrayNode arrivedItemIds = JsonNodeFactory.instance.arrayNode();
            payload.set("arrivedItemIds", arrivedItemIds);
        }
        return payload;
    }
}
