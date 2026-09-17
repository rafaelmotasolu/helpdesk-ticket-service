package com.solutis.projeto.helpdesk_ticket_service.event;

import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public record TicketStatusChangedEvent(
    Long ticketId,
    String ticketTitle,
    TicketStatus oldStatus,
    TicketStatus newStatus,
    Long customerId,
    Long technicianId,
    LocalDateTime occurredOn
) implements Serializable {
    public TicketStatusChangedEvent(Long ticketId, String ticketTitle, TicketStatus oldStatus,
                                    TicketStatus newStatus, Long customerId, Long technicianId) {
        this(ticketId, ticketTitle, oldStatus, newStatus, customerId, technicianId, LocalDateTime.now());
    }
}