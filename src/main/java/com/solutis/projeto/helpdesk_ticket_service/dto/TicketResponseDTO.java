package com.solutis.projeto.helpdesk_ticket_service.dto;

import com.solutis.projeto.helpdesk_ticket_service.entity.Ticket;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;

import java.time.LocalDateTime;

public record TicketResponseDTO(
    Long id,
    String title,
    String description,
    TicketPriority priority,
    TicketStatus status,
    TicketCategory category,
    Long customerId,
    Long technicianId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static TicketResponseDTO fromEntity(Ticket ticket) {
        return new TicketResponseDTO(
            ticket.getId(),
            ticket.getTitle(),
            ticket.getDescription(),
            ticket.getPriority(),
            ticket.getStatus(),
            ticket.getCategory(),
            ticket.getCustomerId(),
            ticket.getTechnicianId(),
            ticket.getCreatedAt(),
            ticket.getUpdatedAt()
        );
    }
}