package com.solutis.projeto.helpdesk_ticket_service.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnUserIdWhenAuthenticationIsValid() {
        var auth = new UsernamePasswordAuthenticationToken("42", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals(42L, SecurityUtils.getCurrentUserId());
    }

    @Test
    void shouldReturnNullUserIdWhenAuthenticationIsMissingOrInvalid() {
        assertNull(SecurityUtils.getCurrentUserId());

        var auth = new UsernamePasswordAuthenticationToken("not_a_number", "pass", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertNull(SecurityUtils.getCurrentUserId());
    }

    @Test
    void shouldCheckHasRoleWithOrWithoutPrefix() {
        var auth = new UsernamePasswordAuthenticationToken("1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(SecurityUtils.hasRole("CLIENT"));
        assertTrue(SecurityUtils.hasRole("ROLE_CLIENT"));
        assertTrue(SecurityUtils.isClient());
        assertFalse(SecurityUtils.isAdmin());
        assertFalse(SecurityUtils.isTechnician());
    }

    @Test
    void shouldReturnFalseWhenNoAuthentication() {
        assertFalse(SecurityUtils.hasRole("ADMIN"));
        assertFalse(SecurityUtils.isAdmin());
        assertFalse(SecurityUtils.isTechnician());
        assertFalse(SecurityUtils.isClient());
    }
}
