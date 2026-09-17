package com.solutis.projeto.helpdesk_ticket_service.dto;

import jakarta.validation.constraints.NotNull;

public record TicketAssignDTO(
    @NotNull(message = "O ID do técnico é obrigatório para atribuição")
    Long technicianId
) {}
