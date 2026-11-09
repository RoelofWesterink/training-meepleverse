package io.fluxzero.training.meepleverse.catalog.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemDetails;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RegisterItem(@NotNull ItemId itemId, @NotNull @Valid ItemDetails details) {

    @Apply
    Item apply() {
        return new Item(itemId, details);
    }

}
