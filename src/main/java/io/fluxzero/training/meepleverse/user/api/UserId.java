package io.fluxzero.training.meepleverse.user.api;

import io.fluxzero.training.meepleverse.user.api.model.UserProfile;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Id;

public class UserId extends Id<UserProfile> {
    public UserId() {
        this(Fluxzero.generateId());
    }

    public UserId(String id) {
        super(id);
    }
}
