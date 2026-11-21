package com.claims.api.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.claims.api.audit.AuditService;
import com.claims.api.user.AppUser;
import com.claims.api.user.AppUserRepository;
import com.claims.api.user.Role;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminRunnerTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private AuditService audit;

    @Test
    @DisplayName("creates bootstrap admin when no ADMIN exists yet")
    void createsAdminWhenNoneExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("rootadmin")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                new BootstrapAdminProperties("rootadmin", "very-strong-admin-password", "Root Admin"),
                users, new BCryptPasswordEncoder(), audit);

        runner.run(null);

        verify(users).save(any(AppUser.class));
        verify(audit).recordAs(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("does nothing when an ADMIN already exists")
    void skipsWhenAdminAlreadyExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                new BootstrapAdminProperties("rootadmin", "very-strong-admin-password", "Root Admin"),
                users, new BCryptPasswordEncoder(), audit);

        runner.run(null);

        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("rejects short bootstrap passwords")
    void rejectsShortPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                new BootstrapAdminProperties("rootadmin", "short", "Root Admin"),
                users, new BCryptPasswordEncoder(), audit);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12");
    }
}
