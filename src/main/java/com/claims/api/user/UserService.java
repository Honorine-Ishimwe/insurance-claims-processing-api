package com.claims.api.user;

import java.nio.charset.StandardCharsets;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claims.api.audit.AuditAction;
import com.claims.api.audit.AuditService;
import com.claims.api.common.BadRequestException;
import com.claims.api.common.ConflictException;
import com.claims.api.common.NotFoundException;
import com.claims.api.user.dto.CreateUserRequest;
import com.claims.api.user.dto.RegisterRequest;
import com.claims.api.user.dto.UserResponse;
import com.claims.api.user.dto.UserRules;

/** Account management: public member registration and admin user administration. */
@Service
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(AppUserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** Public sign-up. Always a MEMBER: nobody can self-register as staff. */
    public UserResponse register(RegisterRequest request) {
        AppUser user = create(request.username(), request.password(), request.fullName(), Role.MEMBER);
        audit.recordAs(user.getUsername(), AuditAction.MEMBER_REGISTERED, "USER", user.getId(),
                "Self-registered member account");
        return UserResponse.from(user);
    }

    /** Admin creates an account of any role. */
    public UserResponse createByAdmin(CreateUserRequest request) {
        AppUser user = create(request.username(), request.password(), request.fullName(), request.role());
        audit.record(AuditAction.USER_CREATED, "USER", user.getId(),
                "Created " + user.getRole() + " account '" + user.getUsername() + "'");
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Role role, Pageable pageable) {
        Page<AppUser> page = role == null ? users.findAll(pageable) : users.findByRole(role, pageable);
        return page.map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    /** Disabling takes effect immediately, including for tokens already issued. */
    public UserResponse setEnabled(Long id, boolean enabled, String actingUsername) {
        AppUser user = find(id);
        if (!enabled && user.getUsername().equals(actingUsername)) {
            throw new BadRequestException("You cannot disable your own account");
        }
        if (user.isEnabled() != enabled) {
            user.setEnabled(enabled);
            audit.record(enabled ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED, "USER", user.getId(),
                    (enabled ? "Enabled" : "Disabled") + " account '" + user.getUsername() + "'");
        }
        return UserResponse.from(user);
    }

    private AppUser create(String rawUsername, String password, String fullName, Role role) {
        String username = rawUsername.trim();
        if (password.getBytes(StandardCharsets.UTF_8).length > UserRules.MAX_PASSWORD) {
            throw new BadRequestException("password is too long (maximum 72 bytes)");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }
        return users.save(new AppUser(username, encoder.encode(password), role, fullName.trim()));
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
