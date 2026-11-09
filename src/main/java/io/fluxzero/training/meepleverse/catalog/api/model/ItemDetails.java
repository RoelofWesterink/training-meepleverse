package io.fluxzero.training.meepleverse.catalog.api.model;

import io.fluxzero.common.search.Facet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ItemDetails(@NotBlank String name, LocalDate releaseDate, @Facet String category, @PositiveOrZero BigDecimal price) {
}
