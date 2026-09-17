package com.solutis.projeto.helpdesk_ticket_service.controller;

import com.solutis.projeto.helpdesk_ticket_service.dto.*;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;
import com.solutis.projeto.helpdesk_ticket_service.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@Tag(name = "Chamados (Tickets)", description = "Endpoints para gerenciamento do ciclo de vida dos chamados")
@SecurityRequirement(name = "bearerAuth")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @Operation(summary = "Criar um novo chamado de suporte")
    public ResponseEntity<TicketResponseDTO> create(@Valid @RequestBody TicketCreateDTO dto) {
        TicketResponseDTO created = ticketService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Listar chamados com paginação e filtros opcionais por status, prioridade, categoria e IDs")
    public ResponseEntity<Page<TicketResponseDTO>> findAll(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long technicianId,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<TicketResponseDTO> tickets = ticketService.findAll(status, priority, category, customerId, technicianId, pageable);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar dados completos de um chamado pelo ID")
    public ResponseEntity<TicketResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.findById(id));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Listar todos os chamados abertos por um cliente específico")
    public ResponseEntity<List<TicketResponseDTO>> findByCustomerId(@PathVariable Long customerId) {
        return ResponseEntity.ok(ticketService.findByCustomerId(customerId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar informações cadastrais do chamado (título, descrição, prioridade, categoria)")
    public ResponseEntity<TicketResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody TicketUpdateDTO dto
    ) {
        return ResponseEntity.ok(ticketService.update(id, dto));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Atribuir um técnico responsável ao chamado")
    public ResponseEntity<TicketResponseDTO> assignTechnician(
            @PathVariable Long id,
            @Valid @RequestBody TicketAssignDTO dto
    ) {
        return ResponseEntity.ok(ticketService.assignTechnician(id, dto));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Alterar o status de atendimento do chamado")
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody TicketStatusUpdateDTO dto
    ) {
        return ResponseEntity.ok(ticketService.updateStatus(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Encerrar um chamado (marcação de status CLOSED)")
    public ResponseEntity<Void> closeTicket(@PathVariable Long id) {
        ticketService.closeTicket(id);
        return ResponseEntity.noContent().build();
    }
}
