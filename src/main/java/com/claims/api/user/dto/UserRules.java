package com.claims.api.user.dto;

/** Shared account rules, used by validation annotations. */
public final class UserRules {

    public static final String USERNAME_REGEX = "^[A-Za-z0-9._-]{3,50}$";
    public static final String USERNAME_MESSAGE =
            "username must be 3-50 characters: letters, digits, dot, underscore or hyphen";

    /** NIST SP 800-63B favors length over complexity rules. */
    public static final int MIN_PASSWORD = 12;
    /** BCrypt only uses the first 72 bytes; the service also checks the UTF-8 byte length. */
    public static final int MAX_PASSWORD = 72;
    public static final String PASSWORD_MESSAGE = "password must be 12-72 characters";

    private UserRules() {
    }
}
