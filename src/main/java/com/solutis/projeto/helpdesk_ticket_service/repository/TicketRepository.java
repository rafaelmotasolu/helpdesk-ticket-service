package com.solutis.projeto.helpdesk_ticket_service.repository;

import com.solutis.projeto.helpdesk_ticket_service.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    List<Ticket> findByCustomerId(Long customerId);
    List<Ticket> findByTechnicianId(Long technicianId);
}