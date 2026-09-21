package com.solutis.projeto.helpdesk_ticket_service.event;

import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public record TicketCreatedEvent(
    Long ticketId,
    String title,
    TicketPriority priority,
    TicketStatus status,
    TicketCategory category,
    Long customerId,
    boolean ticketEnabled,
    LocalDateTime occurredOn
) implements Serializable {
    public TicketCreatedEvent(Long ticketId, String title, TicketPriority priority, 
                              TicketStatus status, TicketCategory category, Long customerId, boolean ticketEnabled) {
        this(ticketId, title, priority, status, category, customerId, ticketEnabled, LocalDateTime.now());
    }

    public TicketCreatedEvent(Long ticketId, String title, TicketPriority priority, 
                              TicketStatus status, TicketCategory category, Long customerId) {
        this(ticketId, title, priority, status, category, customerId, true, LocalDateTime.now());
    }
}