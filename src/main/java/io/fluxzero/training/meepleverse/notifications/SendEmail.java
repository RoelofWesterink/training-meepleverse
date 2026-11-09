package io.fluxzero.training.meepleverse.notifications;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendEmail(@NotBlank @Email String email, @NotBlank String subject) {
}
