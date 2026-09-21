package com.solutis.projeto.helpdesk_ticket_service.service;

import com.solutis.projeto.helpdesk_ticket_service.config.RabbitMQConfig;
import com.solutis.projeto.helpdesk_ticket_service.dto.*;
import com.solutis.projeto.helpdesk_ticket_service.entity.Ticket;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;
import com.solutis.projeto.helpdesk_ticket_service.event.TicketAssignedEvent;
import com.solutis.projeto.helpdesk_ticket_service.event.TicketCreatedEvent;
import com.solutis.projeto.helpdesk_ticket_service.event.TicketStatusChangedEvent;
import com.solutis.projeto.helpdesk_ticket_service.exception.BusinessException;
import com.solutis.projeto.helpdesk_ticket_service.exception.ResourceNotFoundException;
import com.solutis.projeto.helpdesk_ticket_service.repository.TicketRepository;
import com.solutis.projeto.helpdesk_ticket_service.repository.specification.TicketSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final RabbitTemplate rabbitTemplate;

    public TicketService(TicketRepository ticketRepository, RabbitTemplate rabbitTemplate) {
        this.ticketRepository = ticketRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public TicketResponseDTO create(TicketCreateDTO dto) {
        Ticket ticket = new Ticket(
                dto.title(),
                dto.description(),
                dto.category(),
                dto.priority(),
                dto.customerId()
        );

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Ticket criado com sucesso com ID: {}", savedTicket.getId());

        // Publicar evento assíncrono: TicketCreated
        TicketCreatedEvent event = new TicketCreatedEvent(
                savedTicket.getId(),
                savedTicket.getTitle(),
                savedTicket.getPriority(),
                savedTicket.getStatus(),
                savedTicket.getCategory(),
                savedTicket.getCustomerId()
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TICKET_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_CREATED,
                event
        );

        return TicketResponseDTO.fromEntity(savedTicket);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponseDTO> findAll(TicketStatus status,
                                          TicketPriority priority,
                                          TicketCategory category,
                                          Long customerId,
                                          Long technicianId,
                                          String enabledFilter,
                                          Pageable pageable) {
        Specification<Ticket> spec = TicketSpecification.withFilters(status, priority, category, customerId, technicianId, enabledFilter);
        return ticketRepository.findAll(spec, pageable).map(TicketResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO findById(Long id) {
        Ticket ticket = findEntityById(id);
        return TicketResponseDTO.fromEntity(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> findByCustomerId(Long customerId) {
        return ticketRepository.findByCustomerId(customerId)
                .stream()
                .map(TicketResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public TicketResponseDTO update(Long id, TicketUpdateDTO dto) {
        Ticket ticket = findEntityById(id);

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessException("Chamados com status 'CLOSED' não podem ser alterados.");
        }

        ticket.setTitle(dto.title());
        ticket.setDescription(dto.description());
        ticket.setCategory(dto.category());
        ticket.setPriority(dto.priority());

        return TicketResponseDTO.fromEntity(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponseDTO assignTechnician(Long id, TicketAssignDTO dto) {
        Ticket ticket = findEntityById(id);

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessException("Não é permitido atribuir técnico a um chamado já encerrado.");
        }

        ticket.setTechnicianId(dto.technicianId());

        // Se o chamado estava apenas ABERTO, transita para EM ATENDIMENTO
        TicketStatus previousStatus = ticket.getStatus();
        if (previousStatus == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        log.info("Técnico {} atribuído ao chamado {}", dto.technicianId(), id);

        // 1. Publica evento: TicketAssigned
        TicketAssignedEvent assignedEvent = new TicketAssignedEvent(
                updatedTicket.getId(),
                updatedTicket.getTitle(),
                updatedTicket.getCustomerId(),
                updatedTicket.getTechnicianId()
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TICKET_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_ASSIGNED,
                assignedEvent
        );

        // 2. Se o status mudou para IN_PROGRESS, publica também o status changed
        if (previousStatus != updatedTicket.getStatus()) {
            publishStatusChangedEvent(updatedTicket, previousStatus);
        }

        return TicketResponseDTO.fromEntity(updatedTicket);
    }

    @Transactional
    public TicketResponseDTO updateStatus(Long id, TicketStatusUpdateDTO dto) {
        Ticket ticket = findEntityById(id);
        TicketStatus oldStatus = ticket.getStatus();

        if (oldStatus == dto.status()) {
            return TicketResponseDTO.fromEntity(ticket);
        }

        if (oldStatus == TicketStatus.CLOSED) {
            throw new BusinessException("Não é permitido reabrir ou alterar status de um chamado 'CLOSED'.");
        }

        ticket.setStatus(dto.status());
        Ticket updatedTicket = ticketRepository.save(ticket);
        log.info("Status do ticket {} alterado de {} para {}", id, oldStatus, dto.status());

        publishStatusChangedEvent(updatedTicket, oldStatus);

        return TicketResponseDTO.fromEntity(updatedTicket);
    }

    @Transactional
    public void delete(Long id) {
        Ticket ticket = findEntityById(id);
        ticket.setTicketEnabled(false);
        ticketRepository.save(ticket);
        log.info("Ticket {} desativado logicamente (ticketEnabled = false). Status mantido: {}", id, ticket.getStatus());
    }

    @Transactional
    public void closeTicket(Long id) {
        updateStatus(id, new TicketStatusUpdateDTO(TicketStatus.CLOSED));
    }

    private void publishStatusChangedEvent(Ticket ticket, TicketStatus oldStatus) {
        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                ticket.getId(),
                ticket.getTitle(),
                oldStatus,
                ticket.getStatus(),
                ticket.getCustomerId(),
                ticket.getTechnicianId()
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TICKET_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_STATUS_CHANGED,
                event
        );
    }

    private Ticket findEntityById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado não encontrado com ID: " + id));
    }
}
