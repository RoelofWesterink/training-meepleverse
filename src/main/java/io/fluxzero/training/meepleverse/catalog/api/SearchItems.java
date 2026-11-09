package io.fluxzero.training.meepleverse.catalog.api;

import io.fluxzero.common.api.search.constraints.ContainsConstraint;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record SearchItems(String term, String category) implements Request<SearchItems.Result> {
    public SearchItems() {
        this(null);
    }

    public SearchItems(String term) {
        this(term, null);
    }

    public record Result(List<Item> items, Map<String, Integer> categories) {}

    @HandleQuery
    Result handle() {
        var search = Fluxzero.search(Item.class);
        if (term != null && !term.isBlank()) {
            search = search.constraint(ContainsConstraint.contains(term.strip(), true, true, "details/name"));
        }
        var matches = search.fetchAll(Item.class);
        Map<String, Integer> counts = new TreeMap<>();
        for (var item : matches) {
            var label = item.details().category();
            if (label != null && !label.isBlank()) {
                counts.merge(label, 1, Integer::sum);
            }
        }
        var items = category == null || category.isBlank() ? matches : matches.stream()
                .filter(item -> category.equals(item.details().category())).toList();
        return new Result(items, counts);
    }
}
