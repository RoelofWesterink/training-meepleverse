package io.fluxzero.training.meepleverse.catalog.api.model;

import io.fluxzero.sdk.modeling.Aggregate;
import io.fluxzero.sdk.modeling.EntityId;

import static io.fluxzero.sdk.modeling.EventPublication.IF_MODIFIED;

@Aggregate(searchable = true, timestampPath = "details.releaseDate", eventPublication = IF_MODIFIED)
public record Item(@EntityId ItemId itemId, ItemDetails details) {
}
