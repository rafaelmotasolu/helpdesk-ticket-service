package com.solutis.projeto.helpdesk_ticket_service.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || role == null) {
            return false;
        }
        String targetAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority != null && targetAuthority.equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public static boolean isTechnician() {
        return hasRole("TECHNICIAN");
    }

    public static boolean isClient() {
        return hasRole("CLIENT");
    }
}

