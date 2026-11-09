package io.fluxzero.training.meepleverse.orders.api.model;

import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderDetails(@NotEmpty List<ItemId> itemIds, PaymentId paymentId, Addressee addressee) {
}
