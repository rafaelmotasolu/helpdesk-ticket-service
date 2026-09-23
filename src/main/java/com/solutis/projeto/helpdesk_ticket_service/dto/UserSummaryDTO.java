package com.solutis.projeto.helpdesk_ticket_service.dto;

public record UserSummaryDTO(
        Long id,
        String name,
        String email,
        String role,
        Boolean userEnabled
) {
}

