package io.fluxzero.training.meepleverse.shipping.api.model;

import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.Addressee;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShipmentDetails(@NotEmpty List<ItemId> itemIds, @NotNull OrderId orderId, @NotNull @Valid Addressee addressee) {
}
