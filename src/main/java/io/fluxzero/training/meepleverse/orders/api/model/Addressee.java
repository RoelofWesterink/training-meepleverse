package io.fluxzero.training.meepleverse.orders.api.model;

import jakarta.validation.constraints.NotBlank;

public record Addressee(
    @NotBlank String fullName,
    @NotBlank String street,
    @NotBlank String houseNumber,
    @NotBlank String postalCode,
    @NotBlank String city,
    @NotBlank String country,
    @NotBlank String email,
    String phone
) {}