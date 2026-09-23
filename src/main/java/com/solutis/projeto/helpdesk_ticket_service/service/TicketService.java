package com.solutis.projeto.helpdesk_ticket_service.service;

import com.solutis.projeto.helpdesk_ticket_service.client.UserServiceClient;
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
import com.solutis.projeto.helpdesk_ticket_service.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final RabbitTemplate rabbitTemplate;
    private final UserServiceClient userServiceClient;

    public TicketService(TicketRepository ticketRepository,
                         RabbitTemplate rabbitTemplate,
                         UserServiceClient userServiceClient) {
        this.ticketRepository = ticketRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.userServiceClient = userServiceClient;
    }

    @Transactional
    public TicketResponseDTO create(TicketCreateDTO dto) {
        Long customerId = dto.customerId();
        // Se o usuário autenticado for CLIENTE ou TÉCNICO, o chamado é obrigatoriamente associado a ele como solicitante
        if (SecurityUtils.isClient() || SecurityUtils.isTechnician()) {
            customerId = SecurityUtils.getCurrentUserId();
        }

        Ticket ticket = new Ticket(
                dto.title(),
                dto.description(),
                dto.category(),
                dto.priority(),
                customerId
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
                savedTicket.getCustomerId(),
                savedTicket.isTicketEnabled()
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
        if (SecurityUtils.isClient()) {
            // Se o usuário autenticado for CLIENTE, restringe estritamente aos seus próprios chamados
            Long currentUserId = SecurityUtils.getCurrentUserId();
            customerId = currentUserId;
        }

        // Apenas ADMIN tem permissão para visualizar chamados desativados
        if (!SecurityUtils.isAdmin()) {
            enabledFilter = "ativados";
        }

        Specification<Ticket> spec = TicketSpecification.withFilters(status, priority, category, customerId, technicianId, enabledFilter);

        // Se o usuário autenticado for TÉCNICO, pode visualizar os chamados atribuídos a ele OU abertos por ele
        if (SecurityUtils.isTechnician()) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            Specification<Ticket> techVisibility = (root, query, cb) -> cb.or(
                    cb.equal(root.get("technicianId"), currentUserId),
                    cb.equal(root.get("customerId"), currentUserId)
            );
            spec = spec.and(techVisibility);
        }

        return ticketRepository.findAll(spec, pageable).map(TicketResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO findById(Long id) {
        Ticket ticket = findEntityById(id);

        // Se o usuário autenticado for TÉCNICO, pode visualizar se o chamado estiver atribuído a ele ou tiver sido aberto por ele
        if (SecurityUtils.isTechnician()) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            boolean isAssigned = ticket.getTechnicianId() != null && ticket.getTechnicianId().equals(currentUserId);
            boolean isCreator = ticket.getCustomerId() != null && ticket.getCustomerId().equals(currentUserId);
            if (!isAssigned && !isCreator) {
                throw new AccessDeniedException("Acesso negado: Técnicos só podem visualizar chamados atribuídos a eles ou abertos por eles.");
            }
        } else if (SecurityUtils.isClient()) {
            // Se o usuário autenticado for CLIENTE, só pode visualizar se o chamado pertencer a ele
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (ticket.getCustomerId() == null || !ticket.getCustomerId().equals(currentUserId)) {
                throw new AccessDeniedException("Acesso negado: Clientes só podem visualizar seus próprios chamados.");
            }
        }

        // Se não for ADMIN e o chamado estiver desativado, bloqueia visualização
        if (!SecurityUtils.isAdmin() && !ticket.isTicketEnabled()) {
            throw new AccessDeniedException("Acesso negado: Chamado desativado.");
        }

        return TicketResponseDTO.fromEntity(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> findByCustomerId(Long customerId) {
        if (SecurityUtils.isClient()) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (!customerId.equals(currentUserId)) {
                throw new AccessDeniedException("Acesso negado: Clientes só podem consultar seus próprios chamados.");
            }
        }
        return ticketRepository.findByCustomerId(customerId)
                .stream()
                .map(TicketResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public TicketResponseDTO update(Long id, TicketUpdateDTO dto) {
        Ticket ticket = findEntityById(id);

        validateModificationPermission(ticket);

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
        // Apenas ADMIN pode atribuir técnicos
        if (!SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Apenas o administrador pode atribuir técnicos a chamados.");
        }

        Ticket ticket = findEntityById(id);

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessException("Não é permitido atribuir técnico a um chamado já encerrado.");
        }

        // Consultar usuário no user-service e validar regras de perfil
        UserSummaryDTO targetUser = userServiceClient.getUserById(dto.technicianId());
        if (targetUser == null) {
            throw new ResourceNotFoundException("Técnico não encontrado com ID: " + dto.technicianId());
        }

        if ("ADMIN".equalsIgnoreCase(targetUser.role())) {
            throw new BusinessException("Não é permitido atribuir um administrador a um chamado. Escolha um usuário com perfil TÉCNICO.");
        }

        if (!"TECHNICIAN".equalsIgnoreCase(targetUser.role())) {
            throw new BusinessException("Apenas usuários com perfil de TÉCNICO podem ser atribuídos a chamados.");
        }

        // Regra de negócio: um técnico não pode ser atribuído ao seu próprio chamado
        if (ticket.getCustomerId() != null && ticket.getCustomerId().equals(dto.technicianId())) {
            throw new BusinessException("Um técnico não pode ser atribuído ao seu próprio chamado.");
        }

        ticket.setTechnicianId(dto.technicianId());

        // Se o chamado estava apenas ABERTO, transita para EM ATENDIMENTO
        TicketStatus previousStatus = ticket.getStatus();
        if (previousStatus == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        log.info("Técnico {} ({}) atribuído ao chamado {}", dto.technicianId(), targetUser.name(), id);

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

        validateModificationPermission(ticket);

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
        // Apenas o administrador tem permissão para desativar chamados
        if (!SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Apenas administradores podem desativar chamados.");
        }

        Ticket ticket = findEntityById(id);
        ticket.setTicketEnabled(false);
        ticketRepository.save(ticket);
        log.info("Ticket {} desativado logicamente pelo administrador. Status mantido: {}", id, ticket.getStatus());
    }

    @Transactional
    public void closeTicket(Long id) {
        updateStatus(id, new TicketStatusUpdateDTO(TicketStatus.CLOSED));
    }

    private void validateModificationPermission(Ticket ticket) {
        if (SecurityUtils.isAdmin()) {
            return;
        }

        if (SecurityUtils.isTechnician()) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (ticket.getTechnicianId() == null || !ticket.getTechnicianId().equals(currentUserId)) {
                throw new AccessDeniedException("Apenas o técnico atribuído a este chamado ou o administrador podem alterá-lo.");
            }
            return;
        }

        throw new AccessDeniedException("Você não possui permissão para alterar este chamado.");
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
