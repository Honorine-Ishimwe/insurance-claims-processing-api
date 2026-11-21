package com.claims.api.user.dto;

import com.claims.api.user.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin-created account of any role (e.g. a new adjuster). */
public record CreateUserRequest(
        @Schema(example = "adjuster2")
        @NotBlank(message = "username is required")
        @Pattern(regexp = UserRules.USERNAME_REGEX, message = UserRules.USERNAME_MESSAGE)
        String username,

        @NotBlank(message = "password is required")
        @Size(min = UserRules.MIN_PASSWORD, max = UserRules.MAX_PASSWORD, message = UserRules.PASSWORD_MESSAGE)
        String password,

        @Schema(example = "Sam Rivera")
        @NotBlank(message = "fullName is required")
        @Size(max = 200, message = "fullName must be at most 200 characters")
        String fullName,

        @Schema(example = "ADJUSTER")
        @NotNull(message = "role is required")
        Role role) {
}
