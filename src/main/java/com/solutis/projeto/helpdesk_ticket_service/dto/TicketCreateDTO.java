package com.solutis.projeto.helpdesk_ticket_service.dto;

import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketCreateDTO(
    @NotBlank(message = "O título é obrigatório")
    @Size(min = 5, max = 150, message = "O título deve ter entre 5 e 150 caracteres")
    String title,

    @NotBlank(message = "A descrição é obrigatória")
    @Size(min = 10, message = "A descrição deve conter no mínimo 10 caracteres")
    String description,

    @NotNull(message = "A categoria é obrigatória")
    TicketCategory category,

    @NotNull(message = "A prioridade é obrigatória")
    TicketPriority priority,

    @NotNull(message = "O identificador do cliente (customerId) é obrigatório")
    Long customerId
) {}
