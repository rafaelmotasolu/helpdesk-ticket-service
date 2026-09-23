package com.solutis.projeto.helpdesk_ticket_service.client;

import com.solutis.projeto.helpdesk_ticket_service.dto.UserSummaryDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestClient restClient;

    public UserServiceClient(@Value("${user-service.url:http://localhost:8081}") String userServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    public UserSummaryDTO getUserById(Long userId) {
        try {
            var requestSpec = restClient.get()
                    .uri("/users/{id}", userId);

            String authHeader = resolveBearerToken();
            if (authHeader != null && !authHeader.isBlank()) {
                requestSpec.header("Authorization", authHeader);
            }

            return requestSpec.retrieve().body(UserSummaryDTO.class);
        } catch (Exception e) {
            log.error("Erro ao consultar usuário com ID {} no user-service: {}", userId, e.getMessage());
            return null;
        }
    }

    private String resolveBearerToken() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader("Authorization");
        }
        return null;
    }
}

