package io.fluxzero.training.meepleverse.user.api.model;

import io.fluxzero.training.meepleverse.authentication.Role;
import io.fluxzero.training.meepleverse.user.api.UserId;
import io.fluxzero.sdk.modeling.Aggregate;
import io.fluxzero.sdk.modeling.EventPublication;
import lombok.Builder;

@Aggregate(searchable = true, eventPublication = EventPublication.IF_MODIFIED)
@Builder(toBuilder = true)
public record UserProfile(UserId userId, UserDetails details, Role role) {
}
