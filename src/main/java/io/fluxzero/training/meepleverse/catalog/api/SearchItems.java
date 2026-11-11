package io.fluxzero.training.meepleverse.catalog.api;

import io.fluxzero.common.api.search.FacetStats;
import io.fluxzero.common.api.search.constraints.ContainsConstraint;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.search.Search;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.stream.Collectors.toMap;

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
        Search search = Fluxzero.search(Item.class);
        if (term != null && !term.isBlank()) {
            search = search.constraint(ContainsConstraint.contains(term.strip(), true, true, "details/name"));
        }
        Map<String, Integer> counts = new TreeMap<>(search.facetStats().stream()
                .filter(f -> f.getValue() != null && !f.getValue().isBlank())
                .collect(toMap(FacetStats::getValue, FacetStats::getCount)));
        if (category != null && !category.isBlank()) {
            search = search.matchFacet("details/category", category);
        }
        return new Result(search.fetchAll(Item.class), counts);
    }
}
