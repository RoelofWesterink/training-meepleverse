package io.fluxzero.training.meepleverse.catalog;

import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;
import org.springframework.stereotype.Component;

@Component
@Consumer(name = "item-facets-2", minIndex = 0)
public class ReindexItems {
    @HandleDocument
    Item migrate(Item item) {
        return item;
    }
}
