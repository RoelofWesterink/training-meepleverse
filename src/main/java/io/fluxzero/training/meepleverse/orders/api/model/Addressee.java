package io.fluxzero.training.meepleverse.orders.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Addressee(
    @NotBlank @Size(max = 200, groups = ProfileEdit.class) String fullName,
    @NotBlank @Size(max = 200, groups = ProfileEdit.class) String street,
    @NotBlank @Size(max = 200, groups = ProfileEdit.class) String houseNumber,
    @NotBlank @Size(max = 200, groups = ProfileEdit.class) String postalCode,
    @NotBlank @Size(max = 200, groups = ProfileEdit.class) String city,
    @NotBlank @Size(max = 200, groups = ProfileEdit.class) String country,
    @NotBlank @Size(max = 200, groups = ProfileEdit.class) String email,
    @Size(max = 200, groups = ProfileEdit.class) String phone
) {
    public interface ProfileEdit {}
}
