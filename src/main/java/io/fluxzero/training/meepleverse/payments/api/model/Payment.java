package io.fluxzero.training.meepleverse.payments.api.model;

import io.fluxzero.sdk.modeling.Aggregate;
import io.fluxzero.sdk.modeling.EntityId;

import static io.fluxzero.sdk.modeling.EventPublication.IF_MODIFIED;

@Aggregate(searchable = true, eventPublication = IF_MODIFIED)
public record Payment(@EntityId PaymentId paymentId, String reference, boolean accepted) {
}
