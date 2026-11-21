package com.claims.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.claims.api.audit.AuditAction;
import com.claims.api.audit.AuditService;
import com.claims.api.user.AppUser;
import com.claims.api.user.AppUserRepository;
import com.claims.api.user.Role;

/**
 * Solves the "who creates the first admin?" problem in a fresh production database
 * (which has no demo users). If BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD are set
 * and no ADMIN exists, one is created on startup. Otherwise this does nothing.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminRunner implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public BootstrapAdminRunner(BootstrapAdminProperties properties, AppUserRepository users,
                                PasswordEncoder encoder, AuditService audit) {
        this.properties = properties;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.username();
        String password = properties.password();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            if (!users.existsByRole(Role.ADMIN)) {
                log.warn("No ADMIN account exists. Set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD "
                        + "to create one on the next start.");
            }
            return;
        }
        if (users.existsByRole(Role.ADMIN)) {
            log.info("An ADMIN account already exists; ignoring BOOTSTRAP_ADMIN_* (you can remove them now).");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least "
                    + MIN_PASSWORD_LENGTH + " characters");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME '" + username + "' is already taken by a non-admin");
        }
        String fullName = properties.fullName() == null || properties.fullName().isBlank()
                ? "Administrator" : properties.fullName();
        AppUser admin = users.save(new AppUser(username.trim(), encoder.encode(password), Role.ADMIN, fullName));
        audit.recordAs("system", AuditAction.USER_CREATED, "USER", admin.getId(),
                "Bootstrap admin '" + admin.getUsername() + "' created from environment");
        log.info("Created bootstrap ADMIN account '{}'. Remove BOOTSTRAP_ADMIN_* from the environment.",
                admin.getUsername());
    }
}
