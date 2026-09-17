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
                                                   Long technicianId) {
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

            return predicate;
        };
    }
}
