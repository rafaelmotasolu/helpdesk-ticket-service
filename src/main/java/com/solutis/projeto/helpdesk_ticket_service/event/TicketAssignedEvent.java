package com.solutis.projeto.helpdesk_ticket_service.event;

import java.io.Serializable;
import java.time.LocalDateTime;

public record TicketAssignedEvent(
    Long ticketId,
    String ticketTitle,
    Long customerId,
    Long technicianId,
    LocalDateTime occurredOn
) implements Serializable {
    public TicketAssignedEvent(Long ticketId, String ticketTitle, Long customerId, Long technicianId) {
        this(ticketId, ticketTitle, customerId, technicianId, LocalDateTime.now());
    }
}