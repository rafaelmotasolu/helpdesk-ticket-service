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
}

