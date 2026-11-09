package io.fluxzero.training.meepleverse.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.training.meepleverse.catalog.api.RegisterItem;
import io.fluxzero.training.meepleverse.catalog.api.RegisterItems;
import org.springframework.stereotype.Component;

@Component
public class CatalogHandler {

    @HandleCommand
    void handle(RegisterItems items) {
        items.items().forEach(this::handle);
    }

    @HandleCommand
    void handle(RegisterItem command) {
        Fluxzero.loadAggregate(command.itemId()).assertAndApply(command);
    }

}
