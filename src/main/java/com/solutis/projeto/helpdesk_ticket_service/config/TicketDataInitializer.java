package com.solutis.projeto.helpdesk_ticket_service.config;

import com.solutis.projeto.helpdesk_ticket_service.entity.Ticket;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketCategory;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketPriority;
import com.solutis.projeto.helpdesk_ticket_service.entity.TicketStatus;
import com.solutis.projeto.helpdesk_ticket_service.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TicketDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TicketDataInitializer.class);

    private final TicketRepository ticketRepository;

    public TicketDataInitializer(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    public void run(String... args) {
        if (ticketRepository.count() <= 1) {
            log.info("Inicializando população de chamados para ambiente de demonstração...");

            // 5: Ana Pereira (CLIENT), 6: Lucas Ferreira (CLIENT), 7: Beatriz Santos (CLIENT)
            // 2: Carlos Silva (TECH), 3: Mariana Souza (TECH), 4: Roberto Lima (TECH)

            Ticket t1 = new Ticket("Falha de conectividade na VPN Corporativa",
                    "Usuários do setor de desenvolvimento relatam queda frequente de conexão com os servidores de homologação.",
                    TicketCategory.NETWORK, TicketPriority.CRITICAL, 5L);
            t1.setStatus(TicketStatus.IN_PROGRESS);
            t1.setTechnicianId(2L); // Carlos Silva
            t1.setTicketEnabled(true);

            Ticket t2 = new Ticket("Instalação e configuração de IDE e Docker",
                    "Solicito auxílio técnico para configuração do ambiente de desenvolvimento Docker e IntelliJ na máquina de onboarding.",
                    TicketCategory.SOFTWARE, TicketPriority.LOW, 6L);
            t2.setStatus(TicketStatus.RESOLVED);
            t2.setTechnicianId(3L); // Mariana Souza
            t2.setTicketEnabled(true);

            Ticket t3 = new Ticket("Monitor não liga após oscilação de energia",
                    "Após queda rápida de energia no 2º andar, o monitor secundário Dell não dá mais sinal de alimentação.",
                    TicketCategory.HARDWARE, TicketPriority.HIGH, 7L);
            t3.setStatus(TicketStatus.OPEN);
            t3.setTechnicianId(null); // Pendente de atribuição!
            t3.setTicketEnabled(true);

            Ticket t4 = new Ticket("Lentidão crítica e timeout no módulo financeiro",
                    "Ao emitir relatórios de fechamento mensal, o sistema HelpDesk/Financeiro trava com erro 504 Gateway Timeout.",
                    TicketCategory.SOFTWARE, TicketPriority.HIGH, 5L);
            t4.setStatus(TicketStatus.WAITING);
            t4.setTechnicianId(4L); // Roberto Lima
            t4.setTicketEnabled(true);

            Ticket t5 = new Ticket("Substituição emergencial de teclado danificado",
                    "Teclado do posto de atendimento com teclas presas inviabilizando o atendimento ao cliente externo.",
                    TicketCategory.HARDWARE, TicketPriority.LOW, 6L);
            t5.setStatus(TicketStatus.CLOSED);
            t5.setTechnicianId(2L); // Carlos Silva
            t5.setTicketEnabled(true);

            Ticket t6 = new Ticket("Queda intermitente do sinal Wi-Fi no 3º andar",
                    "Ponto de acesso (AP) da sala de reuniões desconectando constantemente os notebooks durante chamadas de vídeo.",
                    TicketCategory.NETWORK, TicketPriority.MEDIUM, 7L);
            t6.setStatus(TicketStatus.OPEN);
            t6.setTechnicianId(null); // Pendente de atribuição!
            t6.setTicketEnabled(true);

            Ticket t7 = new Ticket("Solicitação de acesso duplicada (Cancelada)",
                    "Chamado aberto em duplicidade para liberação de acesso na VPN da equipe de QA.",
                    TicketCategory.SOFTWARE, TicketPriority.LOW, 6L);
            t7.setStatus(TicketStatus.OPEN);
            t7.setTechnicianId(null);
            t7.setTicketEnabled(false); // Desativado / Cancelado logicamente

            ticketRepository.saveAll(List.of(t1, t2, t3, t4, t5, t6, t7));
            log.info("População inicial de 7 chamados concluída com sucesso.");
        }
    }
}
