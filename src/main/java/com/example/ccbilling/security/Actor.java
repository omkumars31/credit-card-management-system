package com.example.ccbilling.security;

import com.example.ccbilling.domain.ActorType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who is performing the current operation (SYSTEM when there is no authenticated user). */
public record Actor(ActorType type, Long id, String name) {

    public static Actor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
            return new Actor(p.getUserType(), p.getId(), p.getLoginName());
        }
        return new Actor(ActorType.SYSTEM, null, "system");
    }

    public String label() {
        return type.name() + ":" + name;
    }
}
