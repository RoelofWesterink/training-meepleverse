package io.fluxzero.training.meepleverse.catalog.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;

import java.util.List;

public record SearchItems(String term) implements Request<List<Item>> {

    public SearchItems() {
        this(null);
    }

    @HandleQuery
    List<Item> handle() {
        return Fluxzero.search(Item.class).lookAhead(term).fetch(100);
    }
}
