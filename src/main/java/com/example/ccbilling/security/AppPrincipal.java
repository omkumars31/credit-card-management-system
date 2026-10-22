package com.example.ccbilling.security;

import com.example.ccbilling.domain.ActorType;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated user (customer or administrator). The password hash is erased after authentication. */
public class AppPrincipal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final ActorType userType;
    private final String loginName;
    private final String displayName;
    private final boolean enabled;
    private String passwordHash;

    public AppPrincipal(Long id, ActorType userType, String loginName, String displayName,
                        String passwordHash, boolean enabled) {
        this.id = id;
        this.userType = userType;
        this.loginName = loginName;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public Long getId() { return id; }

    public ActorType getUserType() { return userType; }

    public String getLoginName() { return loginName; }

    public String getDisplayName() { return displayName; }

    public boolean isAdmin() { return userType == ActorType.ADMIN; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userType.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    /** The Spring Security username is prefixed with the user type so both tables can share one login flow. */
    @Override
    public String getUsername() { return userType.name() + ":" + loginName; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void eraseCredentials() { this.passwordHash = null; }
}
