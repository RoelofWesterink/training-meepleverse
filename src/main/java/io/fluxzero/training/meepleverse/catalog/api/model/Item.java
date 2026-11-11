package io.fluxzero.training.meepleverse.catalog.api.model;

import io.fluxzero.common.search.Facet;
import io.fluxzero.common.serialization.Revision;
import io.fluxzero.sdk.modeling.Aggregate;
import io.fluxzero.sdk.modeling.EntityId;

import static io.fluxzero.sdk.modeling.EventPublication.IF_MODIFIED;

@Revision(1)
@Aggregate(searchable = true, timestampPath = "details.releaseDate", eventPublication = IF_MODIFIED)
public record Item(@EntityId ItemId itemId, @Facet ItemDetails details) {
}
