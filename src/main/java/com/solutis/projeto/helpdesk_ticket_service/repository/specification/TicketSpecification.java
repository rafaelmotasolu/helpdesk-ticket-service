package com.solutis.projeto.helpdesk_ticket_service.repository.specification;

import com.solutis.projeto.helpdesk_ticket_service.entity.Ticket;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;
import org.springframework.data.jpa.domain.Specification;

public class TicketSpecification {

    public static Specification<Ticket> withFilters(TicketStatus status,
                                                   TicketPriority priority,
                                                   TicketCategory category,
                                                   Long customerId,
                                                   Long technicianId,
                                                   String enabledFilter) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();

            if (status != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("priority"), priority));
            }
            if (category != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("category"), category));
            }
            if (customerId != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("customerId"), customerId));
            }
            if (technicianId != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("technicianId"), technicianId));
            }

            // Filtro por ticketEnabled: "ativados" (padrão), "desativados" ou "todos"
            if (enabledFilter != null && !enabledFilter.isBlank()) {
                String normalized = enabledFilter.trim().toLowerCase();
                if (normalized.equals("desativados") || normalized.equals("false") || normalized.equals("disabled")) {
                    predicate = criteriaBuilder.and(predicate, criteriaBuilder.isFalse(root.get("ticketEnabled")));
                } else if (normalized.equals("todos") || normalized.equals("all")) {
                    // Retorna todos (sem filtro de ticketEnabled)
                } else {
                    // Padrão "ativados"
                    predicate = criteriaBuilder.and(predicate, criteriaBuilder.isTrue(root.get("ticketEnabled")));
                }
            } else {
                // Padrão quando não informado: "ativados"
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.isTrue(root.get("ticketEnabled")));
            }

            return predicate;
        };
    }
}
