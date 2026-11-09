package io.fluxzero.training.meepleverse.catalog.api.model;

import io.fluxzero.sdk.modeling.Id;

public class ItemId extends Id<Item> {
    public ItemId(String functionalId) {
        super(functionalId);
    }
}
