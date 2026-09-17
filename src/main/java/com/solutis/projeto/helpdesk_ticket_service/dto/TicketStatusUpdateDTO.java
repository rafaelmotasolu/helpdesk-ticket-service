package com.solutis.projeto.helpdesk_ticket_service.dto;

import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusUpdateDTO(
    @NotNull(message = "O novo status é obrigatório")
    TicketStatus status
) {}
