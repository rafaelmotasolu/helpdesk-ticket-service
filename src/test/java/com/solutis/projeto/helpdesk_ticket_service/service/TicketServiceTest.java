package com.solutis.projeto.helpdesk_ticket_service.service;

import com.solutis.projeto.helpdesk_ticket_service.config.RabbitMQConfig;
import com.solutis.projeto.helpdesk_ticket_service.dto.TicketCreateDTO;
import com.solutis.projeto.helpdesk_ticket_service.dto.TicketResponseDTO;
import com.solutis.projeto.helpdesk_ticket_service.entity.Ticket;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;
import com.solutis.projeto.helpdesk_ticket_service.event.TicketCreatedEvent;
import com.solutis.projeto.helpdesk_ticket_service.exception.ResourceNotFoundException;
import com.solutis.projeto.helpdesk_ticket_service.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import com.solutis.projeto.helpdesk_ticket_service.dto.TicketAssignDTO;
import com.solutis.projeto.helpdesk_ticket_service.dto.UserSummaryDTO;
import com.solutis.projeto.helpdesk_ticket_service.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private com.solutis.projeto.helpdesk_ticket_service.client.UserServiceClient userServiceClient;

    @InjectMocks
    private TicketService ticketService;

    private Ticket sampleTicket;

    @BeforeEach
    void setUp() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTicket = new Ticket(
                "Problema na rede",
                "Sem acesso a rede e computadores",
                TicketCategory.NETWORK,
                TicketPriority.HIGH,
                2L
        );
        sampleTicket.setId(1L);
        sampleTicket.setStatus(TicketStatus.IN_PROGRESS);
        sampleTicket.setTicketEnabled(true);
    }

    @Test
    @DisplayName("Deve criar ticket com ticketEnabled true por padrão")
    void shouldCreateTicketWithTicketEnabledTrue() {
        TicketCreateDTO dto = new TicketCreateDTO(
                "Problema de rede",
                "Sem acesso à rede interna e internet",
                TicketCategory.NETWORK,
                TicketPriority.HIGH,
                2L
        );

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        TicketResponseDTO response = ticketService.create(dto);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertTrue(response.ticketEnabled(), "Ticket deve ser criado como ativado (ticketEnabled = true)");
        verify(ticketRepository).save(any(Ticket.class));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.TICKET_EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_CREATED),
                any(TicketCreatedEvent.class)
        );
    }

    @Test
    @DisplayName("Deve realizar exclusão lógica (ticketEnabled = false) sem alterar o status atual")
    void shouldSoftDeleteTicketWithoutChangingStatus() {
        // Status inicial: IN_PROGRESS
        assertEquals(TicketStatus.IN_PROGRESS, sampleTicket.getStatus());
        assertTrue(sampleTicket.isTicketEnabled());

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(sampleTicket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ticketService.delete(1L);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());

        Ticket saved = captor.getValue();
        assertFalse(saved.isTicketEnabled(), "O ticket deve ter ticketEnabled = false após a exclusão lógica");
        assertEquals(TicketStatus.IN_PROGRESS, saved.getStatus(), "O status do chamado deve ser mantido inalterado!");
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar excluir ticket inexistente")
    void shouldThrowNotFoundWhenDeletingNonExistentTicket() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ticketService.delete(999L));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve listar chamados aplicando Specification com enabledFilter")
    void shouldListTicketsWithEnabledFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Ticket> page = new PageImpl<>(List.of(sampleTicket));

        when(ticketRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<TicketResponseDTO> result = ticketService.findAll(null, null, null, null, null, "ativados", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).ticketEnabled());
        verify(ticketRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Não deve permitir atribuir um técnico ao seu próprio chamado (customerId == technicianId)")
    void shouldNotAssignTechnicianToOwnTicket() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTicket.setCustomerId(5L);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(sampleTicket));
        when(userServiceClient.getUserById(5L)).thenReturn(new UserSummaryDTO(5L, "Pedro Paulo", "pp@gmail.com", "TECHNICIAN", true));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                ticketService.assignTechnician(1L, new TicketAssignDTO(5L))
        );

        assertEquals("Um técnico não pode ser atribuído ao seu próprio chamado.", ex.getMessage());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Deve atribuir técnico com sucesso quando não for o criador do chamado")
    void shouldAssignTechnicianToDifferentTicket() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTicket.setCustomerId(9L);
        sampleTicket.setStatus(TicketStatus.OPEN);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(sampleTicket));
        when(userServiceClient.getUserById(5L)).thenReturn(new UserSummaryDTO(5L, "Carlos Silva", "carlos@helpdesk.com", "TECHNICIAN", true));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.assignTechnician(1L, new TicketAssignDTO(5L));

        assertNotNull(response);
        assertEquals(5L, response.technicianId());
        assertEquals(TicketStatus.IN_PROGRESS, response.status());
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Técnico deve conseguir assumir chamado disponível (sem técnico e que não seja dele)")
    void shouldAllowTechnicianToAssumeUnassignedTicket() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "5", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTicket.setCustomerId(9L);
        sampleTicket.setTechnicianId(null);
        sampleTicket.setStatus(TicketStatus.OPEN);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(sampleTicket));
        when(userServiceClient.getUserById(5L)).thenReturn(new UserSummaryDTO(5L, "Carlos Silva", "carlos@helpdesk.com", "TECHNICIAN", true));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.assignTechnician(1L, new TicketAssignDTO(5L));

        assertNotNull(response);
        assertEquals(5L, response.technicianId());
        assertEquals(TicketStatus.IN_PROGRESS, response.status());
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Técnico não deve conseguir assumir chamado já atribuído a outro técnico")
    void shouldNotAllowTechnicianToAssumeAlreadyAssignedTicket() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "5", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTicket.setCustomerId(9L);
        sampleTicket.setTechnicianId(2L);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(sampleTicket));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                ticketService.assignTechnician(1L, new TicketAssignDTO(5L))
        );

        assertEquals("Este chamado já possui um técnico atribuído.", ex.getMessage());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Técnico não deve conseguir assumir seu próprio chamado")
    void shouldNotAllowTechnicianToAssumeOwnTicket() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "5", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTicket.setCustomerId(5L);
        sampleTicket.setTechnicianId(null);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(sampleTicket));
        when(userServiceClient.getUserById(5L)).thenReturn(new UserSummaryDTO(5L, "Pedro Paulo", "pp@gmail.com", "TECHNICIAN", true));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                ticketService.assignTechnician(1L, new TicketAssignDTO(5L))
        );

        assertEquals("Um técnico não pode ser atribuído ao seu próprio chamado.", ex.getMessage());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Técnico não deve conseguir atribuir um chamado para outro técnico")
    void shouldNotAllowTechnicianToAssignSomeoneElse() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "5", null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        sampleTicket.setCustomerId(9L);
        sampleTicket.setTechnicianId(null);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(sampleTicket));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                ticketService.assignTechnician(1L, new TicketAssignDTO(2L))
        );

        assertEquals("Técnicos só podem assumir chamados para si mesmos.", ex.getMessage());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
}

