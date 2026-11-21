package com.claims.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public self-registration. Always creates a MEMBER account; an admin then links a policy to it. */
public record RegisterRequest(
        @Schema(example = "jdoe")
        @NotBlank(message = "username is required")
        @Pattern(regexp = UserRules.USERNAME_REGEX, message = UserRules.USERNAME_MESSAGE)
        String username,

        @Schema(example = "correct-horse-battery-staple")
        @NotBlank(message = "password is required")
        @Size(min = UserRules.MIN_PASSWORD, max = UserRules.MAX_PASSWORD, message = UserRules.PASSWORD_MESSAGE)
        String password,

        @Schema(example = "Jane Doe")
        @NotBlank(message = "fullName is required")
        @Size(max = 200, message = "fullName must be at most 200 characters")
        String fullName) {
}
